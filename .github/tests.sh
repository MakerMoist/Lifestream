#!/usr/bin/env bash
# Run by .github/emulator.sh after the smoke test: the on-device feature tests in
# app/src/androidTest (saved songs, volume levelling, search, translation, playlists, queue
# order, Android Auto, the soundtrack index, update checks). Results become annotations.
set -u
PKG=com.lifestream.player
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb uninstall "$PKG.test" >/dev/null 2>&1 || true
# A large log buffer, so the tests' own notes aren't pushed out by the player's logging.
adb logcat -G 16M >/dev/null 2>&1 || true
adb logcat -c

# The tests' own notes are collected as they're written (nothing can scroll them away).
adb logcat -v brief -s LSTEST:I > "$RUNNER_TEMP/notes.txt" 2>/dev/null &
notes_pid=$!

./gradlew --no-daemon connectedDebugAndroidTest > "$RUNNER_TEMP/tests.log" 2>&1
status=$?
sleep 2
kill "$notes_pid" 2>/dev/null || true
if [ "$status" != 0 ]; then
  grep -E '^e: |error:|FAILED|Execution failed' "$RUNNER_TEMP/tests.log" | head -20 | cut -c1-400 | sed 's/^/::error::/' || true
fi

python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
passed = failed = skipped = 0
for path in glob.glob("app/build/outputs/androidTest-results/**/TEST-*.xml", recursive=True):
    for case in ET.parse(path).getroot().iter("testcase"):
        name = case.get("classname", "").split(".")[-1] + "." + case.get("name", "")
        bad = case.find("failure")
        if bad is None:
            bad = case.find("error")
        if bad is not None:
            failed += 1
            text = (bad.get("message") or bad.text or "").strip().replace("%", "%25").replace("\r", "").replace("\n", "%0A")
            print(f"::error title={name}::{text[:600]}")
        elif case.find("skipped") is not None:
            skipped += 1
            print(f"::warning title=Skipped::{name}")
        else:
            passed += 1
print(f"::notice title=Feature tests::{passed} passed, {failed} failed, {skipped} skipped")
PY

# One annotation for all notes (GitHub shows only ten notices per step).
notes=$(grep -E "LSTEST" "$RUNNER_TEMP/notes.txt" | sed -E 's/^[^:]*: //' | sort -u | head -40 | sed 's/%/%25/g' | awk 'BEGIN{ORS="%0A"} {print}')
[ -n "$notes" ] && echo "::notice title=Feature notes::$notes"
exit $status
