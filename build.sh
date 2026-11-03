#!/usr/bin/env bash
# Build dell'APK senza Gradle: aapt2 + javac + dx + apksigner.
# Richiede: JDK 17+, aapt2, dalvik-exchange (dx), zipalign, apksigner (pacchetti Ubuntu)
# e due jar: ANDROID_CLASSES (classi API 35, es. Robolectric android-all 15)
# e ANDROID_RES (resources.arsc + AndroidManifest.xml di framework API 33).
set -euo pipefail
cd "$(dirname "$0")"
# La password della chiave non è nel repository: passala con KS_PASS. Senza keystore ne viene creato uno nuovo
# (va bene solo per build di prova: gli aggiornamenti richiedono sempre la chiave originale).
: "${ANDROID_CLASSES:?}" "${ANDROID_RES:?}" "${KEYSTORE:=keystore/wgswitch.jks}" "${KS_PASS:?}"
PKG=it.michele.wgswitch
# Versione MAGGIORE.MINORE.CORREZIONE: nuova funzione = MINORE+1, correzione = CORREZIONE+1,
# MAGGIORE solo su indicazione di Michele. versionCode derivato: M*10000 + m*100 + c.
VERSION_NAME=${VERSION_NAME:-2.1.0}
IFS=. read -r V_MAJ V_MIN V_FIX <<< "$VERSION_NAME"
VERSION_CODE=${VERSION_CODE:-$((V_MAJ * 10000 + V_MIN * 100 + ${V_FIX:-0}))}
SRC=app/src/main
OUT=build
rm -rf "$OUT"; mkdir -p "$OUT"/{res,gen,classes}

sed "s|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$PKG\" android:versionCode=\"$VERSION_CODE\" android:versionName=\"$VERSION_NAME\">|" \
  "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"

aapt2 compile --dir "$SRC/res" -o "$OUT/res/res.zip"
aapt2 link -o "$OUT/base.apk" -I "$ANDROID_RES" --manifest "$OUT/AndroidManifest.xml" \
  --min-sdk-version 33 --target-sdk-version 34 --java "$OUT/gen" "$OUT/res/res.zip"

javac -Xlint:-options --release 8 -encoding UTF-8 -cp "$ANDROID_CLASSES" -d "$OUT/classes" \
  $(find "$SRC/java" "$OUT/gen" -name '*.java')
if find "$OUT/classes" -name "*.class" -exec javap -c -p {} + | grep -q invokedynamic; then
  echo "Errore: lambda/invokedynamic non supportate da dx su Android" >&2; exit 1
fi
dalvik-exchange --dex --min-sdk-version=26 --output="$OUT/classes.dex" "$OUT/classes"

cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT" && zip -q unsigned.apk classes.dex)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias wgswitch \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=WG Switch"
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --v2-signing-enabled true \
  --out "$OUT/wgswitch-$VERSION_NAME.apk" "$OUT/aligned.apk"
apksigner verify "$OUT/wgswitch-$VERSION_NAME.apk"
echo "APK: $OUT/wgswitch-$VERSION_NAME.apk"
