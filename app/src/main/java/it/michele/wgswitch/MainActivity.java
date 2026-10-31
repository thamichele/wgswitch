package it.michele.wgswitch;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Schermata unica: stato, permessi e configurazione. Interfaccia costruita da codice. */
public class MainActivity extends Activity {
    private static final int REQ_PERMS = 1;

    private Prefs prefs;
    private LinearLayout root;
    private TextView statusView;
    private TextView logView;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() { @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (Prefs.KEY_STATUS.equals(key) || Prefs.KEY_LOG.equals(key)) runOnUiThread(new Runnable() { @Override public void run() { refreshLive(); }});
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad * 2);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        prefs.sp.registerOnSharedPreferenceChangeListener(listener);
        if (!SwitchService.running && granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            SwitchService.start(this, "apertura app");
        }
        build();
    }

    @Override
    protected void onPause() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(listener);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!SwitchService.running && granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            SwitchService.start(this, "permessi concessi");
        }
        build();
    }

    private void build() {
        root.removeAllViews();

        statusView = text("", 20, true);
        root.addView(statusView);

        Switch auto = new Switch(this);
        auto.setText("Automazione attiva");
        auto.setChecked(!prefs.paused());
        auto.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) { prefs.setPaused(!on); }});
        root.addView(auto, matchWrap(dp(8)));

        Switch minNotif = new Switch(this);
        minNotif.setText("Notifica minimizzata (niente icona in barra)");
        minNotif.setChecked(prefs.minimizedNotification());
        minNotif.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) { prefs.setMinimizedNotification(on); }});
        root.addView(minNotif, matchWrap(dp(4)));

        buildPermissions();
        buildTunnel();
        buildWifi();
        buildBluetooth();
        buildCar();
        buildService();

        header("Registro eventi");
        logView = text("", 12, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        root.addView(logView);
        root.addView(button("Svuota registro", new View.OnClickListener() { @Override public void onClick(View v) { prefs.clearLog(); }}));

        refreshLive();
    }

    private void refreshLive() {
        if (statusView == null) return;
        String status = SwitchService.running ? prefs.status() : "Servizio fermo";
        statusView.setText(status);
        logView.setText(prefs.log().isEmpty() ? "Nessun evento" : prefs.log());
    }

    // ---- Sezioni ----

    private void buildPermissions() {
        header("Permessi");
        boolean wgInstalled = WireGuard.installed(this);
        permRow("App WireGuard installata", wgInstalled, null, null);
        permRow("Controllo di WireGuard", WireGuard.permissionGranted(this), "Concedi",
                new View.OnClickListener() { @Override public void onClick(View v) { request(WireGuard.PERMISSION); }});
        hint("In WireGuard: Impostazioni > \"Consenti app di controllo remoto\" deve essere attivo. "
                + "Se WireGuard è stato installato dopo questa app, reinstalla questa app.");
        permRow("Bluetooth (dispositivi connessi)", granted(Manifest.permission.BLUETOOTH_CONNECT), "Concedi",
                new View.OnClickListener() { @Override public void onClick(View v) { request(Manifest.permission.BLUETOOTH_CONNECT); }});
        permRow("Posizione (per leggere il nome del WiFi)", granted(Manifest.permission.ACCESS_FINE_LOCATION), "Concedi",
                new View.OnClickListener() { @Override public void onClick(View v) { request(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION); }});
        boolean fine = granted(Manifest.permission.ACCESS_FINE_LOCATION);
        permRow("Posizione in background (\"Consenti sempre\")",
                granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION), fine ? "Concedi" : null,
                new View.OnClickListener() { @Override public void onClick(View v) { request(Manifest.permission.ACCESS_BACKGROUND_LOCATION); }});
        LocationManager lm = getSystemService(LocationManager.class);
        permRow("Geolocalizzazione di sistema attiva", lm != null && lm.isLocationEnabled(), "Apri",
                new View.OnClickListener() { @Override public void onClick(View v) { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }});
        permRow("Notifiche", granted(Manifest.permission.POST_NOTIFICATIONS), "Concedi",
                new View.OnClickListener() { @Override public void onClick(View v) { request(Manifest.permission.POST_NOTIFICATIONS); }});
        PowerManager pm = getSystemService(PowerManager.class);
        permRow("Esclusa dall'ottimizzazione batteria", pm.isIgnoringBatteryOptimizations(getPackageName()), "Escludi",
                new View.OnClickListener() { @Override public void onClick(View v) { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()))); }});
        if (isXiaomi()) {
            buildXiaomi();
        } else {
            hint("Samsung: Impostazioni > Batteria > Limiti utilizzo in background > \"App mai in sospensione\": "
                    + "aggiungi WG Switch.");
        }
        root.addView(button("Apri impostazioni dell'app", new View.OnClickListener() { @Override public void onClick(View v) { startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }}));
    }

    private void buildTunnel() {
        header("Tunnel WireGuard");
        hint("Il nome esatto del tunnel come appare nell'app WireGuard.");
        EditText name = new EditText(this);
        name.setSingleLine();
        name.setHint("es. casa");
        name.setText(prefs.tunnel());
        root.addView(name, matchWrap(0));
        LinearLayout row = hRow();
        row.addView(button("Salva", new View.OnClickListener() { @Override public void onClick(View v) {
            prefs.setTunnel(name.getText().toString());
            toast("Tunnel salvato");
        }}));
        row.addView(button("Test ON", new View.OnClickListener() { @Override public void onClick(View v) { test(name.getText().toString(), true); }}));
        row.addView(button("Test OFF", new View.OnClickListener() { @Override public void onClick(View v) { test(name.getText().toString(), false); }}));
        root.addView(row);

        hint("Ritardo prima di riaccendere il tunnel (secondi)");
        LinearLayout delayRow = hRow();
        EditText delay = new EditText(this);
        delay.setInputType(InputType.TYPE_CLASS_NUMBER);
        delay.setText(String.valueOf(prefs.reenableDelaySec()));
        delay.setEms(4);
        delayRow.addView(delay);
        delayRow.addView(button("Salva", new View.OnClickListener() { @Override public void onClick(View v) {
            try {
                prefs.setReenableDelaySec(Integer.parseInt(delay.getText().toString().trim()));
                toast("Ritardo salvato");
            } catch (NumberFormatException e) {
                toast("Numero non valido");
            }
        }}));
        root.addView(delayRow);
    }

    private void buildWifi() {
        header("Reti WiFi di eccezione");
        hint("Connesso a queste reti, il tunnel si spegne.");
        Set<String> ssids = prefs.ssids();
        List<String> sorted = new ArrayList<>(ssids);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        if (sorted.isEmpty()) hint("Nessuna rete.");
        for (String s : sorted) {
            LinearLayout row = hRow();
            TextView t = text(s, 16, false);
            row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(button("Rimuovi", new View.OnClickListener() { @Override public void onClick(View v) {
                Set<String> cur = prefs.ssids();
                cur.remove(s);
                prefs.setSsids(cur);
                build();
            }}));
            root.addView(row);
        }
        String current = currentSsid();
        hint("Rete attuale: " + (current != null ? current : "nessuna o non leggibile"));
        if (current != null && !ssids.contains(current)) {
            root.addView(button("Aggiungi \"" + current + "\"", new View.OnClickListener() { @Override public void onClick(View v) { addSsid(current); }}));
        }
        LinearLayout row = hRow();
        EditText manual = new EditText(this);
        manual.setSingleLine();
        manual.setHint("SSID manuale");
        row.addView(manual, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(button("Aggiungi", new View.OnClickListener() { @Override public void onClick(View v) {
            String s = manual.getText().toString().trim();
            if (!s.isEmpty()) addSsid(s);
        }}));
        root.addView(row);
    }

    private void buildBluetooth() {
        header("Dispositivi Bluetooth di eccezione");
        hint("Connesso a questi dispositivi (es. l'autoradio), il tunnel si spegne.");
        if (!granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            hint("Concedi il permesso Bluetooth per vedere i dispositivi associati.");
            return;
        }
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
        Set<BluetoothDevice> bonded = adapter != null ? adapter.getBondedDevices() : null;
        if (bonded == null || bonded.isEmpty()) {
            hint("Nessun dispositivo associato (o Bluetooth spento).");
            return;
        }
        List<BluetoothDevice> list = new ArrayList<>(bonded);
        Collections.sort(list, new java.util.Comparator<BluetoothDevice>() { @Override public int compare(BluetoothDevice a, BluetoothDevice b) { return SwitchService.deviceName(a).compareToIgnoreCase(SwitchService.deviceName(b)); }});
        Set<String> selected = prefs.btDevices();
        List<String> connected = SwitchService.currentBt;
        for (BluetoothDevice d : list) {
            String name = SwitchService.deviceName(d);
            CheckBox cb = new CheckBox(this);
            cb.setText(name + (connected.contains(name) ? "  (connesso)" : ""));
            cb.setChecked(selected.contains(d.getAddress()));
            cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                Set<String> cur = prefs.btDevices();
                if (on) cur.add(d.getAddress()); else cur.remove(d.getAddress());
                prefs.setBtDevices(cur);
            }});
            root.addView(cb);
        }
    }

    private void buildCar() {
        header("Android Auto");
        CheckBox cb = new CheckBox(this);
        cb.setText("Spegni il tunnel quando Android Auto è attivo");
        cb.setChecked(prefs.carEnabled());
        cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) { prefs.setCarEnabled(on); }});
        root.addView(cb);
        hint("Stato attuale: " + (SwitchService.currentCar ? "attivo" : "non attivo")
                + ". Con Android Auto wireless conviene selezionare anche l'autoradio tra i dispositivi Bluetooth.");
    }

    private void buildService() {
        header("Servizio");
        LinearLayout row = hRow();
        row.addView(button("Riavvia servizio", new View.OnClickListener() { @Override public void onClick(View v) {
            SwitchService.stop(MainActivity.this);
            root.postDelayed(new Runnable() { @Override public void run() {
                SwitchService.start(MainActivity.this, "riavvio manuale");
                root.postDelayed(new Runnable() { @Override public void run() { build(); }}, 500);
            }}, 500);
        }}));
        row.addView(button("Aggiorna", new View.OnClickListener() { @Override public void onClick(View v) { build(); }}));
        root.addView(row);
    }

    // ---- Xiaomi / HyperOS ----

    static boolean isXiaomi() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT);
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    private void buildXiaomi() {
        hint("Xiaomi / HyperOS chiude le app in background: servono questi tre passaggi.");
        hint("1. Avvio automatico: attivalo per WG Switch.");
        root.addView(button("Apri Avvio automatico", new View.OnClickListener() { @Override public void onClick(View v) {
            Intent i = new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            openOrAppDetails(i);
        }}));
        hint("2. Risparmio batteria: scegli \"Nessuna restrizione\".");
        root.addView(button("Apri Risparmio batteria dell'app", new View.OnClickListener() { @Override public void onClick(View v) {
            Intent i = new Intent().setComponent(new ComponentName("com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"));
            i.putExtra("package_name", getPackageName());
            i.putExtra("package_label", "WG Switch");
            openOrAppDetails(i);
        }}));
        hint("3. Nelle app recenti tieni premuta la scheda di WG Switch e tocca il lucchetto per bloccarla.");
    }

    /** Apre una schermata specifica del produttore; se non esiste, apre le impostazioni dell'app. */
    private void openOrAppDetails(Intent i) {
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            toast("Schermata non trovata: apro le impostazioni dell'app");
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    // ---- Azioni ----

    private void test(String tunnel, boolean up) {
        if (tunnel.trim().isEmpty()) {
            toast("Scrivi il nome del tunnel");
            return;
        }
        if (!WireGuard.permissionGranted(this)) {
            toast("Concedi prima il controllo di WireGuard");
            return;
        }
        WireGuard.setTunnel(this, tunnel.trim(), up);
        prefs.log("Test manuale: tunnel " + (up ? "ACCESO" : "SPENTO"));
        toast("Comando inviato a WireGuard");
    }

    private void addSsid(String s) {
        Set<String> cur = prefs.ssids();
        cur.add(s);
        prefs.setSsids(cur);
        build();
    }

    @SuppressWarnings("deprecation")
    private String currentSsid() {
        if (!SwitchService.currentSsids.isEmpty()) return SwitchService.currentSsids.get(0);
        try {
            WifiManager wm = getSystemService(WifiManager.class);
            return SwitchService.cleanSsid(wm.getConnectionInfo().getSSID());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void request(String... perms) {
        requestPermissions(perms, REQ_PERMS);
    }

    private boolean granted(String perm) {
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    // ---- Widget ----

    private void permRow(String label, boolean ok, String action, View.OnClickListener onClick) {
        LinearLayout row = hRow();
        TextView t = text((ok ? "✓  " : "✗  ") + label, 15, false);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (!ok && action != null) row.addView(button(action, onClick));
        root.addView(row);
    }

    private void header(String s) {
        TextView t = text(s, 17, true);
        root.addView(t, matchWrap(dp(24)));
    }

    private void hint(String s) {
        TextView t = text(s, 13, false);
        t.setAlpha(0.7f);
        root.addView(t, matchWrap(dp(4)));
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String label, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(onClick);
        return b;
    }

    private LinearLayout hRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
