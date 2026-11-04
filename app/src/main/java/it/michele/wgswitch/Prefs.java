package it.michele.wgswitch;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Configurazione e stato condiviso tra UI e servizio. */
final class Prefs {
    static final String KEY_TUNNEL = "tunnel";
    static final String KEY_SSIDS = "ssids";
    static final String KEY_BT = "bt_devices";
    static final String KEY_CAR = "car_enabled";
    static final String KEY_DELAY = "reenable_delay";
    static final String KEY_PAUSED = "paused";
    static final String KEY_MIN_NOTIF = "min_notification";
    static final String KEY_STATUS = "status";
    static final String KEY_LOG = "log";
    static final String KEY_LAST_CHECK = "last_check";
    static final String KEY_LAST_HEALTH = "last_health";
    static final String KEY_LAST_EXIT = "last_exit_seen";

    /** Chiavi di stato che non cambiano la configurazione: il servizio non deve rivalutare per queste. */
    static boolean isStateKey(String key) {
        return KEY_STATUS.equals(key) || KEY_LOG.equals(key) || KEY_LAST_CHECK.equals(key) || KEY_LAST_HEALTH.equals(key)
                || KEY_LAST_EXIT.equals(key);
    }

    private static final int MAX_LOG_LINES = 100;

    final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("wgswitch", Context.MODE_PRIVATE);
    }

    String tunnel() { return sp.getString(KEY_TUNNEL, "").trim(); }
    void setTunnel(String v) { sp.edit().putString(KEY_TUNNEL, v.trim()).apply(); }

    Set<String> ssids() { return new HashSet<>(sp.getStringSet(KEY_SSIDS, new HashSet<String>())); }
    void setSsids(Set<String> v) { sp.edit().putStringSet(KEY_SSIDS, new HashSet<>(v)).apply(); }

    /** Indirizzi MAC dei dispositivi Bluetooth di eccezione. */
    Set<String> btDevices() { return new HashSet<>(sp.getStringSet(KEY_BT, new HashSet<String>())); }
    void setBtDevices(Set<String> v) { sp.edit().putStringSet(KEY_BT, new HashSet<>(v)).apply(); }

    boolean carEnabled() { return sp.getBoolean(KEY_CAR, true); }
    void setCarEnabled(boolean v) { sp.edit().putBoolean(KEY_CAR, v).apply(); }

    int reenableDelaySec() { return sp.getInt(KEY_DELAY, 10); }
    void setReenableDelaySec(int v) { sp.edit().putInt(KEY_DELAY, Math.max(0, Math.min(v, 600))).apply(); }

    boolean paused() { return sp.getBoolean(KEY_PAUSED, false); }
    void setPaused(boolean v) { sp.edit().putBoolean(KEY_PAUSED, v).apply(); }

    boolean minimizedNotification() { return sp.getBoolean(KEY_MIN_NOTIF, false); }
    void setMinimizedNotification(boolean v) { sp.edit().putBoolean(KEY_MIN_NOTIF, v).apply(); }

    String status() { return sp.getString(KEY_STATUS, "Servizio non avviato"); }
    void setStatus(String v) { sp.edit().putString(KEY_STATUS, v).apply(); }

    long lastCheck() { return sp.getLong(KEY_LAST_CHECK, 0); }
    void setLastCheck(long v) { sp.edit().putLong(KEY_LAST_CHECK, v).apply(); }

    String lastHealth() { return sp.getString(KEY_LAST_HEALTH, ""); }
    void setLastHealth(String v) { sp.edit().putString(KEY_LAST_HEALTH, v).apply(); }

    long lastExitSeen() { return sp.getLong(KEY_LAST_EXIT, 0); }
    void setLastExitSeen(long v) { sp.edit().putLong(KEY_LAST_EXIT, v).apply(); }

    String log() { return sp.getString(KEY_LOG, ""); }

    void log(String msg) { log(msg, false); }

    /** sync = true scrive subito su disco (usato dal crash handler, prima che il processo muoia). */
    synchronized void log(String msg, boolean sync) {
        String line = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALY).format(new Date()) + "  " + msg;
        String[] old = log().isEmpty() ? new String[0] : log().split("\n");
        StringBuilder sb = new StringBuilder(line);
        for (int i = 0; i < old.length && i < MAX_LOG_LINES - 1; i++) sb.append('\n').append(old[i]);
        SharedPreferences.Editor e = sp.edit().putString(KEY_LOG, sb.toString());
        if (sync) e.commit(); else e.apply();
    }

    void clearLog() { sp.edit().putString(KEY_LOG, "").apply(); }
}
