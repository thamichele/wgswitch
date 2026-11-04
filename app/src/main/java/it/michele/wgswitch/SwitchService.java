package it.michele.wgswitch;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TransportInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Servizio in primo piano sempre attivo: ascolta WiFi, Bluetooth e Android Auto e
 * accende o spegne il tunnel. Regola: un'eccezione attiva spegne il tunnel, altrimenti
 * il tunnel resta acceso (la riaccensione aspetta qualche secondo per evitare rimbalzi).
 */
public class SwitchService extends Service {
    static final String ACTION_TOGGLE_PAUSE = "it.michele.wgswitch.TOGGLE_PAUSE";
    static final String ACTION_REEVALUATE = "it.michele.wgswitch.REEVALUATE";

    private static final String CHANNEL = "status";
    private static final String CHANNEL_MIN = "status_min";
    private static final int NOTIFICATION_ID = 1;

    // Contratto pubblico di androidx.car.app.connection.CarConnection, letto senza la libreria.
    private static final String CAR_AUTHORITY = "androidx.car.app.connection";
    private static final String CAR_ACTION_UPDATED = "androidx.car.app.connection.action.CAR_CONNECTION_UPDATED";
    private static final String CAR_COLUMN = "CarConnectionState";
    /** Solo il provider di Android Auto è attendibile: un'altra app non deve poter spegnere il tunnel. */
    private static final String ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead";

    /** Stato corrente, letto dalla UI (stesso processo). */
    static volatile List<String> currentSsids = new ArrayList<>();
    static volatile List<String> currentBt = new ArrayList<>();
    static volatile boolean currentCar;
    static volatile boolean running;
    /** WiFi connesso ma nome non leggibile: di solito manca la posizione (o "Consenti sempre"). */
    static volatile boolean ssidUnreadable;
    /** WireGuard non ha acceso/spento la VPN nemmeno dopo un secondo tentativo. */
    static volatile boolean wireguardNotResponding;

    /** Secondi di attesa prima di verificare che WireGuard abbia eseguito il comando. */
    private static final int VERIFY_DELAY_S = 10;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback wifiCallback;
    private BroadcastReceiver btReceiver;
    private BroadcastReceiver carReceiver;
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    private final Map<Network, String> wifi = new HashMap<>();
    /** MAC -> nome dei dispositivi Bluetooth connessi. */
    private final Map<String, String> bt = new HashMap<>();
    private boolean car;

    /** Ultimo comando inviato: null = nessuno, TRUE = acceso, FALSE = spento. */
    private Boolean lastSent;
    private Runnable pendingUp;
    private String notifiedText;
    private boolean permissionWarned;

    static boolean start(Context c, String reason) {
        Prefs p = new Prefs(c);
        if (c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            p.log("Servizio non avviato: manca il permesso Bluetooth");
            return false;
        }
        try {
            c.startForegroundService(new Intent(c, SwitchService.class).putExtra("reason", reason));
            return true;
        } catch (RuntimeException e) {
            p.log("Servizio non avviato: " + e.getMessage());
            return false;
        }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, SwitchService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        createChannel();
        try {
            startForeground(NOTIFICATION_ID, buildNotification("Avvio…"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } catch (RuntimeException e) {
            prefs.log("Impossibile avviare il servizio: " + e.getMessage());
            stopSelf();
            return;
        }
        running = true;
        Health.logExitReasons(this, prefs);
        prefs.log("Servizio avviato");
        Watchdog.schedule(this);
        // Stato di sospensione all'avvio: se Samsung limita l'app lo si vede subito nel registro.
        handler.post(new Runnable() { @Override public void run() { Watchdog.check(SwitchService.this, "avvio servizio"); }});
        startWifi();
        startBluetooth();
        startCar();
        prefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() { @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
            if (key == null || Prefs.isStateKey(key)) return;
            if (Prefs.KEY_TUNNEL.equals(key)) lastSent = null;
            if (Prefs.KEY_MIN_NOTIF.equals(key)) {
                notifiedText = null;
                setStatus(prefs.status());
                return;
            }
            if (Prefs.KEY_PAUSED.equals(key)) {
                notifiedText = null; // aggiorna il testo del pulsante Pausa/Riprendi
                prefs.log(prefs.paused() ? "Automazione in pausa" : "Automazione ripresa");
                lastSent = null;
            }
            evaluate();
        }};
        prefs.sp.registerOnSharedPreferenceChangeListener(prefsListener);
        evaluate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            prefs.log("Servizio riavviato automaticamente dal sistema");
        } else if (intent.getStringExtra("reason") != null && intent.getAction() == null) {
            prefs.log("Richiesta di avvio: " + intent.getStringExtra("reason"));
        }
        if (intent != null && ACTION_TOGGLE_PAUSE.equals(intent.getAction())) {
            prefs.setPaused(!prefs.paused());
        } else if (intent != null && ACTION_REEVALUATE.equals(intent.getAction())) {
            lastSent = null;
            evaluate();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        ssidUnreadable = false;
        wireguardNotResponding = false;
        handler.removeCallbacksAndMessages(null);
        if (wifiCallback != null) cm.unregisterNetworkCallback(wifiCallback);
        if (btReceiver != null) unregisterReceiver(btReceiver);
        if (carReceiver != null) unregisterReceiver(carReceiver);
        if (prefsListener != null) prefs.sp.unregisterOnSharedPreferenceChangeListener(prefsListener);
        prefs.setStatus("Servizio fermo");
        prefs.log("Servizio fermato");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---- WiFi ----

    private void startWifi() {
        cm = getSystemService(ConnectivityManager.class);
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                // anche le reti senza internet, come il WiFi del dongle di Android Auto wireless
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        wifiCallback = new ConnectivityManager.NetworkCallback(
                ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                TransportInfo info = caps.getTransportInfo();
                String ssid = info instanceof WifiInfo ? cleanSsid(((WifiInfo) info).getSSID()) : null;
                boolean known = wifi.containsKey(network);
                String old = wifi.put(network, ssid);
                if (known && (ssid == null ? old == null : ssid.equals(old))) return;
                if (ssid != null) prefs.log("WiFi connesso: " + ssid);
                else prefs.log("WiFi connesso ma SSID non leggibile: controlla posizione e permessi");
                updateSsidReadable();
                evaluate();
            }

            @Override
            public void onLost(Network network) {
                if (!wifi.containsKey(network)) return;
                String old = wifi.remove(network);
                prefs.log("WiFi disconnesso" + (old != null ? ": " + old : ""));
                updateSsidReadable();
                evaluate();
            }
        };
        cm.registerNetworkCallback(request, wifiCallback, handler);
    }

    /** Segnala (registro + notifica) quando il nome del WiFi diventa illeggibile, cioè le reti di eccezione non funzionano. */
    private void updateSsidReadable() {
        boolean unreadable = wifi.containsValue(null);
        if (unreadable == ssidUnreadable) return;
        ssidUnreadable = unreadable;
        if (unreadable) {
            prefs.log("PERMESSI: non riesco a leggere il nome del WiFi, le reti di eccezione non funzionano");
            Health.alert(this, "Non riesco a leggere il nome della rete WiFi: controlla Posizione \"Consenti sempre\" "
                    + "e che la geolocalizzazione sia attiva.");
        }
    }

    static String cleanSsid(String s) {
        if (s == null || s.equals(WifiManagerCompat.UNKNOWN_SSID) || s.isEmpty()) return null;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s;
    }

    // ---- Bluetooth ----

    private void startBluetooth() {
        btReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                    int st = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON);
                    if (st == BluetoothAdapter.STATE_ON) {
                        prefs.log("Bluetooth acceso");
                        scheduleResync();
                    } else if (st == BluetoothAdapter.STATE_OFF && !bt.isEmpty()) {
                        // Samsung a volte annuncia "spento" mentre il Bluetooth resta attivo (es. con Android Auto
                        // wireless): si svuota l'elenco solo se l'adattatore è davvero spento.
                        BluetoothAdapter a = adapter();
                        if (a != null && a.getState() != BluetoothAdapter.STATE_OFF) {
                            prefs.log("Avviso \"Bluetooth spento\" ignorato: l'adattatore risulta ancora attivo");
                            scheduleResync();
                            return;
                        }
                        bt.clear();
                        prefs.log("Bluetooth spento");
                        evaluate();
                    }
                    return;
                }
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                if (d == null) return;
                String name = deviceName(d);
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
                    bt.put(d.getAddress(), name);
                    prefs.log("Bluetooth connesso: " + name);
                    // Android Auto wireless parte subito dopo il Bluetooth: ricontrolla tra poco.
                    handler.postDelayed(new Runnable() { @Override public void run() { queryCar(); }}, 5000);
                    handler.postDelayed(new Runnable() { @Override public void run() { queryCar(); }}, 20000);
                } else {
                    bt.remove(d.getAddress());
                    prefs.log("Bluetooth disconnesso: " + name);
                    handler.postDelayed(new Runnable() { @Override public void run() { queryCar(); }}, 3000);
                }
                evaluate();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        // Broadcast di sistema: arrivano anche a un receiver non esportato, che le altre app non possono raggiungere.
        registerReceiver(btReceiver, f, Context.RECEIVER_NOT_EXPORTED);

        queryProfiles();
        scheduleResync();
        handler.postDelayed(periodicSync, PERIODIC_SYNC_MS);
    }

    /** Ricontrolla profili Bluetooth e Android Auto qualche secondo dopo un cambiamento (o all'avvio). */
    private void scheduleResync() {
        for (long ms : new long[]{4000, 12000, 25000}) {
            handler.postDelayed(new Runnable() { @Override public void run() { queryProfiles(); queryCar(); }}, ms);
        }
    }

    /**
     * Rete di sicurezza: non ci si affida solo agli eventi (su Samsung a volte mancano), ogni 30 s si rilegge
     * quali dispositivi sono connessi e se Android Auto è attivo.
     */
    private static final long PERIODIC_SYNC_MS = 30000;
    private final Runnable periodicSync = new Runnable() { @Override public void run() {
        if (!running) return;
        queryProfiles();
        queryCar();
        handler.postDelayed(this, PERIODIC_SYNC_MS);
    }};

    private BluetoothAdapter adapter() {
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        return bm != null ? bm.getAdapter() : null;
    }

    /** Dispositivi già connessi sui profili audio e chiamate. */
    private void queryProfiles() {
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
        if (adapter == null) return;
        for (int profile : new int[]{BluetoothProfile.HEADSET, BluetoothProfile.A2DP}) {
            try {
                adapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int p, BluetoothProfile proxy) {
                        try {
                            for (BluetoothDevice d : proxy.getConnectedDevices()) {
                                if (!bt.containsKey(d.getAddress())) {
                                    String name = deviceName(d);
                                    bt.put(d.getAddress(), name);
                                    prefs.log("Bluetooth già connesso: " + name);
                                }
                            }
                        } catch (SecurityException ignored) {
                        }
                        adapter.closeProfileProxy(p, proxy);
                        if (running) evaluate();
                    }

                    @Override
                    public void onServiceDisconnected(int p) {
                    }
                }, profile);
            } catch (SecurityException ignored) {
            }
        }
    }

    static String deviceName(BluetoothDevice d) {
        try {
            String n = d.getAlias();
            if (n == null) n = d.getName();
            return n != null ? n : d.getAddress();
        } catch (SecurityException e) {
            return d.getAddress();
        }
    }

    // ---- Android Auto ----

    private void startCar() {
        carReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                queryCar();
            }
        };
        registerReceiver(carReceiver, new IntentFilter(CAR_ACTION_UPDATED), Context.RECEIVER_EXPORTED);
        queryCar();
    }

    /** Interroga Android Auto in background: 0 = non connesso, 1 = auto nativa, 2 = proiezione. */
    private void queryCar() {
        new Thread(new Runnable() { @Override public void run() {
            int state = 0;
            if (!trustedCarProvider()) {
                postCarState(false);
                return;
            }
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(CAR_AUTHORITY).build();
            try (Cursor c = getContentResolver().query(uri, new String[]{CAR_COLUMN}, null, null, null)) {
                if (c != null && c.moveToNext()) {
                    int idx = c.getColumnIndex(CAR_COLUMN);
                    if (idx >= 0) state = c.getInt(idx);
                }
            } catch (RuntimeException ignored) {
                // Android Auto non installato o provider non disponibile.
            }
            postCarState(state != 0);
        }}).start();
    }

    private void postCarState(final boolean connected) {
        handler.post(new Runnable() { @Override public void run() {
            if (running && connected != car) {
                car = connected;
                prefs.log(connected ? "Android Auto attivo" : "Android Auto non attivo");
                evaluate();
            }
        }});
    }

    private boolean trustedCarProvider() {
        ProviderInfo pi = getPackageManager().resolveContentProvider(CAR_AUTHORITY, 0);
        return pi != null && ANDROID_AUTO_PACKAGE.equals(pi.packageName);
    }

    // ---- Regole ----

    /** Motivo per cui il tunnel deve restare spento, oppure null se deve essere acceso. */
    private String exceptionReason() {
        Set<String> ssids = prefs.ssids();
        for (String s : wifi.values()) {
            if (s != null && ssids.contains(s)) return "WiFi " + s;
        }
        Set<String> devices = prefs.btDevices();
        for (Map.Entry<String, String> e : bt.entrySet()) {
            if (devices.contains(e.getKey())) return "Bluetooth " + e.getValue();
        }
        if (car && prefs.carEnabled()) return "Android Auto";
        return null;
    }

    private void evaluate() {
        publishSnapshot();
        String tunnel = prefs.tunnel();
        if (prefs.paused()) {
            cancelPendingUp();
            setStatus("Automazione in pausa");
            return;
        }
        if (tunnel.isEmpty()) {
            cancelPendingUp();
            setStatus("Imposta il nome del tunnel nell'app");
            return;
        }
        String reason = exceptionReason();
        if (reason != null) {
            cancelPendingUp();
            send(false, reason);
            setStatus("Tunnel OFF · " + reason);
        } else if (!Boolean.TRUE.equals(lastSent)) {
            if (pendingUp == null) {
                int delay = lastSent == null ? 3 : prefs.reenableDelaySec();
                pendingUp = new Runnable() { @Override public void run() {
                    pendingUp = null;
                    if (!prefs.paused() && exceptionReason() == null) {
                        send(true, "nessuna eccezione attiva");
                        setStatus("Tunnel ON");
                    }
                }};
                handler.postDelayed(pendingUp, delay * 1000L);
                setStatus("Riaccensione tra " + delay + " s");
            }
        } else {
            setStatus("Tunnel ON");
        }
    }

    private void cancelPendingUp() {
        if (pendingUp != null) {
            handler.removeCallbacks(pendingUp);
            pendingUp = null;
        }
    }

    private Runnable pendingVerify;

    private void send(boolean up, String why) {
        if (lastSent != null && lastSent == up) return;
        if (!WireGuard.permissionGranted(this)) {
            if (!permissionWarned) {
                prefs.log("PERMESSI: manca il permesso per controllare WireGuard, il tunnel non viene " + (up ? "acceso" : "spento"));
                Health.alert(this, "Manca il permesso \"Controllo di WireGuard\": il tunnel non viene "
                        + (up ? "acceso" : "spento") + ". Apri l'app e concedilo.");
            }
            permissionWarned = true;
            return;
        }
        permissionWarned = false;
        WireGuard.setTunnel(this, prefs.tunnel(), up);
        lastSent = up;
        prefs.log((up ? "Tunnel ACCESO" : "Tunnel SPENTO") + " (" + why + ")");
        scheduleVerify(up, false);
    }

    /** Dopo qualche secondo controlla che la VPN sia davvero accesa/spenta; altrimenti riprova una volta e avvisa. */
    private void scheduleVerify(final boolean up, final boolean retried) {
        if (pendingVerify != null) handler.removeCallbacks(pendingVerify);
        pendingVerify = new Runnable() { @Override public void run() {
            pendingVerify = null;
            if (!running || lastSent == null || lastSent != up) return;
            boolean vpn = Health.vpnActive(SwitchService.this);
            if (vpn == up) {
                if (retried) prefs.log("Verifica: al secondo tentativo WireGuard ha eseguito il comando");
                if (wireguardNotResponding) {
                    wireguardNotResponding = false;
                    Health.clearAlert(SwitchService.this);
                }
                return;
            }
            String state = vpn ? "ancora accesa" : "ancora spenta";
            if (!retried) {
                prefs.log("Verifica: la VPN risulta " + state + ", invio di nuovo il comando a WireGuard");
                WireGuard.setTunnel(SwitchService.this, prefs.tunnel(), up);
                scheduleVerify(up, true);
            } else {
                wireguardNotResponding = true;
                prefs.log("ATTENZIONE: WireGuard non esegue i comandi (VPN " + state + "). Controlla il nome del tunnel, "
                        + "\"Consenti app di controllo remoto\" e che WireGuard non sia in sospensione");
                Health.alert(SwitchService.this, "WireGuard non ha " + (up ? "acceso" : "spento") + " il tunnel. "
                        + "Controlla il nome del tunnel e che l'app WireGuard non sia in sospensione (Batteria: Senza restrizioni).");
            }
        }};
        handler.postDelayed(pendingVerify, VERIFY_DELAY_S * 1000L);
    }

    private void publishSnapshot() {
        List<String> s = new ArrayList<>();
        for (String v : wifi.values()) if (v != null) s.add(v);
        currentSsids = s;
        currentBt = new ArrayList<>(bt.values());
        currentCar = car;
    }

    // ---- Notifica ----

    private void setStatus(String text) {
        if (!text.equals(prefs.status())) prefs.setStatus(text);
        if (text.equals(notifiedText)) return;
        notifiedText = text;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Stato del tunnel", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        NotificationChannel min = new NotificationChannel(CHANNEL_MIN, "Stato del tunnel (minimizzata)",
                NotificationManager.IMPORTANCE_MIN);
        min.setShowBadge(false);
        min.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(ch);
        nm.createNotificationChannel(min);
    }

    private Notification buildNotification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggle = PendingIntent.getService(this, 1,
                new Intent(this, SwitchService.class).setAction(ACTION_TOGGLE_PAUSE), PendingIntent.FLAG_IMMUTABLE);
        boolean paused = prefs != null && prefs.paused();
        boolean minimized = prefs != null && prefs.minimizedNotification();
        return new Notification.Builder(this, minimized ? CHANNEL_MIN : CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(Ui.notificationColor(this))
                .setContentTitle("WG Switch")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(minimized ? Notification.VISIBILITY_SECRET : Notification.VISIBILITY_PRIVATE)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(new Notification.Action.Builder(null, paused ? "Riprendi" : "Pausa", toggle).build())
                .build();
    }

    /** Costanti di WifiManager senza dipendere da metodi deprecati. */
    static final class WifiManagerCompat {
        static final String UNKNOWN_SSID = "<unknown ssid>";
    }
}
