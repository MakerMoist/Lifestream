#!/usr/bin/env bash
# Everything that runs inside the emulator session: the smoke test, then the feature tests.
bash .github/smoke.sh
smoke=$?
bash .github/shots.sh
bash .github/tests.sh
tests=$?
exit $(( smoke | tests ))
