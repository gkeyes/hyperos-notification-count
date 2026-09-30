#!/usr/bin/env python3
"""Check the compiled release manifest and fixed signer, then prepare delivery receipts."""

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess

from verify_module import MODULE_ROOT, require, verify_module_apk


def run(*arguments):
    return subprocess.run(list(map(str, arguments)), check=True, capture_output=True,
                          text=True, timeout=60).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--build-tools", required=True, type=Path)
    parser.add_argument("--signed", action="store_true")
    arguments = parser.parse_args()
    info = verify_module_apk(arguments.apk)
    badging = run(arguments.build_tools / "aapt", "dump", "badging", arguments.apk)
    manifest = run(arguments.build_tools / "aapt", "dump", "xmltree", arguments.apk, "AndroidManifest.xml")
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    require(package is not None, "Cannot read APK package/version")
    source = (MODULE_ROOT / "app/build.gradle.kts").read_text()
    expected_version = re.search(r'versionName\s*=\s*"([^"]+)"', source).group(1)
    expected_code = re.search(r'versionCode\s*=\s*(\d+)', source).group(1)
    require(package.groups() == ("dev.hyperos.notificationcount", expected_code, expected_version),
            "APK package/version differs from source")
    require("sdkVersion:'37'" in badging and "targetSdkVersion:'37'" in badging, "Unexpected Android SDK levels")
    elements = set(re.findall(r"^\s*E:\s+([\w-]+)", manifest, re.M))
    require(elements == {"manifest", "uses-sdk", "application"}, "Unexpected Android component, permission or metadata")
    require("application-debuggable" not in badging, "Release APK must not be debuggable")
    for line in manifest.splitlines():
        if "android:debuggable(" in line:
            require(line.rstrip().endswith("0x0"), "Release debuggable flag must be false")
    print(f"Release manifest verified: {package.group(3)}, SDK 37, no UI/components/permissions")
    if not arguments.signed:
        print("Unsigned PR build checked; not an installable delivery artifact")
        return

    signature = run(arguments.build_tools / "apksigner", "verify", "--verbose", "--print-certs", arguments.apk)
    print(signature)
    # Newer apksigner versions include the signer's SDK range before its certificate fields.
    certificates = [value.lower() for value in re.findall(
        r"^Signer .+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$", signature, re.M)]
    expected_certificate = (MODULE_ROOT / "docs/signing-certificate.sha256").read_text().strip()
    require(certificates == [expected_certificate],
            f"APK signer differs from the fixed module certificate: actual={certificates}, expected={expected_certificate}")
    outputs = MODULE_ROOT / "outputs"
    outputs.mkdir(exist_ok=True)
    name = f"HyperOS-Notification-Count-{expected_version}-release.apk"
    shutil.copy2(arguments.apk, outputs / name)
    (outputs / "APK_INFO.txt").write_text(badging)
    (outputs / "SIGNATURE_INFO.txt").write_text(signature)
    (outputs / "SHA256SUMS.txt").write_text(f"{info['sha256']}  {name}\n")
    receipt = (
        f"version={expected_version}\nversionCode={expected_code}\n"
        f"commit={os.environ['GITHUB_SHA']}\n"
        f"run={os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}\n"
        f"apk_sha256={info['sha256']}\ncertificate_sha256={expected_certificate}\n"
        "scope=com.android.systemui\napi=102\ndebuggable=false\n"
    )
    (outputs / "BUILD_RECEIPT.txt").write_text(receipt)
    print(f"Fixed signer verified; prepared {name}, SHA-256 {info['sha256']}")


if __name__ == "__main__":
    main()
