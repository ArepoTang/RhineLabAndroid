package cc.lubeiluchen.rhinelab;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Startup milestone and crash trail.
 *
 * Android 12 stopped letting other apps read our logcat, so the only reliable
 * channel back to a terminal on the same device is a file. `/sdcard/Android/media/<pkg>/`
 * is the one external location that needs no permission and stays readable.
 */
final class Trace {

    private static final String TAG = "RhineLab";
    private static File run;
    private static File crash;

    private Trace() {}

    static synchronized void init(Context context) {
        File dir = null;
        try {
            File[] media = context.getExternalMediaDirs();
            if (media != null && media.length > 0) dir = media[0];
        } catch (Throwable ignored) {
        }
        if (dir == null) dir = context.getFilesDir();
        if (dir == null) return;
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        run = new File(dir, "trace.txt");
        crash = new File(dir, "crash.txt");
        try (PrintWriter out = new PrintWriter(new FileWriter(run, false))) {
            out.println(header(context));
        } catch (Throwable ignored) {
        }
        Log.i(TAG, "trace -> " + run);
    }

    static synchronized void log(String message) {
        Log.i(TAG, message);
        append(run, message);
    }

    static synchronized void crash(Thread thread, Throwable error) {
        StringWriter buffer = new StringWriter();
        buffer.append("thread ").append(String.valueOf(thread)).append('\n');
        error.printStackTrace(new PrintWriter(buffer));
        String text = buffer.toString();
        Log.e(TAG, "uncaught", error);
        append(run, "CRASH\n" + text);
        append(crash, text);
    }

    private static void append(File file, String message) {
        if (file == null) return;
        String line = stamp() + " " + message;
        try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
            out.println(line);
        } catch (Throwable ignored) {
        }
    }

    private static String header(Context context) {
        StringBuilder text = new StringBuilder();
        text.append("package ").append(context.getPackageName()).append('\n');
        text.append("android ").append(Build.VERSION.RELEASE)
                .append(" (api ").append(Build.VERSION.SDK_INT).append(")\n");
        text.append("device ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        text.append("abi ").append(String.join(",", Build.SUPPORTED_ABIS)).append('\n');
        text.append("started ").append(stamp()).append('\n');
        return text.toString();
    }

    private static String stamp() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
