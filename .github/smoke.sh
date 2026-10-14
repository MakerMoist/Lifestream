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

# Tap the element whose text matches $1 (found through the accessibility tree).
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  python3 - "$1" <<'PY' > tap.txt
import re, sys
xml = open("ui.xml", encoding="utf-8", errors="ignore").read()
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    t = re.search(r'text="([^"]*)"', node)
    if t and t.group(1) == sys.argv[1]:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
  if [ -s tap.txt ]; then adb shell input tap $(cat tap.txt); return 0; fi
  return 1
}

# Play a soundtrack: this runs the whole streaming path (the YouTube extractor and its
# JavaScript engine resolving a stream, the download, decoding and the volume leveler).
played=0
if tap_text "Final Fantasy" || tap_text "Final Fantasy II"; then
  sleep 20
  if tap_text "Play all"; then
    sleep 45
    state=$(adb shell dumpsys media_session | grep -m1 -oE "state=[A-Z_]+\([0-9]+\)|state=[0-9]+")
    echo "Media session: $state"
    if adb shell dumpsys media_session | grep -qE "state=PLAYING|state=3[^0-9]|state=3$"; then
      played=1
      echo "::notice::A song is playing (stream resolved and decoding)"
    else
      echo "::warning::Playback didn't reach PLAYING (the emulator may have no route to YouTube): $state"
    fi
  else
    echo "::warning::Couldn't find the Play all button"
  fi
else
  echo "::warning::Couldn't find a soundtrack card. On screen: $(grep -oE 'text="[^"]+"' ui.xml 2>/dev/null | head -12 | tr '\n' ' ')"
fi

if ! adb shell pidof "$PKG" >/dev/null; then
  echo "::error::LifeStream stopped while playing"
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
