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
    # Resolving the first stream downloads and runs YouTube's player script, which is slow on
    # an emulator; give it up to three minutes.
    for i in $(seq 1 18); do
      sleep 10
      state=$(adb shell dumpsys media_session | grep -m1 -oE "state=[A-Z_]+\([0-9]+\)|state=[0-9]+")
      if echo "$state" | grep -qE "PLAYING|state=3$"; then played=1; break; fi
    done
    echo "Media session: $state"
    if [ "$played" = 1 ]; then
      echo "::notice::A song is playing (stream resolved and decoding) after about $((i * 10)) s"
    else
      echo "::warning::Playback didn't reach PLAYING: $state"
      adb logcat -d 2>/dev/null | grep -E "ExoPlayerImplInternal|AndroidRuntime" | grep -vE "^\S+ \S+ +[0-9]+ +[0-9]+ [A-Z] [A-Za-z]+: +at " \
        | grep -v "^--" | sort -u -k6 | head -4 | cut -c1-400 | sed 's/^/::warning::/' || true
    fi
  else
    echo "::warning::Couldn't find the Play all button"
  fi
else
  echo "::warning::Couldn't find a soundtrack card. On screen: $(grep -oE 'text="[^"]+"' ui.xml 2>/dev/null | head -12 | tr '\n' ' ')"
fi

# Where an element is on screen: "x y top bottom" for the node whose text or description is $1.
find_node() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  python3 - "$1" <<'PY'
import re, sys
xml = open("ui.xml", encoding="utf-8", errors="ignore").read()
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    t = re.search(r'text="([^"]*)"', node)
    d = re.search(r'content-desc="([^"]*)"', node)
    if (t and t.group(1) == sys.argv[1]) or (d and d.group(1) == sys.argv[1]):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2, y1, y2)
            break
PY
}

# The song menu opens where it was opened from: at a song's ⋮ button, and where a row is held.
dots=$(find_node "Song options")
if [ -n "$dots" ]; then
  set -- $dots; dx=$1; dy=$2
  adb shell input tap "$dx" "$dy"; sleep 2
  item=$(find_node "Add to playlist")
  if [ -n "$item" ]; then
    set -- $item; iy=$2
    dist=$(( iy > dy ? iy - dy : dy - iy ))
    echo "::notice::Song menu from ⋮ at y=$dy shows 'Add to playlist' at y=$iy (${dist}px away; screen $(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1))"
    [ "$dist" -gt 1200 ] && echo "::warning::Song menu opened far from the ⋮ button"
    adb shell input keyevent KEYCODE_BACK; sleep 2
    [ -n "$(find_node "Add to playlist")" ] && echo "::warning::Back didn't close the song menu"
  else
    echo "::warning::The ⋮ button didn't open the song menu"
  fi
  # Hold a row a little to the left of its ⋮ button.
  hx=$(( dx / 2 ))
  adb shell input swipe "$hx" "$dy" "$hx" "$dy" 900; sleep 2
  item=$(find_node "Play next")
  [ -z "$item" ] && item=$(find_node "Add to playlist")
  if [ -n "$item" ]; then
    set -- $item
    echo "::notice::Holding a song at y=$dy opened its menu (first item at y=$2)"
    adb shell input keyevent KEYCODE_BACK; sleep 1
  else
    echo "::warning::Holding a song didn't open its menu"
  fi
else
  echo "::warning::No song rows on screen to try the song menu"
fi

# Library: swipe from Playlists to History and back with the Library tab.
if tap_text "Library"; then
  sleep 3
  adb shell input swipe 900 1300 150 1300 300; sleep 2
  if [ -n "$(find_node "Clear")" ] || [ -n "$(find_node "Nothing played yet")" ]; then
    echo "::notice::Library: swiping left opens History"
  else
    echo "::warning::Library: swiping left didn't open History"
  fi
  tap_text "Library"; sleep 2
  if [ -n "$(find_node "New playlist")" ]; then
    echo "::notice::Library: tapping the Library tab returns to Playlists"
  else
    echo "::warning::Library: the Library tab didn't return to Playlists"
  fi
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
