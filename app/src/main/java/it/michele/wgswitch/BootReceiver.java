package it.michele.wgswitch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Riavvia il servizio dopo il boot o dopo un aggiornamento dell'app. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        // Il receiver è esportato (serve per il boot): accetta solo le azioni di sistema previste.
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        SwitchService.start(context, "avvio da " + action);
        Watchdog.schedule(context);
    }
}
