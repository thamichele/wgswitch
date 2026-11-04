package it.michele.wgswitch;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.List;

/**
 * Controlla se il sistema (in particolare Samsung "App in sospensione") sta limitando l'app.
 * Nessuno di questi controlli richiede permessi.
 */
final class Health {
    private static final String CHANNEL_ALERT = "alert";
    private static final int ALERT_ID = 2;

    private Health() {}

    static boolean isSamsung() {
        return Build.MANUFACTURER != null && Build.MANUFACTURER.equalsIgnoreCase("samsung");
    }

    static boolean ignoringBatteryOptimizations(Context c) {
        PowerManager pm = c.getSystemService(PowerManager.class);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    /** true se l'uso in background è "Limitato" (Samsung: "App in sospensione profonda"). */
    static boolean backgroundRestricted(Context c) {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        return am != null && am.isBackgroundRestricted();
    }

    /** true se "Sospendi attività app se non utilizzata" è disattivato per questa app. */
    static boolean hibernationDisabled(Context c) {
        try {
            return c.getPackageManager().isAutoRevokeWhitelisted();
        } catch (RuntimeException e) {
            return true;
        }
    }

    static int standbyBucket(Context c) {
        UsageStatsManager us = c.getSystemService(UsageStatsManager.class);
        return us != null ? us.getAppStandbyBucket() : UsageStatsManager.STANDBY_BUCKET_ACTIVE;
    }

    static String bucketName(int b) {
        if (b <= 5) return "esente";
        if (b <= UsageStatsManager.STANDBY_BUCKET_ACTIVE) return "attiva";
        if (b <= UsageStatsManager.STANDBY_BUCKET_WORKING_SET) return "uso frequente";
        if (b <= UsageStatsManager.STANDBY_BUCKET_FREQUENT) return "uso occasionale";
        if (b <= UsageStatsManager.STANDBY_BUCKET_RARE) return "uso raro";
        return "limitata";
    }

    /** Il sistema sta già trattando l'app come inutilizzata: le sue attività in background vengono ridotte. */
    static boolean bucketLimited(int b) {
        return b >= UsageStatsManager.STANDBY_BUCKET_RARE;
    }

    /** Elenco dei problemi che possono far smettere di funzionare il servizio; vuoto se è tutto a posto. */
    static List<String> problems(Context c) {
        List<String> p = new ArrayList<>();
        if (!ignoringBatteryOptimizations(c)) p.add("batteria ottimizzata (non \"Senza restrizioni\")");
        if (backgroundRestricted(c)) p.add("uso in background limitato (sospensione profonda)");
        if (!hibernationDisabled(c)) p.add("\"Sospendi attività app se non utilizzata\" attivo");
        int b = standbyBucket(c);
        if (bucketLimited(b)) p.add("app considerata inutilizzata (standby: " + bucketName(b) + ")");
        return p;
    }

    /** Riassunto per il registro eventi. */
    static String summary(Context c) {
        List<String> p = problems(c);
        String bucket = "standby " + bucketName(standbyBucket(c));
        return p.isEmpty() ? "Controllo sospensione: tutto ok (" + bucket + ")"
                : "ATTENZIONE sospensione: " + join(p) + " (" + bucket + ")";
    }

    static String join(List<String> p) {
        StringBuilder sb = new StringBuilder();
        for (String s : p) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Scrive nel registro perché il sistema ha chiuso l'app le volte precedenti (crash, memoria,
     * sospensione Samsung, chiusura forzata...). Ogni chiusura viene annotata una sola volta.
     */
    static void logExitReasons(Context c, Prefs prefs) {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        if (am == null) return;
        List<ApplicationExitInfo> list;
        try {
            list = am.getHistoricalProcessExitReasons(c.getPackageName(), 0, 10);
        } catch (RuntimeException e) {
            return;
        }
        long seen = prefs.lastExitSeen();
        long newest = seen;
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALY);
        // la lista arriva dalla più recente: il registro aggiunge in cima, quindi si scrive dalla più vecchia
        for (int i = list.size() - 1; i >= 0; i--) {
            ApplicationExitInfo e = list.get(i);
            if (e.getTimestamp() <= seen) continue;
            newest = Math.max(newest, e.getTimestamp());
            String desc = e.getDescription();
            prefs.log("Chiusura dell'app alle " + f.format(new Date(e.getTimestamp())) + ": "
                    + exitReasonName(e.getReason()) + (desc != null && !desc.isEmpty() ? " (" + desc + ")" : "")
                    + ", importanza " + e.getImportance());
        }
        if (newest != seen) prefs.setLastExitSeen(newest);
    }

    static String exitReasonName(int r) {
        switch (r) {
            case ApplicationExitInfo.REASON_EXIT_SELF: return "uscita normale";
            case ApplicationExitInfo.REASON_SIGNALED: return "terminata dal sistema (segnale)";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "memoria insufficiente";
            case ApplicationExitInfo.REASON_CRASH: return "crash";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "crash nativo";
            case ApplicationExitInfo.REASON_ANR: return "app bloccata (ANR)";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "errore di avvio";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE: return "permesso cambiato";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "consumo eccessivo di risorse";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "chiusa dall'utente o dal sistema";
            case ApplicationExitInfo.REASON_USER_STOPPED: return "utente fermato";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED: return "dipendenza terminata";
            case ApplicationExitInfo.REASON_OTHER: return "altro motivo del sistema";
            case ApplicationExitInfo.REASON_FREEZER: return "congelata dal sistema";
            case ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE: return "stato pacchetto cambiato";
            case ApplicationExitInfo.REASON_PACKAGE_UPDATED: return "aggiornamento dell'app";
            default: return "motivo " + r;
        }
    }

    /** Notifica ben visibile che chiede di aprire l'app e sistemare le impostazioni di batteria. */
    static void alert(Context c, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ALERT, "Avvisi di sospensione",
                NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(c, 2,
                new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(Ui.notificationColor(c))
                .setContentTitle("WG Switch potrebbe smettere di funzionare")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text + "\nTocca per aprire l'app e sistemare."))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        try {
            nm.notify(ALERT_ID, n);
        } catch (SecurityException ignored) {
            // notifiche non consentite
        }
    }

    static void clearAlert(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ALERT_ID);
    }
}
