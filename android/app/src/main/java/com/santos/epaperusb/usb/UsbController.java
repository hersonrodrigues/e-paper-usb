package com.santos.epaperusb.usb;

import android.app.PendingIntent;
import android.content.*;
import android.hardware.usb.*;
import android.os.*;
import com.hoho.android.usbserial.driver.*;
import com.santos.epaperusb.DiagnosticLog;
import com.santos.epaperusb.R;
import com.santos.epaperusb.UiText;
import com.santos.epaperusb.image.PreparedImage;
import com.santos.epaperusb.protocol.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Main-thread commands; one worker owns the port and all blocking I/O. */
public final class UsbController {
    public enum Phase { DISCONNECTED, PERMISSION, CONNECTING, READY, SENDING, CLOSING, RESET_REQUIRED }
    public record State(Phase phase, UiText message, int blocks) { }
    public interface Listener { void changed(State state); }
    private final Context context;
    private final UsbManager manager;
    private final SharedPreferences preferences;
    private final DiagnosticLog log;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final String permissionAction;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final TransferEngine engine = new TransferEngine();
    private volatile boolean resetRequired, disposed;
    private volatile int epoch;
    private State state;
    private UsbDevice selected;
    private PendingIntent permissionIntent;
    private UsbSerialPort port; // only touched by io worker

    public UsbController(Context context, DiagnosticLog log, Listener listener) {
        this.context = context.getApplicationContext(); this.log = log; this.listener = listener;
        manager = (UsbManager)this.context.getSystemService(Context.USB_SERVICE);
        preferences = this.context.getSharedPreferences("usb-session", Context.MODE_PRIVATE);
        resetRequired = preferences.getBoolean("incomplete", false);
        permissionAction = context.getPackageName() + ".USB_PERMISSION." + UUID.randomUUID();
        IntentFilter permissions = new IntentFilter(permissionAction);
        IntentFilter devices = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        devices.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) {
            this.context.registerReceiver(receiver, permissions, Context.RECEIVER_NOT_EXPORTED);
            this.context.registerReceiver(receiver, devices, Context.RECEIVER_NOT_EXPORTED);
        } else {
            this.context.registerReceiver(receiver, permissions);
            this.context.registerReceiver(receiver, devices);
        }
        publish(resetRequired ? Phase.RESET_REQUIRED : Phase.DISCONNECTED, resetRequired ? resetMessage() : UiText.of(R.string.usb_initial), 0);
        log.add("App started; incomplete session=" + resetRequired);
    }
    public State state() { return state; }
    private void publish(Phase phase, UiText message, int blocks) {
        if (disposed) return;
        state = new State(phase, message, blocks); listener.changed(state);
    }
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            UsbDevice device = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class)
                : intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (permissionAction.equals(intent.getAction())) {
                if (state.phase() != Phase.PERMISSION || intent.getIntExtra("request_epoch", -1) != epoch
                        || selected == null) return;
                if (device != null && !selected.equals(device)) { log.add("Permission reply for another device ignored"); return; }
                if (permissionIntent != null) { permissionIntent.cancel(); permissionIntent = null; }
                boolean actualPermission = manager.hasPermission(selected);
                log.add("Permission callback: device=" + (device != null) + "; result="
                    + intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) + "; UsbManager=" + actualPermission);
                if (!actualPermission) {
                    selected = null; log.add("USB permission denied");
                    publish(Phase.DISCONNECTED, UiText.of(R.string.usb_denied), 0);
                } else open(selected, epoch);
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction()) && selected != null && selected.equals(device)) {
                log.add("USB device detached"); disconnect();
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction()) && matches(device)) {
                log.add("CH340 attached; no automatic transfer");
                if (state.phase() == Phase.DISCONNECTED) publish(Phase.DISCONNECTED, UiText.of(R.string.usb_detected), 0);
            }
        }
    };
    public static boolean matches(UsbDevice device) {
        return device != null && device.getVendorId() == EpaperProtocol.VENDOR_ID && device.getProductId() == EpaperProtocol.PRODUCT_ID;
    }
    public void connect() {
        if (!disposed && state.phase() == Phase.PERMISSION) {
            recheckPendingPermission();
            if (state.phase() == Phase.PERMISSION) {
                if (permissionIntent != null) { permissionIntent.cancel(); permissionIntent = null; }
                selected = null; ++epoch;
                log.add("Manual check: USB permission not granted yet");
                publish(Phase.DISCONNECTED, UiText.of(R.string.usb_unconfirmed), 0);
            }
            return;
        }
        if (disposed || state.phase() != Phase.DISCONNECTED || resetRequired) return;
        List<UsbDevice> devices = new ArrayList<>();
        for (UsbDevice device : manager.getDeviceList().values()) if (matches(device)) devices.add(device);
        if (devices.size() != 1) {
            publish(Phase.DISCONNECTED, devices.isEmpty() ? UiText.of(R.string.usb_not_found)
                : UiText.of(R.string.usb_multiple), 0);
            log.add("USB scan: " + devices.size() + " devices 1A86:7523"); return;
        }
        selected = devices.get(0); int request = ++epoch;
        log.add("CH340 selected: VID=1A86 PID=7523; does not identify the panel model");
        if (manager.hasPermission(selected)) open(selected, request);
        else {
            publish(Phase.PERMISSION, UiText.of(R.string.usb_authorize), 0);
            permissionIntent = PendingIntent.getBroadcast(context, request,
                new Intent(permissionAction).setPackage(context.getPackageName()).putExtra("request_epoch", request),
                // Android fills EXTRA_DEVICE and EXTRA_PERMISSION_GRANTED when sending this callback.
                // Immutable intents drop those extras. Scope the mutable callback to this package,
                // a private receiver, a random action and the current request generation.
                PendingIntent.FLAG_CANCEL_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
            log.add("Requesting USB permission; request=" + request);
            try { manager.requestPermission(selected, permissionIntent); }
            catch (RuntimeException e) { log.add("Permission: " + e); disconnect(); }
        }
    }
    /** Recover a granted request if a device/OS delays or omits the callback. Never sends pixels. */
    public void recheckPendingPermission() {
        if (disposed || state.phase() != Phase.PERMISSION || selected == null) return;
        if (!manager.getDeviceList().containsKey(selected.getDeviceName())) {
            log.add("Device detached while awaiting permission"); disconnect(); return;
        }
        if (manager.hasPermission(selected)) {
            if (permissionIntent != null) { permissionIntent.cancel(); permissionIntent = null; }
            log.add("USB permission confirmed directly by UsbManager");
            open(selected, epoch);
        }
    }
    private void open(UsbDevice device, int request) {
        publish(Phase.CONNECTING, UiText.of(R.string.usb_opening), 0);
        io.execute(() -> {
            try {
                closePort();
                if (disposed || request != epoch) return;
                ProbeTable table = new ProbeTable();
                table.addProduct(EpaperProtocol.VENDOR_ID, EpaperProtocol.PRODUCT_ID, Ch34xSerialDriver.class);
                UsbSerialDriver driver = new UsbSerialProber(table).probeDevice(device);
                if (driver == null || driver.getPorts().isEmpty()) throw new IOException("CH34x driver unavailable");
                UsbDeviceConnection connection = manager.openDevice(device);
                if (connection == null) throw new IOException("USB access unavailable; reconnect");
                port = driver.getPorts().get(0);
                try {
                    port.open(connection);
                    port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                    port.setRTS(true); port.setDTR(false);
                } catch (Exception e) { connection.close(); throw e; }
                if (disposed || request != epoch) { closePort(); return; }
                log.add("Port opened: 115200 8N1 RTS=true DTR=false");
                main.post(() -> { if (request == epoch) publish(Phase.READY, UiText.of(R.string.usb_ready), 0); });
            } catch (Exception e) {
                closePort(); log.add("Connection error: " + e);
                main.post(() -> { if (request == epoch) { selected = null; publish(Phase.DISCONNECTED, UiText.of(R.string.usb_open_failed), 0); } });
            }
        });
    }
    public void send(PreparedImage image) {
        if (disposed || state.phase() != Phase.READY || resetRequired || image == null) return;
        cancelled.set(false); int request = epoch;
        publish(Phase.SENDING, UiText.of(R.string.usb_wait_ack), 0);
        io.execute(() -> {
            try {
                if (request != epoch || disposed) return;
                // Persist BEFORE any header byte; process death must not silently permit a retry.
                if (!preferences.edit().putBoolean("incomplete", true).commit()) throw new IOException("Could not persist session state");
                resetRequired = true;
                if (port == null) throw new IOException("USB port closed");
                final UsbSerialPort activePort = port;
                engine.send(new TransferEngine.Transport() {
                    public void write(byte[] bytes, int timeout) throws IOException { activePort.write(bytes, timeout); }
                    public int read(byte[] bytes, int timeout) throws IOException { return activePort.read(bytes, timeout); }
                    public void close() throws IOException { activePort.close(); }
                }, image, cancelled::get, new TransferEngine.Observer() {
                    public void log(String message) { log.add(message); }
                    public void progress(int blocks) {
                        main.post(() -> { if (request == epoch) publish(Phase.SENDING, UiText.of(R.string.usb_progress, blocks), blocks); });
                    }
                });
                if (!preferences.edit().putBoolean("incomplete", false).commit()) throw new IOException("Could not persist completion");
                resetRequired = false;
                main.post(() -> { if (request == epoch) publish(Phase.READY, UiText.of(R.string.usb_sent), 24); });
            } catch (Exception e) {
                closePort(); log.add("Session ended: " + e);
                main.post(() -> { if (request == epoch) { selected = null; publish(resetRequired ? Phase.RESET_REQUIRED : Phase.DISCONNECTED,
                    resetRequired ? UiText.join(transferError(e), resetMessage()) : transferError(e), 0); } });
            }
        });
    }
    public void cancel() {
        if (state.phase() != Phase.SENDING) return;
        cancelled.set(true); log.add("User requested cancellation");
        publish(Phase.SENDING, UiText.of(R.string.usb_cancelling), state.blocks());
    }
    public void disconnect() {
        if (disposed) return;
        cancelled.set(true); int request = ++epoch; selected = null;
        if (permissionIntent != null) { permissionIntent.cancel(); permissionIntent = null; }
        publish(Phase.CLOSING, UiText.of(R.string.usb_closing), 0);
        io.execute(() -> {
            closePort();
            main.post(() -> { if (request == epoch) publish(resetRequired ? Phase.RESET_REQUIRED : Phase.DISCONNECTED,
                resetRequired ? resetMessage() : UiText.of(R.string.usb_disconnected), 0); });
        });
    }
    public void confirmPhysicalReset() {
        if (state.phase() != Phase.RESET_REQUIRED || disposed) return;
        publish(Phase.CLOSING, UiText.of(R.string.usb_new_session), 0);
        io.execute(() -> {
            closePort(); boolean cleared = preferences.edit().putBoolean("incomplete", false).commit();
            resetRequired = !cleared;
            log.add("User confirmed physical restart; state cleared=" + cleared);
            main.post(() -> publish(cleared ? Phase.DISCONNECTED : Phase.RESET_REQUIRED,
                cleared ? UiText.of(R.string.usb_reset_cleared) : resetMessage(), 0));
        });
    }
    private static UiText resetMessage() { return UiText.of(R.string.usb_reset_required); }
    private static UiText transferError(Exception error) {
        if (error instanceof TransferEngine.Failure failure) return UiText.of(switch (failure.code) {
            case BUSY -> R.string.error_busy;
            case ACK_TIMEOUT, ACK_LATE -> R.string.error_timeout;
            case INVALID_READ -> R.string.error_invalid_response;
            case CANCELLED -> R.string.error_cancelled;
        });
        return UiText.of(R.string.error_transfer);
    }
    private void closePort() {
        if (port == null) return;
        try { port.close(); } catch (IOException e) { log.add("Port closure: " + e.getMessage()); }
        port = null;
    }
    public void dispose() {
        if (disposed) return;
        disposed = true; cancelled.set(true); ++epoch;
        if (permissionIntent != null) permissionIntent.cancel();
        context.unregisterReceiver(receiver);
        io.execute(this::closePort); io.shutdown();
    }
}
