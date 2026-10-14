#!/usr/bin/env bash
# Run by .github/workflows/smoke.yml inside the emulator session.
set -u
PKG=com.lifestream.player
APK=app/build/outputs/apk/release/app-release.apk

adb install -r "$APK" || { echo "::error::Install failed"; exit 1; }
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c

adb shell am start -W -n "$PKG/.MainActivity"
# Long enough for start-up, the player service connection and the background soundtrack index
# (which runs the YouTube extractor) to get going.
sleep 60

fail=0
if ! adb shell pidof "$PKG" >/dev/null; then
  echo "::error::LifeStream is not running 60 s after launch"
  fail=1
fi

adb logcat -d -b crash > crash.txt 2>/dev/null || true
if grep -q "$PKG" crash.txt; then
  echo "::error::LifeStream crashed"
  grep -E "FATAL|Exception|Error|Caused by|^\s+at " crash.txt | head -30 | sed 's/^/::error::/'
  fail=1
fi

# Shrinking problems show up as missing classes or methods even when they're caught.
adb logcat -d 2>/dev/null | grep -E "ClassNotFoundException|NoClassDefFoundError|NoSuchMethodError|NoSuchFieldError" \
  | grep -v "^--" | head -20 | sed 's/^/::warning::/' || true

if [ "$fail" = 0 ]; then
  echo "::notice::LifeStream launched and kept running without crashing"
fi
exit $fail
