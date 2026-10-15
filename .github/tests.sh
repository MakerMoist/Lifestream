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

./gradlew --no-daemon connectedDebugAndroidTest > "$RUNNER_TEMP/tests.log" 2>&1
status=$?
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

adb logcat -d 2>/dev/null | grep -E " LSTEST *: " | sed -E 's/.* LSTEST *: //' | sort -u | head -20 | sed 's/^/::notice::/' || true
exit $status
