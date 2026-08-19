#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app/src/main"
OUT="$ROOT/build"

export JAVA_HOME="${JAVA_HOME:-${JAVA_HOME_21:-/usr/lib/jvm/java-21-openjdk-amd64}}"
if [ ! -x "${JAVA_HOME}/bin/javac" ] && [ -n "${JAVA_HOME_17:-}" ]; then
  export JAVA_HOME="$JAVA_HOME_17"
fi
JAVAC_HOME="${JAVAC_HOME:-$JAVA_HOME}"
if [ ! -x "$JAVAC_HOME/bin/javac" ]; then
  JAVAC_HOME="$JAVA_HOME"
fi

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$ROOT/../android-sdk}}"
BT="$ANDROID_HOME/build-tools/34.0.0"
AJ="$ANDROID_HOME/platforms/android-34/android.jar"
PATH="$JAVA_HOME/bin:$JAVAC_HOME/bin:$BT:$PATH"

echo "==> FNF Porter For Mobile"
echo "    JAVA_HOME=$JAVA_HOME"
echo "    ANDROID_HOME=$ANDROID_HOME"

test -f "$AJ" || { echo "android.jar missing at $AJ"; exit 1; }
test -x "$BT/aapt2" || { echo "aapt2 missing"; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/reszip" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> Compile resources"
"$BT/aapt2" compile --dir "$APP/res" -o "$OUT/reszip"
mapfile -t FLATS < <(find "$OUT" -name '*.flat')
echo "    flats: ${#FLATS[@]}"
if [ ${#FLATS[@]} -eq 0 ]; then
  echo "No compiled resources"
  exit 1
fi

echo "==> Link APK + generate R.java"
"$BT/aapt2" link \
  -o "$OUT/unsigned-unaligned.apk" \
  -I "$AJ" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --custom-package com.sametgkte.fnfporter \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code 3 \
  --version-name "1.0.0" \
  --auto-add-overlay \
  -A "$APP/assets" \
  "${FLATS[@]}"

echo "==> javac"
mapfile -t JAVAS < <(find "$APP/java" "$OUT/gen" -name '*.java')
"$JAVAC_HOME/bin/javac" -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$AJ" \
  -classpath "$AJ" \
  -d "$OUT/classes" \
  "${JAVAS[@]}"

echo "==> d8"
( cd "$OUT/classes" && jar cf "$OUT/classes.jar" . )
"$BT/d8" --min-api 24 --lib "$AJ" --output "$OUT/dex" "$OUT/classes.jar"

echo "==> Inject classes.dex"
cp "$OUT/unsigned-unaligned.apk" "$OUT/withdex.apk"
( cd "$OUT/dex" && zip -q "$OUT/withdex.apk" classes.dex )

echo "==> zipalign"
"$BT/zipalign" -p -f 4 "$OUT/withdex.apk" "$OUT/aligned.apk"

echo "==> sign"
KS="$ROOT/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=FNF Porter For Mobile, OU=Android, O=SametGkTe, L=Ankara, S=Ankara, C=TR"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/FNF-Porter-For-Mobile.apk" "$OUT/aligned.apk"

"$BT/apksigner" verify "$OUT/FNF-Porter-For-Mobile.apk"
cp "$OUT/FNF-Porter-For-Mobile.apk" "$ROOT/FNF-Porter-For-Mobile.apk"
if [ -d /home/user ]; then
  cp "$OUT/FNF-Porter-For-Mobile.apk" /home/user/FNF-Porter-For-Mobile.apk
  rm -f /home/user/FNF-Porter-By-GkTe.apk /home/user/FNF-Porter-Android.apk
fi
ls -lh "$OUT/FNF-Porter-For-Mobile.apk"
echo "==> DONE"
