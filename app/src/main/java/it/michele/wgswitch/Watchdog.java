package it.michele.wgswitch;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import java.util.List;

/**
 * Controllo periodico (ogni ~15 minuti): se il sistema ha chiuso il servizio lo riavvia, e annota nel
 * registro quando Samsung/Android iniziano a limitare l'app, avvisando con una notifica.
 */
public class Watchdog extends JobService {
    private static final int JOB_ID = 1;
    private static final long PERIOD_MS = 15 * 60 * 1000L;

    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, Watchdog.class))
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build();
        try {
            js.schedule(job);
        } catch (RuntimeException e) {
            new Prefs(c).log("Controllo periodico non pianificato: " + e.getMessage());
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        check(this, "controllo periodico");
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    /** Riavvia il servizio se è fermo e registra i cambiamenti dello stato di sospensione. */
    static void check(Context c, String source) {
        Prefs prefs = new Prefs(c);
        prefs.setLastCheck(System.currentTimeMillis());
        boolean restartFailed = false;
        if (!SwitchService.running
                && c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            prefs.log("Servizio trovato fermo (" + source + "): riavvio");
            restartFailed = !SwitchService.start(c, source);
        }
        String summary = Health.summary(c);
        if (!summary.equals(prefs.lastHealth())) {
            prefs.setLastHealth(summary);
            prefs.log(summary);
        }
        List<String> problems = Health.problems(c);
        if (restartFailed) {
            Health.alert(c, "Il servizio è stato chiuso dal sistema e non è ripartito. Apri l'app.");
        } else if (!problems.isEmpty()) {
            Health.alert(c, "Il sistema sta limitando l'app: " + Health.join(problems) + ".");
        } else {
            Health.clearAlert(c);
        }
    }
}
