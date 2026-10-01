#!/usr/bin/env python3
"""Require the release APK's Android 17 runtime checks before delivering it."""

from pathlib import Path
import xml.etree.ElementTree as ET

from verify_module import MODULE_ROOT, digest, require


def main():
    results = MODULE_ROOT / "app/build/outputs/androidTest-results/connected"
    expected = {
        "allFiltersPersistReopenAndResetInTheShrunkActivity",
        "connectionLossDisablesFiltersAndManagerHasNoLauncher",
        "everyCountVectorSurvivesShrinkingAndDrawsInBothTints",
    }
    actual = set()
    for path in results.rglob("TEST-*.xml"):
        root = ET.parse(path).getroot()
        require(all(int(root.get(key, "0")) == 0 for key in ("failures", "errors", "skipped")),
                f"Runtime failures/errors/skips in {path}")
        for case in root.iter("testcase"):
            require(case.get("classname") == "dev.hyperos.notificationcount.settings.ReleaseSmokeTest",
                    "Unexpected runtime test class")
            require(case.get("name") not in actual, "Duplicate runtime result")
            require(not any(case.find(key) is not None for key in ("failure", "error", "skipped")),
                    "Runtime test did not pass")
            actual.add(case.get("name"))
    require(actual == expected, f"Incomplete runtime checks: {sorted(actual)}")
    outputs = MODULE_ROOT / "outputs"
    checksum, name = (outputs / "SHA256SUMS.txt").read_text().strip().split()
    release = MODULE_ROOT / "app/build/outputs/apk/release/app-release.apk"
    require(digest(release) == digest(outputs / name) == checksum,
            "Runtime-tested release APK differs from the delivery artifact")
    with (outputs / "BUILD_RECEIPT.txt").open("a") as receipt:
        receipt.write("release_runtime=android-37-emulator\nrelease_smoke_tests=3\n")
        receipt.write("phone_systemui_runtime=not-tested\n")
    print("3 Android 17 runtime checks passed on the identical signed release APK")


if __name__ == "__main__":
    main()
