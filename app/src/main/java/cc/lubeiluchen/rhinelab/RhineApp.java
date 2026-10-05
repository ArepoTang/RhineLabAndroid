package cc.lubeiluchen.rhinelab;

import android.app.Application;

/** Runs before any activity, so a failure to load MainActivity still leaves a trail. */
public class RhineApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Trace.init(this);
        Trace.log("application onCreate");
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            Trace.crash(thread, error);
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }
}
