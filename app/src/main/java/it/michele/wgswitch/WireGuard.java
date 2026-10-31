package it.michele.wgswitch;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Comanda l'app WireGuard ufficiale tramite i suoi intent pubblici. */
final class WireGuard {
    static final String PACKAGE = "com.wireguard.android";
    static final String PERMISSION = "com.wireguard.android.permission.CONTROL_TUNNELS";
    private static final String ACTION_UP = "com.wireguard.android.action.SET_TUNNEL_UP";
    private static final String ACTION_DOWN = "com.wireguard.android.action.SET_TUNNEL_DOWN";

    private WireGuard() {}

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static boolean permissionGranted(Context c) {
        return c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    static void setTunnel(Context c, String tunnel, boolean up) {
        Intent i = new Intent(up ? ACTION_UP : ACTION_DOWN);
        i.setPackage(PACKAGE);
        i.putExtra("tunnel", tunnel);
        c.sendBroadcast(i);
    }
}
