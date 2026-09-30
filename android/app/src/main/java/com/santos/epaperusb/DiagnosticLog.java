package com.santos.epaperusb;

import java.text.SimpleDateFormat;
import java.util.*;

/** Local only, bounded, excludes photo content and source URI. */
public final class DiagnosticLog {
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final long start = android.os.SystemClock.elapsedRealtime();
    private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
    public synchronized void add(String message) {
        lines.add(format.format(new Date()) + " +" + (android.os.SystemClock.elapsedRealtime() - start) + "ms  " + message);
        while (lines.size() > 600) lines.removeFirst();
    }
    public synchronized String snapshot() {
        return "E-paper USB " + BuildConfig.VERSION_NAME + "\nAndroid " + android.os.Build.VERSION.RELEASE + " / " + android.os.Build.MODEL
            + "\nProfile: 800x480 C4; 115200 8N1 RTS=true DTR=false\nLibrary: usb-serial-for-android 3.11.0\n"
            + "Statically reconstructed protocol. Transfer completion does not confirm physical display update.\n\n"
            + String.join("\n", lines) + "\n";
    }
}
