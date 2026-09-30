package com.santos.epaperusb;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import androidx.lifecycle.*;
import com.santos.epaperusb.image.*;
import com.santos.epaperusb.usb.UsbController;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class AppModel extends AndroidViewModel {
    public record ImageState(PreparedImage prepared, Bitmap preview, boolean processing, UiText message) { }
    public final MutableLiveData<ImageState> image = new MutableLiveData<>(new ImageState(null, null, false, UiText.of(R.string.image_empty)));
    public final MutableLiveData<UsbController.State> usbState = new MutableLiveData<>();
    public final MutableLiveData<UiText> notice = new MutableLiveData<>();
    public final DiagnosticLog log = new DiagnosticLog();
    public final UsbController usb;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService imageWorker = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private File source; // imageWorker owns this file
    private boolean pattern;
    private String[] patternLabels = {"Black", "White", "Yellow", "Red", "TOP / 1", "BOTTOM / 2"};
    public boolean crop, dither = true;
    public int turns;
    public AppModel(Application application) {
        super(application);
        imageWorker.execute(() -> {
            File[] leftovers = application.getCacheDir().listFiles((dir, name) ->
                (name.startsWith("photo-") && name.endsWith(".image")) || (name.startsWith("chart-") && name.endsWith(".png")));
            if (leftovers != null) for (File file : leftovers) file.delete();
        });
        usb = new UsbController(application, log, usbState::setValue);
    }
    public boolean busy() { return usb.state().phase() == UsbController.Phase.SENDING || image.getValue().processing(); }
    public void select(Uri uri) { if (!busy()) process(uri, false); }
    public void pattern(String[] labels) { if (!busy()) { patternLabels = labels.clone(); turns = 0; process(null, true); } }
    public void options(boolean crop, boolean dither, int turns) {
        if (busy()) return;
        this.crop = crop; this.dither = dither; this.turns = turns % 4;
        if (image.getValue().prepared() != null) process(null, pattern);
    }
    private void process(Uri uri, boolean usePattern) {
        int request = generation.incrementAndGet();
        boolean requestedCrop = crop, requestedDither = dither; int requestedTurns = turns;
        String[] requestedLabels = patternLabels.clone();
        ImageState previous = image.getValue();
        image.setValue(new ImageState(previous.prepared(), previous.preview(), true, UiText.of(R.string.image_processing)));
        imageWorker.execute(() -> {
            try {
                if (uri != null) {
                    File imported = ImageProcessor.importPhoto(getApplication().getContentResolver(), uri, getApplication().getCacheDir(), () -> request != generation.get());
                    if (source != null) source.delete(); source = imported;
                }
                PreparedImage prepared;
                if (usePattern) {
                    // Transform the diagnostic chart through the same image path as photos.
                    PreparedImage chart = ImageProcessor.testPattern(requestedLabels);
                    File chartFile = File.createTempFile("chart-", ".png", getApplication().getCacheDir());
                    Bitmap chartBitmap = Bitmap.createBitmap(chart.pixels(), 800, 480, Bitmap.Config.ARGB_8888);
                    try {
                        try (OutputStream out = new FileOutputStream(chartFile)) { chartBitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
                        prepared = ImageProcessor.prepare(chartFile, requestedCrop, requestedTurns, requestedDither, () -> request != generation.get());
                    } finally { chartFile.delete(); chartBitmap.recycle(); }
                } else {
                    if (source == null) throw new IOException("No photo selected");
                    prepared = ImageProcessor.prepare(source, requestedCrop, requestedTurns, requestedDither, () -> request != generation.get());
                }
                Bitmap preview = Bitmap.createBitmap(prepared.pixels(), 800, 480, Bitmap.Config.ARGB_8888);
                log.add("Image prepared: 800x480; " + (requestedCrop ? "crop" : "white borders") + "; rotation=" + (requestedTurns * 90)
                    + "; dithering=" + requestedDither + "; payload SHA-256=" + prepared.sha256());
                main.post(() -> {
                    if (request != generation.get()) return;
                    pattern = usePattern;
                    image.setValue(new ImageState(prepared, preview, false, UiText.of(R.string.image_ready, 96000)));
                });
            } catch (Exception | OutOfMemoryError e) {
                log.add("Image processing error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                main.post(() -> {
                    if (request != generation.get()) return;
                    // An old preview must never become sendable after a failed edit.
                    image.setValue(new ImageState(null, null, false, UiText.of(R.string.image_failed)));
                });
            }
        });
    }
    public void send() {
        ImageState value = image.getValue();
        if (!value.processing() && value.prepared() != null) usb.send(value.prepared());
    }
    public void exportLog(Uri uri) {
        if (uri == null) return;
        String text = log.snapshot();
        imageWorker.execute(() -> {
            try (OutputStream out = getApplication().getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("Document unavailable");
                out.write(text.getBytes(StandardCharsets.UTF_8)); notice.postValue(UiText.of(R.string.export_success));
            } catch (Exception e) { log.add("Log export failed: " + e); notice.postValue(UiText.of(R.string.export_failed)); }
        });
    }
    @Override protected void onCleared() {
        generation.incrementAndGet(); usb.dispose();
        imageWorker.execute(() -> { if (source != null) source.delete(); }); imageWorker.shutdown();
    }
}
