package it.michele.wgswitch;

import android.app.Application;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Salva nel registro eventi l'errore di un eventuale crash, per poterlo diagnosticare. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String trace = sw.toString();
                    if (trace.length() > 3000) trace = trace.substring(0, 3000);
                    Prefs p = new Prefs(App.this);
                    p.log("CRASH: " + trace, true);
                } catch (Throwable ignored) {
                }
                if (previous != null) previous.uncaughtException(t, e);
            }
        });
    }
}
