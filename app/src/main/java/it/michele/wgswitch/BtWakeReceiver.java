package it.michele.wgswitch;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Rete di sicurezza: se il sistema ha chiuso il servizio, una connessione Bluetooth (es. salendo in auto)
 * lo fa ripartire. Non si fida dei dati del broadcast: il servizio rilegge da sé lo stato reale.
 */
public class BtWakeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) return;
        if (SwitchService.running) return;
        Prefs prefs = new Prefs(context);
        prefs.log("Servizio fermo alla connessione Bluetooth: riavvio");
        if (!SwitchService.start(context, "connessione Bluetooth")) {
            Health.alert(context, "Il servizio era chiuso e non è ripartito alla connessione Bluetooth. Apri l'app.");
        }
    }
}
