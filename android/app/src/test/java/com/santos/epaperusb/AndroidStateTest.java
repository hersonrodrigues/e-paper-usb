package com.santos.epaperusb;

import android.app.*;
import android.content.*;
import android.hardware.usb.*;
import android.os.Looper;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import com.santos.epaperusb.usb.UsbController;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowUsbManager;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, shadows=AndroidStateTest.PermissionManager.class)
public class AndroidStateTest {
    @Implements(UsbManager.class)
    public static class PermissionManager extends ShadowUsbManager {
        static PendingIntent pending;
        @Implementation protected void requestPermission(UsbDevice device, PendingIntent intent) { pending=intent; }
    }
    private Application app;
    @Before public void before() {
        app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("usb-session",0).edit().clear().commit(); PermissionManager.pending=null;
    }
    private UsbDevice device() {
        UsbDevice device=ReflectionHelpers.newInstance(UsbDevice.class);
        ReflectionHelpers.setField(device,"mName","/dev/bus/usb/001/001");
        ReflectionHelpers.setField(device,"mVendorId",0x1a86); ReflectionHelpers.setField(device,"mProductId",0x7523);
        return device;
    }
    @Test public void permissionDenialDeliveredThroughActualPendingIntentExitsWaiting() throws Exception {
        UsbManager manager=(UsbManager)app.getSystemService(Context.USB_SERVICE); UsbDevice device=device();
        shadowOf(manager).addOrUpdateUsbDevice(device,false);
        UsbController controller=new UsbController(app,new DiagnosticLog(),state->{});
        try {
            controller.connect();
            assertFalse(PermissionManager.pending.isImmutable());
            assertEquals(app.getPackageName(),shadowOf(PermissionManager.pending).getSavedIntent().getPackage());
            // Match Android's callback: extras are supplied at send time, not added to the base intent.
            PermissionManager.pending.send(app,0,new Intent()
                .putExtra(UsbManager.EXTRA_DEVICE,device)
                .putExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(UsbController.Phase.DISCONNECTED,controller.state().phase());
        } finally { controller.dispose(); }
    }
    @Test public void grantedPermissionViaPendingIntentStartsOpeningExactlyOnce() throws Exception {
        UsbManager manager=(UsbManager)app.getSystemService(Context.USB_SERVICE); UsbDevice device=device();
        shadowOf(manager).addOrUpdateUsbDevice(device,false);
        java.util.List<UsbController.Phase> phases=new java.util.ArrayList<>(); DiagnosticLog log=new DiagnosticLog();
        UsbController controller=new UsbController(app,log,state->phases.add(state.phase()));
        try {
            controller.connect(); shadowOf(manager).addOrUpdateUsbDevice(device,true);
            PermissionManager.pending.send(app,0,new Intent()
                .putExtra(UsbManager.EXTRA_DEVICE,device).putExtra(UsbManager.EXTRA_PERMISSION_GRANTED,true));
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(phases.contains(UsbController.Phase.CONNECTING));
            controller.recheckPendingPermission();
            assertEquals(1,java.util.Collections.frequency(phases,UsbController.Phase.CONNECTING));
            assertTrue(log.snapshot().contains("device=true; result=true; UsbManager=true"));
            assertFalse(phases.contains(UsbController.Phase.SENDING));
        } finally { controller.dispose(); }
    }
    @Test public void permissionRecheckRecoversLostCallbackOnlyWhenActuallyGranted() {
        UsbManager manager=(UsbManager)app.getSystemService(Context.USB_SERVICE); UsbDevice device=device();
        shadowOf(manager).addOrUpdateUsbDevice(device,false);
        java.util.List<UsbController.Phase> phases=new java.util.ArrayList<>();
        UsbController controller=new UsbController(app,new DiagnosticLog(),state->phases.add(state.phase()));
        try {
            controller.connect(); controller.recheckPendingPermission();
            assertEquals(UsbController.Phase.PERMISSION,controller.state().phase());
            shadowOf(manager).addOrUpdateUsbDevice(device,true); controller.recheckPendingPermission();
            assertEquals(UsbController.Phase.CONNECTING,controller.state().phase());
            controller.recheckPendingPermission();
            assertEquals(1,java.util.Collections.frequency(phases,UsbController.Phase.CONNECTING));
            assertFalse(phases.contains(UsbController.Phase.SENDING));
        } finally { controller.dispose(); }
    }
    @Test public void manualRecheckWithoutGrantOffersRetryInsteadOfGettingStuck() {
        UsbManager manager=(UsbManager)app.getSystemService(Context.USB_SERVICE);
        shadowOf(manager).addOrUpdateUsbDevice(device(),false);
        UsbController controller=new UsbController(app,new DiagnosticLog(),state->{});
        try {
            controller.connect(); controller.connect();
            assertEquals(UsbController.Phase.DISCONNECTED,controller.state().phase());
            assertEquals(R.string.usb_unconfirmed,controller.state().message().resourceId());
        } finally { controller.dispose(); }
    }
    @Test public void refusalAndForgedGrantCannotOpenPort() {
        UsbManager manager=(UsbManager)app.getSystemService(Context.USB_SERVICE); UsbDevice device=device();
        shadowOf(manager).addOrUpdateUsbDevice(device,false);
        UsbController controller=new UsbController(app,new DiagnosticLog(),state->{});
        try {
            for (boolean claimedGrant:new boolean[]{false,true}) {
                controller.connect(); assertEquals(UsbController.Phase.PERMISSION,controller.state().phase());
                Intent reply=new Intent(shadowOf(PermissionManager.pending).getSavedIntent());
                reply.putExtra(UsbManager.EXTRA_DEVICE,device); reply.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED,claimedGrant);
                app.sendBroadcast(reply); shadowOf(Looper.getMainLooper()).idle();
                assertEquals(UsbController.Phase.DISCONNECTED,controller.state().phase());
                assertEquals(R.string.usb_denied,controller.state().message().resourceId());
            }
        } finally { controller.dispose(); }
    }
    @Test public void attachDoesNotRequestPermissionOrSend() {
        UsbController controller=new UsbController(app,new DiagnosticLog(),state->{});
        try {
            app.sendBroadcast(new Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED).putExtra(UsbManager.EXTRA_DEVICE,device()));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(UsbController.Phase.DISCONNECTED,controller.state().phase()); assertNull(PermissionManager.pending);
        } finally { controller.dispose(); }
    }
    @Test public void detachedPermissionRequestCannotAffectReconnection() throws Exception {
        UsbManager manager=(UsbManager)app.getSystemService(Context.USB_SERVICE); UsbDevice device=device();
        shadowOf(manager).addOrUpdateUsbDevice(device,false);
        UsbController controller=new UsbController(app,new DiagnosticLog(),state->{});
        try {
            controller.connect(); Intent oldReply=new Intent(shadowOf(PermissionManager.pending).getSavedIntent());
            oldReply.putExtra(UsbManager.EXTRA_DEVICE,device).putExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false);
            app.sendBroadcast(new Intent(UsbManager.ACTION_USB_DEVICE_DETACHED).putExtra(UsbManager.EXTRA_DEVICE,device));
            for (int i=0;i<100 && controller.state().phase()!=UsbController.Phase.DISCONNECTED;i++) {
                shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5);
            }
            assertEquals(UsbController.Phase.DISCONNECTED,controller.state().phase());
            controller.connect(); assertEquals(UsbController.Phase.PERMISSION,controller.state().phase());
            app.sendBroadcast(oldReply); shadowOf(Looper.getMainLooper()).idle();
            assertEquals(UsbController.Phase.PERMISSION,controller.state().phase());
        } finally { controller.dispose(); }
    }
    @Test public void persistentIncompleteSessionBlocksNewConnection() {
        app.getSharedPreferences("usb-session",0).edit().putBoolean("incomplete",true).commit();
        UsbController controller=new UsbController(app,new DiagnosticLog(),state->{});
        try {
            assertEquals(UsbController.Phase.RESET_REQUIRED,controller.state().phase());
            controller.connect(); assertEquals(UsbController.Phase.RESET_REQUIRED,controller.state().phase()); assertNull(PermissionManager.pending);
        } finally { controller.dispose(); }
    }
    @Test public void activityRecreationPreservesExactPreviewAndSendIsDisabledOffline() {
        var controller=Robolectric.buildActivity(MainActivity.class).setup();
        try {
            AppModel model=new ViewModelProvider(controller.get()).get(AppModel.class);
            var prepared=ProtocolTest.chart();
            var bitmap=android.graphics.Bitmap.createBitmap(prepared.pixels(),800,480,android.graphics.Bitmap.Config.ARGB_8888);
            model.image.setValue(new AppModel.ImageState(prepared,bitmap,false,UiText.of(R.string.image_ready,96000)));
            assertFalse(((Button)controller.get().getWindow().getDecorView().findViewWithTag("send")).isEnabled());
            controller.recreate();
            AppModel after=new ViewModelProvider(controller.get()).get(AppModel.class);
            assertSame(model,after); assertSame(prepared,after.image.getValue().prepared());
            assertArrayEquals(prepared.payload(),after.image.getValue().prepared().payload());
            assertSame(bitmap,after.image.getValue().preview());
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void sendRequiresBothImageAndReadyUsbAndExplainsMissingStep() {
        var activity=Robolectric.buildActivity(MainActivity.class).setup();
        try {
            AppModel model=new ViewModelProvider(activity.get()).get(AppModel.class);
            Button send=activity.get().getWindow().getDecorView().findViewWithTag("send");
            TextView reason=activity.get().getWindow().getDecorView().findViewWithTag("sendRequirement");
            ReflectionHelpers.callInstanceMethod(model.usb,"publish",
                ReflectionHelpers.ClassParameter.from(UsbController.Phase.class,UsbController.Phase.READY),
                ReflectionHelpers.ClassParameter.from(UiText.class,UiText.of(R.string.usb_ready)),ReflectionHelpers.ClassParameter.from(int.class,0));
            assertFalse(send.isEnabled()); assertEquals(activity.get().getString(R.string.need_image),reason.getText().toString());
            var prepared=ProtocolTest.chart();
            var bitmap=android.graphics.Bitmap.createBitmap(prepared.pixels(),800,480,android.graphics.Bitmap.Config.ARGB_8888);
            model.image.setValue(new AppModel.ImageState(prepared,bitmap,false,UiText.of(R.string.image_ready,96000)));
            assertTrue(send.isEnabled());
            model.image.setValue(new AppModel.ImageState(prepared,bitmap,true,UiText.of(R.string.image_processing)));
            assertFalse(send.isEnabled()); assertEquals(activity.get().getString(R.string.need_processing),reason.getText().toString());
        } finally { activity.pause().stop().destroy(); }
    }
}
