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
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Schermata unica: stato, permessi e configurazione. Interfaccia costruita da codice con i componenti di Ui. */
public class MainActivity extends Activity {
    private static final int REQ_PERMS = 1;

    private Prefs prefs;
    private Ui ui;
    private ScrollView scroll;
    private LinearLayout root;
    /** Card in costruzione: le sezioni aggiungono qui i loro elementi. */
    private LinearLayout cur;
    private TextView statusView;
    private TextView serviceChip;
    private TextView tunnelChip;
    private TextView logView;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() { @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (Prefs.KEY_STATUS.equals(key) || Prefs.KEY_LOG.equals(key)) runOnUiThread(new Runnable() { @Override public void run() { refreshLive(); }});
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setClipToPadding(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        prefs.sp.registerOnSharedPreferenceChangeListener(listener);
        // Riavvia il servizio se il sistema l'ha chiuso e aggiorna lo stato di sospensione.
        Watchdog.schedule(this);
        Watchdog.check(this, "apertura app");
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
        int y = scroll.getScrollY();
        ui = new Ui(this, prefs.accent());
        scroll.setBackgroundColor(ui.bg);
        root.removeAllViews();
        // spaziatura del kit: 16 ai lati, 12 tra le card
        root.setPadding(ui.dp(16), ui.dp(20), ui.dp(16), ui.dp(32));
        root.setDividerDrawable(spacer(12));
        root.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

        buildHeader();
        buildStatus();
        buildPermissions();
        buildStandby();
        buildTunnel();
        buildWifi();
        buildBluetooth();
        buildCar();
        buildService();
        buildAppearance();
        buildLog();

        refreshLive();
        scroll.post(new Runnable() { @Override public void run() { scroll.scrollTo(0, y); }});
    }

    private void refreshLive() {
        if (statusView == null) return;
        String status = SwitchService.running ? prefs.status() : "Servizio fermo";
        statusView.setText(status);
        if (SwitchService.running) ui.styleChip(serviceChip, "Servizio attivo", ui.okFg, ui.okBg);
        else ui.styleChip(serviceChip, "Servizio fermo", ui.errFg, ui.errBg);
        tunnelChip.setVisibility(View.VISIBLE);
        if (!SwitchService.running) {
            tunnelChip.setVisibility(View.GONE);
        } else if (status.startsWith("Tunnel ON")) {
            ui.styleChip(tunnelChip, "Tunnel acceso", ui.okFg, ui.okBg);
        } else if (status.startsWith("Tunnel OFF")) {
            ui.styleChip(tunnelChip, "Eccezione attiva", ui.soft, Ui.alpha(ui.fill, 0.16f));
        } else if (status.startsWith("Riaccensione") || status.startsWith("Automazione in pausa")) {
            ui.styleChip(tunnelChip, status.startsWith("Riaccensione") ? "In attesa" : "In pausa", ui.warnFg, ui.warnBg);
        } else {
            tunnelChip.setVisibility(View.GONE);
        }
        logView.setText(prefs.log().isEmpty() ? "Nessun evento" : prefs.log());
    }

    // ---- Sezioni ----

    private void buildHeader() {
        LinearLayout row = ui.row(12);
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(ui.rect(ui.fill, 0, 8));
        ImageView ic = ui.image(R.drawable.ic_shield, ui.onFill, 22);
        tile.addView(ic, new FrameLayout.LayoutParams(ui.dp(22), ui.dp(22), Gravity.CENTER));
        row.addView(tile, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
        LinearLayout titles = ui.column(2);
        titles.addView(ui.text("Tunnel WireGuard automatico", 13, ui.text2, ui.regular));
        titles.addView(ui.title("WG Switch"));
        row.addView(titles, weighted());
        root.addView(row);
    }

    private void buildStatus() {
        section("Stato");
        statusView = ui.text("", 20, ui.text, ui.semibold);
        cur.addView(statusView);
        FlowLayout chips = ui.flow();
        serviceChip = ui.chip("", ui.okFg, ui.okBg);
        tunnelChip = ui.chip("", ui.okFg, ui.okBg);
        chips.addView(serviceChip);
        chips.addView(tunnelChip);
        cur.addView(chips);
        cur.addView(ui.toggle("Automazione attiva", !prefs.paused(), new CompoundButton.OnCheckedChangeListener() { @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.setPaused(!on); }}));
        if (!Health.problems(this).isEmpty()) {
            cur.addView(ui.callout("Il sistema può fermare WG Switch dopo qualche giorno",
                    "Sistema le voci segnate con la X in \"Batteria e sospensione\"."));
        }
    }

    private void buildPermissions() {
        section("Permessi");
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
        buttons(ui.button(Ui.SECONDARY, "Apri impostazioni dell'app", R.drawable.ic_open, new View.OnClickListener() { @Override public void onClick(View v) { startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }}));
    }

    // ---- Sospensione / batteria ----

    private void buildStandby() {
        section("Batteria e sospensione");
        permRow("Batteria: esclusa dall'ottimizzazione", Health.ignoringBatteryOptimizations(this), "Escludi",
                new View.OnClickListener() { @Override public void onClick(View v) { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()))); }});
        permRow("Uso in background non limitato", !Health.backgroundRestricted(this), "Apri",
                new View.OnClickListener() { @Override public void onClick(View v) { openAppBattery(); }});
        permRow("\"Sospendi attività app se non utilizzata\" disattivato", Health.hibernationDisabled(this), "Apri",
                new View.OnClickListener() { @Override public void onClick(View v) { openOrAppDetails(new Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS,
                        Uri.parse("package:" + getPackageName()))); }});
        int bucket = Health.standbyBucket(this);
        permRow("Stato standby di sistema: " + Health.bucketName(bucket), !Health.bucketLimited(bucket), null, null);
        long last = prefs.lastCheck();
        hint("Ultimo controllo automatico: " + (last == 0 ? "mai" :
                new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.ITALY).format(new java.util.Date(last)))
                + ". Ogni 15 minuti circa l'app verifica che il servizio sia vivo e lo riavvia se serve.");
        if (isXiaomi()) {
            buildXiaomi();
        } else if (Health.isSamsung()) {
            buildSamsung();
        }
    }

    private void buildSamsung() {
        hint("Samsung mette \"in sospensione\" le app che non apri per qualche giorno, anche se lavorano in background. "
                + "Per evitarlo:");
        step("1. Batteria dell'app: scegli \"Senza restrizioni\".");
        buttons(ui.button(Ui.SECONDARY, "Apri batteria dell'app", R.drawable.ic_open, new View.OnClickListener() { @Override public void onClick(View v) { openAppBattery(); }}));
        step("2. Impostazioni > Assistenza dispositivo > Batteria > Limiti utilizzo in background: "
                + "aggiungi WG Switch ad \"App mai in sospensione\" e controlla che non sia tra le app in sospensione "
                + "o in sospensione profonda. Se vuoi, disattiva \"Metti in sospensione le app non utilizzate\".");
        buttons(ui.button(Ui.SECONDARY, "Apri Limiti utilizzo in background", R.drawable.ic_open, new View.OnClickListener() { @Override public void onClick(View v) {
            openFirst(new Intent().setComponent(new ComponentName("com.samsung.android.lool",
                            "com.samsung.android.sm.battery.ui.BatteryActivity")),
                    new Intent("com.samsung.android.sm.ACTION_BATTERY"),
                    new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }}));
        step("3. Impostazioni dell'app: disattiva \"Sospendi attività app se non utilizzata\".");
    }

    /** Pagina batteria dell'app (Senza restrizioni / Ottimizzata / Limitata), oppure le info dell'app. */
    private void openAppBattery() {
        openOrAppDetails(new Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL",
                Uri.parse("package:" + getPackageName())));
    }

    /** Apre il primo intent disponibile; in ultima istanza le impostazioni dell'app. */
    private void openFirst(Intent... intents) {
        for (Intent i : intents) {
            try {
                startActivity(i);
                return;
            } catch (RuntimeException ignored) {
            }
        }
        openOrAppDetails(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private void buildTunnel() {
        section("Tunnel WireGuard");
        final EditText name = ui.field(prefs.tunnel(), "es. casa");
        cur.addView(ui.labeled("Nome del tunnel", name, "Il nome esatto del tunnel come appare nell'app WireGuard."));
        buttons(ui.button(Ui.PRIMARY, "Salva", R.drawable.ic_check, new View.OnClickListener() { @Override public void onClick(View v) {
                    prefs.setTunnel(name.getText().toString());
                    toast("Tunnel salvato");
                }}),
                ui.button(Ui.SECONDARY, "Test ON", R.drawable.ic_power, new View.OnClickListener() { @Override public void onClick(View v) { test(name.getText().toString(), true); }}),
                ui.button(Ui.SECONDARY, "Test OFF", R.drawable.ic_power_off, new View.OnClickListener() { @Override public void onClick(View v) { test(name.getText().toString(), false); }}));

        final EditText delay = ui.field(String.valueOf(prefs.reenableDelaySec()), "10");
        delay.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout delayRow = ui.row(8);
        delayRow.addView(delay, new LinearLayout.LayoutParams(ui.dp(120), ViewGroup.LayoutParams.WRAP_CONTENT));
        delayRow.addView(ui.button(Ui.SECONDARY, "Salva", 0, new View.OnClickListener() { @Override public void onClick(View v) {
            try {
                prefs.setReenableDelaySec(Integer.parseInt(delay.getText().toString().trim()));
                toast("Ritardo salvato");
            } catch (NumberFormatException e) {
                toast("Numero non valido");
            }
        }}));
        cur.addView(ui.labeled("Ritardo prima di riaccendere il tunnel (secondi)", delayRow, null));
    }

    private void buildWifi() {
        section("Reti WiFi di eccezione");
        hint("Connesso a queste reti, il tunnel si spegne.");
        Set<String> ssids = prefs.ssids();
        List<String> sorted = new ArrayList<>(ssids);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        if (sorted.isEmpty()) cur.addView(ui.body("Nessuna rete."));
        for (final String s : sorted) {
            LinearLayout row = ui.row(12);
            row.addView(ui.image(R.drawable.ic_wifi, ui.text2, 20));
            row.addView(ui.body(s), weighted());
            TextView rm = ui.button(Ui.DANGER, "Rimuovi", R.drawable.ic_trash, new View.OnClickListener() { @Override public void onClick(View v) {
                Set<String> cur = prefs.ssids();
                cur.remove(s);
                prefs.setSsids(cur);
                build();
            }});
            rm.setContentDescription("Rimuovi " + s);
            row.addView(rm);
            cur.addView(row);
        }
        final String current = currentSsid();
        hint("Rete attuale: " + (current != null ? current : "nessuna o non leggibile"));
        if (current != null && !ssids.contains(current)) {
            buttons(ui.button(Ui.PRIMARY, "Aggiungi \"" + current + "\"", R.drawable.ic_plus, new View.OnClickListener() { @Override public void onClick(View v) { addSsid(current); }}));
        }
        LinearLayout row = ui.row(8);
        final EditText manual = ui.field("", "Nome della rete");
        row.addView(manual, weighted());
        row.addView(ui.button(Ui.SECONDARY, "Aggiungi", R.drawable.ic_plus, new View.OnClickListener() { @Override public void onClick(View v) {
            String s = manual.getText().toString().trim();
            if (!s.isEmpty()) addSsid(s);
        }}));
        cur.addView(ui.labeled("SSID manuale", row, null));
    }

    private void buildBluetooth() {
        section("Dispositivi Bluetooth di eccezione");
        hint("Connesso a questi dispositivi (es. l'autoradio), il tunnel si spegne.");
        if (!granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            cur.addView(ui.body("Concedi il permesso Bluetooth per vedere i dispositivi associati."));
            return;
        }
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
        Set<BluetoothDevice> bonded = adapter != null ? adapter.getBondedDevices() : null;
        if (bonded == null || bonded.isEmpty()) {
            cur.addView(ui.body("Nessun dispositivo associato (o Bluetooth spento)."));
            return;
        }
        List<BluetoothDevice> list = new ArrayList<>(bonded);
        Collections.sort(list, new java.util.Comparator<BluetoothDevice>() { @Override public int compare(BluetoothDevice a, BluetoothDevice b) { return SwitchService.deviceName(a).compareToIgnoreCase(SwitchService.deviceName(b)); }});
        Set<String> selected = prefs.btDevices();
        List<String> connected = SwitchService.currentBt;
        LinearLayout items = ui.column(0);
        for (final BluetoothDevice d : list) {
            String name = SwitchService.deviceName(d);
            LinearLayout row = ui.row(8);
            row.addView(ui.check(name, selected.contains(d.getAddress()), new CompoundButton.OnCheckedChangeListener() { @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                Set<String> cur = prefs.btDevices();
                if (on) cur.add(d.getAddress()); else cur.remove(d.getAddress());
                prefs.setBtDevices(cur);
            }}), weighted());
            if (connected.contains(name)) row.addView(ui.chip("connesso", ui.okFg, ui.okBg));
            items.addView(row);
        }
        cur.addView(items);
    }

    private void buildCar() {
        section("Android Auto");
        cur.addView(ui.toggle("Spegni il tunnel quando Android Auto è attivo", prefs.carEnabled(), new CompoundButton.OnCheckedChangeListener() { @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.setCarEnabled(on); }}));
        FlowLayout chips = ui.flow();
        chips.addView(SwitchService.currentCar ? ui.chip("Android Auto attivo", ui.soft, Ui.alpha(ui.fill, 0.16f))
                : ui.chip("Android Auto non attivo", ui.text2, ui.raised));
        cur.addView(chips);
        hint("Con Android Auto wireless conviene selezionare anche l'autoradio tra i dispositivi Bluetooth.");
    }

    private void buildService() {
        section("Servizio");
        buttons(ui.button(Ui.SECONDARY, "Riavvia servizio", R.drawable.ic_refresh, new View.OnClickListener() { @Override public void onClick(View v) {
                    SwitchService.stop(MainActivity.this);
                    root.postDelayed(new Runnable() { @Override public void run() {
                        SwitchService.start(MainActivity.this, "riavvio manuale");
                        root.postDelayed(new Runnable() { @Override public void run() { build(); }}, 500);
                    }}, 500);
                }}),
                ui.button(Ui.GHOST, "Aggiorna", 0, new View.OnClickListener() { @Override public void onClick(View v) { build(); }}));
    }

    private void buildAppearance() {
        section("Aspetto e notifiche");
        cur.addView(ui.label("Colore principale"));
        FlowLayout picker = ui.flow();
        String selected = Ui.accent(prefs.accent()).id;
        for (final Ui.Accent a : Ui.ACCENTS) {
            picker.addView(ui.swatch(a, a.id.equals(selected), new View.OnClickListener() { @Override public void onClick(View v) {
                prefs.setAccent(a.id);
                build();
            }}));
        }
        cur.addView(picker);
        hint("Chiaro o scuro segue il tema del telefono.");
        cur.addView(ui.toggle("Notifica minimizzata (niente icona in barra)", prefs.minimizedNotification(), new CompoundButton.OnCheckedChangeListener() { @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.setMinimizedNotification(on); }}));
    }

    private void buildLog() {
        section("Registro eventi");
        logView = ui.text("", 12, ui.text, ui.regular);
        logView.setLineSpacing(0, 1.5f);
        logView.setTextIsSelectable(true);
        logView.setBackground(ui.rect(ui.dark ? ui.bg : ui.raised, ui.border, 8));
        logView.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        cur.addView(logView);
        buttons(ui.button(Ui.DANGER, "Svuota registro", R.drawable.ic_trash, new View.OnClickListener() { @Override public void onClick(View v) { prefs.clearLog(); }}));
    }

    // ---- Xiaomi / HyperOS ----

    static boolean isXiaomi() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT);
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    private void buildXiaomi() {
        hint("Xiaomi / HyperOS chiude le app in background: servono questi tre passaggi.");
        step("1. Avvio automatico: attivalo per WG Switch.");
        buttons(ui.button(Ui.SECONDARY, "Apri Avvio automatico", R.drawable.ic_open, new View.OnClickListener() { @Override public void onClick(View v) {
            Intent i = new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            openOrAppDetails(i);
        }}));
        step("2. Risparmio batteria: scegli \"Nessuna restrizione\".");
        buttons(ui.button(Ui.SECONDARY, "Apri Risparmio batteria dell'app", R.drawable.ic_open, new View.OnClickListener() { @Override public void onClick(View v) {
            Intent i = new Intent().setComponent(new ComponentName("com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"));
            i.putExtra("package_name", getPackageName());
            i.putExtra("package_label", "WG Switch");
            openOrAppDetails(i);
        }}));
        step("3. Nelle app recenti tieni premuta la scheda di WG Switch e tocca il lucchetto per bloccarla.");
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

    // ---- Composizione ----

    private void section(String title) {
        cur = ui.card(title);
        root.addView(cur);
    }

    /** Riga di controllo: icona ok (teal) o da sistemare (rosa), testo e pulsante d'azione se serve. */
    private void permRow(String label, boolean ok, String action, View.OnClickListener onClick) {
        LinearLayout row = ui.row(12);
        ImageView ic = ui.image(ok ? R.drawable.ic_check : R.drawable.ic_x, ok ? ui.okFg : ui.errFg, 20);
        ic.setContentDescription(ok ? "a posto" : "da sistemare");
        row.addView(ic);
        row.addView(ui.body(label), weighted());
        if (!ok && action != null) row.addView(ui.button(Ui.TONAL, action, 0, onClick));
        row.setMinimumHeight(ui.dp(44));
        cur.addView(row);
    }

    private void hint(String s) {
        cur.addView(ui.hint(s));
    }

    private void step(String s) {
        cur.addView(ui.body(s));
    }

    private void buttons(View... views) {
        FlowLayout f = ui.flow();
        for (View v : views) f.addView(v);
        cur.addView(f);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private android.graphics.drawable.Drawable spacer(int dp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(0);
        d.setSize(0, ui.dp(dp));
        return d;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
