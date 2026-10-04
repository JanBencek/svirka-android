#!/usr/bin/env bash
# Runs inside the emulator-runner step: install APK, run the Maestro tour.
set -x
adb install -r "$APK"
adb shell settings put global window_animation_scale 0.5
export PATH="$PATH:$HOME/.maestro/bin"
mkdir -p shots && cd shots   # takeScreenshot writes into the working directory
maestro test --test-output-dir=debug -e SERVER="http://10.0.2.2:4533" -e PASSWORD="$ND_PASSWORD" ../.github/screenshots/flow.yaml || echo "::warning::flow did not finish — partial screenshots"
find . -name '*.png' | sort
exit 0
