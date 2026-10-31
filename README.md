# WG Switch

Accende e spegne un tunnel dell'app WireGuard ufficiale in base a WiFi, Bluetooth e Android Auto.
Regola: se è attiva un'eccezione (rete WiFi o dispositivo Bluetooth scelti, oppure Android Auto) il tunnel
si spegne, altrimenti resta acceso. La riaccensione aspetta qualche secondo (configurabile).

## Scaricare l'app
- **F-Droid**: in F-Droid → Impostazioni → Repository → **+** aggiungi
  `https://raw.githubusercontent.com/thamichele/wgswitch/fdroid/repo?fingerprint=BEC44A856B5B49156173AD04BFEA643E44F712C8B4E9C3488EEC0C6A2B850E29`
- **APK firmato**: pagina [Releases](https://github.com/thamichele/wgswitch/releases).

## Installazione (Samsung S23/S26 Ultra, Xiaomi 15 Pro)
1. Installa da F-Droid, oppure copia l'APK sul telefono e aprilo consentendo l'installazione da origini sconosciute.
2. In WireGuard: Impostazioni > attiva "Consenti app di controllo remoto".
3. Apri WG Switch e concedi tutti i permessi nella sezione "Permessi" (posizione "Consenti sempre" compresa).
4. Batteria: escludi l'app dall'ottimizzazione. Samsung: aggiungila ad "App mai in sospensione".
   Xiaomi: attiva "Avvio automatico", Risparmio batteria su "Nessuna restrizione" e blocca l'app nei recenti
   (l'app mostra pulsanti dedicati quando gira su Xiaomi).
5. Scrivi il nome del tunnel, prova "Test ON" / "Test OFF", poi scegli reti WiFi e autoradio.
6. Disattiva il flow di Automate, altrimenti le due app si contendono il tunnel.

## Build
`build.sh` compila senza Gradle (aapt2, javac, dx, apksigner); GitHub Actions lo usa per verificare ogni push con
una chiave di prova. La chiave di firma vera e la sua password non sono nel repository: servono per
pubblicare gli aggiornamenti, che Android accetta solo se firmati con la stessa chiave.

## Pubblicare una nuova versione
1. Aggiorna `VERSION_NAME` in `build.sh` (nuova funzione: MINORE+1; correzione: CORREZIONE+1).
2. `KEYSTORE=... KS_PASS=... ./build.sh`
3. `FDROID_KEYSTORE=... FDROID_PASS=... ./fdroid.sh build/wgswitch-X.Y.Z.apk`, poi il push del ramo `fdroid`
   indicato dallo script, e una release `vX.Y.Z` con l'APK allegato.

## Verifica integrità
Impronta SHA-256 del certificato di firma (uguale per tutte le versioni):
`23:23:47:67:be:c7:03:55:0d:70:5d:3c:d8:19:d5:78:83:2a:2a:1c:7f:9c:eb:ef:15:78:41:25:da:c5:ca:a0`
L'impronta SHA-256 del file APK è in `wgswitch-<versione>.apk.sha256`.
