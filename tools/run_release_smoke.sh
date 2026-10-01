#!/usr/bin/env bash
# Runs only in GitHub's emulator; capture system failures as well as test results.
set -euo pipefail
module_diag="$PWD/app/build/outputs/androidTest-results/environment"
mkdir -p "$module_diag"
adb logcat -v threadtime > "$module_diag/logcat.txt" 2>&1 &
module_logcat_pid=$!
trap 'kill "$module_logcat_pid" 2>/dev/null || true' EXIT
adb shell getprop > "$module_diag/device-properties.txt"
adb shell cat /proc/meminfo > "$module_diag/memory.txt"
adb shell df -h /data > "$module_diag/storage.txt"

# boot_completed can precede the package service's stable first-boot state.
module_stable=0
module_previous_pid=""
for module_attempt in {1..30}; do
    module_pid=$(adb shell pidof system_server 2>/dev/null | tr -d '\r' || true)
    if [[ -n "$module_pid" && "$module_pid" == "$module_previous_pid" ]] &&
        adb shell cmd package list packages --user 0 > "$module_diag/packages.txt" 2>&1; then
        module_stable=$((module_stable + 1))
        if [[ "$module_stable" -ge 5 ]]; then break; fi
    else
        module_stable=0
    fi
    module_previous_pid="$module_pid"
    sleep 2
done
if [[ "$module_stable" -lt 5 ]]; then
    echo 'Android package service did not stabilize; see emulator diagnostics.'
    exit 1
fi
./gradlew :app:connectedReleaseAndroidTest --console=plain
