#!/usr/bin/env bash
# Aggiorna il repository F-Droid personale (ramo "fdroid", servito da raw.githubusercontent.com).
# Uso: FDROID_KEYSTORE=... FDROID_PASS=... ./fdroid.sh build/wgswitch-X.Y.Z.apk
# Richiede fdroidserver (pip install fdroidserver), apksigner e git. La chiave del repo F-Droid
# non è nel repository: senza di essa i client F-Droid rifiutano l'indice aggiornato.
set -euo pipefail
cd "$(dirname "$0")"
: "${FDROID_KEYSTORE:?}" "${FDROID_PASS:?}" "${1:?APK da pubblicare}"
REPO_URL=https://raw.githubusercontent.com/thamichele/wgswitch/fdroid/repo
WORK=build/fdroid
rm -rf "$WORK"
# riparte dal ramo pubblicato, così le versioni precedenti restano disponibili
if git ls-remote --exit-code --heads origin fdroid > /dev/null; then
  git clone -q --depth 1 --branch fdroid "$(git remote get-url origin)" "$WORK"
else
  git init -q -b fdroid "$WORK"
fi
mkdir -p "$WORK/repo/icons" "$WORK/metadata"
cp "$@" "$WORK/repo/"
cp fdroid/metadata/*.yml "$WORK/metadata/"
# fdroid cerca repo_icon nella cartella di lavoro e lo copia in repo/icons/
cp app/src/main/res/mipmap-xxxhdpi/ic_launcher.png "$WORK/icon.png"
cat > "$WORK/config.yml" <<CONFIG
repo_url: $REPO_URL
repo_name: WG Switch
repo_description: Repository F-Droid personale di WG Switch
repo_icon: icon.png
archive_older: 3
keystore: $(realpath "$FDROID_KEYSTORE")
repo_keyalias: repo
keystorepass: $FDROID_PASS
keypass: $FDROID_PASS
CONFIG
chmod 600 "$WORK/config.yml"
(cd "$WORK" && fdroid update --pretty && rm -rf tmp srclibs icon.png config.yml)
echo "Pronto in $WORK: pubblica con"
echo "  cd $WORK && git add -A repo archive metadata && git commit -m 'F-Droid: aggiornamento' && git push origin fdroid"
