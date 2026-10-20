#!/usr/bin/env bash
# Run by .github/emulator.sh after the smoke test: screenshots of the screens a release changes,
# saved to shots/ (uploaded with the run), plus a few checks read from the screen.
set -u
PKG=com.lifestream.player
mkdir -p shots
shot() { adb exec-out screencap -p > "shots/$1.png"; }

dump() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
}
# "x y top bottom" of the first node whose text or description is $1 ($2 = "contains" to match part).
node() {
  dump
  python3 - "$1" "${2:-}" <<'PY'
import re, sys
xml = open("ui.xml", encoding="utf-8", errors="ignore").read()
want, mode = sys.argv[1], sys.argv[2]
hits = []
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    if 'package="com.lifestream.player"' not in n:
        continue
    vals = [x.group(1) for x in re.finditer(r'(?:text|content-desc)="([^"]*)"', n)]
    if any((want in v) if mode == "contains" else (v == want) for v in vals):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            hits.append(((x1 + x2) // 2, (y1 + y2) // 2, y1, y2))
if hits:
    print(*(max(hits, key=lambda h: h[1]) if mode == "lowest" else hits[0]))
PY
}
tap() { local p; p=$(node "$1" "${2:-}"); [ -n "$p" ] || return 1; set -- $p; adb shell input tap "$1" "$2"; }
texts() { dump; grep -oE 'text="[^"]+"' ui.xml | sed 's/text="//;s/"$//'; }
size=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); W=${size%x*}; H=${size#*x}

# The player notification (a song is playing from the smoke test).
adb shell cmd statusbar expand-notifications; sleep 3; shot notification
adb shell cmd statusbar collapse; sleep 1

# Home, with the Recommended row (there's history now): scroll the row and check nothing below moves.
adb shell am start -n "$PKG/.MainActivity" >/dev/null; sleep 2
tap "Home" lowest ; sleep 2; tap "Home" lowest; sleep 4
shot home
rec=$(node "RECOMMENDED")
if [ -n "$rec" ]; then
  set -- $rec; ry=$4
  below() { { node "Main Series"; node "MAIN SERIES"; } | head -1 | awk '{print $3}'; }
  b0=$(below)
  rowy=$(( ry + 220 ))
  moved=""
  for k in 1 2 3 4 5 6; do
    adb shell input swipe $(( W * 85 / 100 )) $rowy $(( W * 20 / 100 )) $rowy 250
    sleep 1
    b=$(below); [ "$b" != "$b0" ] && moved="$moved $b"
  done
  shot home-recommended-scrolled
  if [ -z "$moved" ]; then
    echo "::notice::Recommended row scrolled 6 times; the section below stayed at y=$b0"
  else
    echo "::warning::Section below the Recommended row moved while scrolling: $b0 →$moved"
  fi
else
  echo "::warning::No Recommended row on Home"
fi

# Search: DS and PSP songs, named after the original's.
search() {
  tap "Search" lowest ; sleep 2
  adb shell input text "$1"; sleep 5
  adb shell input keyevent KEYCODE_BACK; sleep 1
}
# The soundtrack index has to include the new sources first; give it time.
for i in $(seq 1 30); do
  tap "Search" lowest; sleep 1
  adb shell input text "the%sprelude"; sleep 4
  if texts | grep -q "(DS)"; then break; fi
  adb shell input keyevent KEYCODE_BACK
  adb shell am start -n "$PKG/.MainActivity" >/dev/null; tap "Home" lowest; sleep 10
done
shot search-prelude
echo "::notice::Search 'prelude': $(texts | grep -iE 'prelude' | head -8 | tr '\n' '|')"
adb shell input keyevent KEYCODE_BACK; sleep 1
tap "Search" lowest; sleep 1
# Clear the field and search for a Crisis Core song.
adb shell input text "combat"; sleep 4
shot search-combat
echo "::notice::Search 'combat': $(texts | grep -iE 'combat' | head -8 | tr '\n' '|')"
adb shell input keyevent KEYCODE_BACK

# Final Fantasy III's page, scrolled to its DS songs.
adb shell am start -n "$PKG/.MainActivity" >/dev/null; sleep 1
tap "Home" lowest; sleep 2; tap "Home" lowest; sleep 3
for k in 1 2; do tap "Final Fantasy III" && break; adb shell input swipe $(( W / 2 )) $(( H * 62 / 100 )) $(( W / 2 )) $(( H * 32 / 100 )) 300; sleep 1; done
sleep 6
for k in $(seq 1 20); do adb shell input swipe $(( W / 2 )) $(( H * 62 / 100 )) $(( W / 2 )) $(( H * 22 / 100 )) 200; sleep 0.5; done
sleep 2; shot ff3-ds
echo "::notice::FF III after scrolling: $(texts | head -40 | tr '\n' '|')"
adb shell input keyevent KEYCODE_BACK; sleep 2

# Lost Odyssey's page (its songs' art).
for k in $(seq 1 10); do tap "Lost Odyssey" && break; adb shell input swipe $(( W / 2 )) $(( H * 62 / 100 )) $(( W / 2 )) $(( H * 22 / 100 )) 300; sleep 1; done
sleep 8; shot lost-odyssey
adb shell input keyevent KEYCODE_BACK; sleep 1

# The Legend of Dragoon: its card on Home, then its page.
shot home-other-games
for k in $(seq 1 10); do tap "The Legend of Dragoon" && break; adb shell input swipe $(( W / 2 )) $(( H * 62 / 100 )) $(( W / 2 )) $(( H * 22 / 100 )) 300; sleep 1; done
sleep 8; shot legend-of-dragoon
echo "::notice::The Legend of Dragoon: $(texts | head -24 | tr '\n' '|')"
adb shell input keyevent KEYCODE_BACK; sleep 1
exit 0
