package it.michele.wgswitch;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.PowerManager;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Kill switch di emergenza: controlla se il gateway WireGuard (FQDN o IP pubblico) risponde.
 * Con una porta TCP impostata prova una connessione (anche "connessione rifiutata" vuol dire che l'host risponde),
 * se fallisce o senza porta usa il ping. Le chiamate sono bloccanti: vanno fatte fuori dal thread principale.
 */
final class Gateway {
    private static final int TIMEOUT_MS = 3000;

    private Gateway() {}

    static final class Result {
        final boolean ok;
        final String detail;

        Result(boolean ok, String detail) {
            this.ok = ok;
            this.detail = detail;
        }
    }

    /** Toglie spazi, schema e percorso: "https://vpn.example.org/" -> "vpn.example.org". null se non valido. */
    static String cleanHost(String s) {
        if (s == null) return null;
        s = s.trim();
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        if (s.isEmpty() || s.length() > 253) return null;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                    || ch == '.' || ch == '-' || ch == ':';
            if (!ok) return null;
        }
        return s.toLowerCase(Locale.ROOT);
    }

    /** Rete fisica (WiFi o dati) con internet verificato, oppure null se il telefono è senza connessione. */
    static Network underlyingNetwork(Context c) {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        if (cm == null) return null;
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return n;
        }
        return null;
    }

    /**
     * true se un fallimento del controllo non dice nulla sul gateway: telefono senza internet, oppure
     * Doze attivo senza esclusione dalla batteria (il sistema blocca la rete dell'app).
     */
    static String skipReason(Context c) {
        if (underlyingNetwork(c) == null) return "telefono senza internet";
        PowerManager pm = c.getSystemService(PowerManager.class);
        if (pm != null && pm.isDeviceIdleMode() && !Health.ignoringBatteryOptimizations(c)) {
            return "telefono in Doze e app non esclusa dall'ottimizzazione batteria";
        }
        return null;
    }

    static Result check(Context c, String host, int port) {
        if (host == null || host.isEmpty()) return new Result(false, "gateway non impostato");
        String tcpFail = null;
        if (port > 0) {
            Result r = tcp(c, host, port);
            if (r.ok) return r;
            tcpFail = r.detail;
        }
        Result p = ping(host);
        if (p.ok) return tcpFail == null ? p : new Result(true, p.detail + " (TCP " + port + ": " + tcpFail + ")");
        return new Result(false, tcpFail == null ? p.detail : "TCP " + port + ": " + tcpFail + "; " + p.detail);
    }

    /**
     * Connessione TCP. Prova prima sulla rete fisica (stessa strada che usa WireGuard per l'endpoint); se Android
     * non lo consente, perché la VPN è attiva e non aggirabile, passa dalla strada normale.
     */
    private static Result tcp(Context c, String host, int port) {
        Network n = underlyingNetwork(c);
        if (n != null) {
            Socket s = new Socket();
            try {
                InetAddress addr = n.getByName(host);
                n.bindSocket(s);
                return connect(s, addr, port, "rete fisica");
            } catch (IOException | SecurityException e) {
                // bind non consentito o DNS fallito sulla rete fisica: si riprova sulla strada normale
            } finally {
                closeQuietly(s);
            }
        }
        Socket s = new Socket();
        try {
            return connect(s, InetAddress.getByName(host), port, "via VPN/rete attiva");
        } catch (IOException e) {
            return new Result(false, describe(e));
        } finally {
            closeQuietly(s);
        }
    }

    private static Result connect(Socket s, InetAddress addr, int port, String path) throws IOException {
        long t0 = System.nanoTime();
        try {
            s.connect(new InetSocketAddress(addr, port), TIMEOUT_MS);
        } catch (ConnectException e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            // ECONNREFUSED: l'host c'è, la porta è solo chiusa
            if (!m.contains("ECONNREFUSED") && !m.toLowerCase(Locale.ROOT).contains("refused")) throw e;
        }
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        return new Result(true, "TCP " + port + " risponde in " + ms + " ms (" + path + ")");
    }

    /** Ping ICMP con il comando di sistema (non serve root). */
    private static Result ping(String host) {
        Process p = null;
        try {
            p = new ProcessBuilder("/system/bin/ping", "-c", "1", "-W", String.valueOf(TIMEOUT_MS / 1000), host)
                    .redirectErrorStream(true).start();
            if (!p.waitFor(TIMEOUT_MS + 2000, TimeUnit.MILLISECONDS)) {
                return new Result(false, "ping senza risposta");
            }
            int code = p.exitValue();
            if (code == 0) return new Result(true, "ping ok");
            return new Result(false, code == 2 ? "ping: host non risolto o rete assente" : "ping senza risposta");
        } catch (IOException e) {
            return new Result(false, "ping non disponibile");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, "ping interrotto");
        } finally {
            if (p != null) p.destroy();
        }
    }

    private static String describe(IOException e) {
        if (e instanceof java.net.UnknownHostException) return "nome non risolto";
        if (e instanceof java.net.SocketTimeoutException) return "nessuna risposta";
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }
}
