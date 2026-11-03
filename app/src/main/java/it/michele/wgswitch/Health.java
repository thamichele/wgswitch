package it.michele.wgswitch;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import java.util.ArrayList;
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
