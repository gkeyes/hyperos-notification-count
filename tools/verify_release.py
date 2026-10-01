#!/usr/bin/env python3
"""Check the compiled release manifest and fixed signer, then prepare delivery receipts."""

import argparse
from collections import Counter
import os
from pathlib import Path
import re
import shutil
import subprocess

from verify_module import MODULE_ROOT, require, verify_module_apk


def run(*arguments):
    return subprocess.run(list(map(str, arguments)), check=True, capture_output=True,
                          text=True, timeout=60).stdout


def manifest_nodes(xmltree):
    """Read aapt's element nesting and raw string/boolean attributes."""
    roots, stack, nodes = [], [], []
    for line in xmltree.splitlines():
        element = re.match(r"^(\s*)E:\s+([\w-]+)", line)
        if element:
            depth = len(element[1])
            while stack and stack[-1][0] >= depth:
                stack.pop()
            node = {"name": element[2], "attributes": {}, "children": []}
            (stack[-1][1]["children"] if stack else roots).append(node)
            stack.append((depth, node))
            nodes.append(node)
        attribute = re.match(r"^\s*A:\s+([\w:.-]+)(?:\([^)]*\))?=(.*)$", line)
        if attribute and stack:
            value = attribute[2].strip()
            raw = re.search(r'\(Raw: "([^"]*)"\)', value)
            if raw:
                value = raw[1]
            elif value.startswith('"'):
                value = value.split('"', 2)[1]
            stack[-1][1]["attributes"][attribute[1]] = value
    require(len(roots) == 1 and roots[0]["name"] == "manifest", "Malformed compiled manifest tree")
    return nodes


def verify_settings_manifest(xmltree, badging):
    nodes = manifest_nodes(xmltree)
    require(Counter(node["name"] for node in nodes) == Counter({
        "manifest": 1, "uses-sdk": 1, "application": 1, "activity": 1,
        "intent-filter": 1, "action": 1, "category": 1, "provider": 1,
    }), "Only the module settings activity and official libxposed provider may be packaged; no permissions")
    by_name = {node["name"]: node for node in nodes}
    application = by_name["application"]
    require(application["attributes"].get("android:name") ==
            "dev.hyperos.notificationcount.settings.ModuleApplication", "Unexpected module application")
    require({child["name"] for child in application["children"]} == {"activity", "provider"},
            "Unexpected application child")
    activity = by_name["activity"]
    require(activity["attributes"].get("android:name") ==
            "dev.hyperos.notificationcount.settings.SettingsActivity", "Unexpected settings activity")
    require(activity["attributes"].get("android:exported", "").endswith("0xffffffff"),
            "Module manager must be able to open settings")
    require([child["name"] for child in activity["children"]] == ["intent-filter"],
            "Settings must have one manager intent filter")
    require(Counter(child["name"] for child in by_name["intent-filter"]["children"]) ==
            Counter({"action": 1, "category": 1}), "Unexpected settings intent filter")
    require(by_name["action"]["attributes"].get("android:name") == "android.intent.action.MAIN",
            "Unexpected settings action")
    require(by_name["category"]["attributes"].get("android:name") ==
            "de.robv.android.xposed.category.MODULE_SETTINGS", "Unexpected settings category")
    provider = by_name["provider"]
    require(provider["attributes"].get("android:name") == "io.github.libxposed.service.XposedProvider",
            "Unexpected provider")
    require(provider["attributes"].get("android:authorities") ==
            "dev.hyperos.notificationcount.XposedService", "Unexpected framework provider authority")
    require(provider["attributes"].get("android:exported", "").endswith("0xffffffff"),
            "Framework must be able to deliver its service binder")
    require(not provider["children"], "Unexpected provider configuration")
    require("android.intent.category.LAUNCHER" not in xmltree and "launchable-activity:" not in badging,
            "The module must not expose a desktop launcher icon")


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
    verify_settings_manifest(manifest, badging)
    require("application-debuggable" not in badging, "Release APK must not be debuggable")
    for line in manifest.splitlines():
        if "android:debuggable(" in line:
            require(line.rstrip().endswith("0x0"), "Release debuggable flag must be false")
    print(f"Release manifest verified: {package.group(3)}, SDK 37, manager-only settings, no launcher/permissions")
    if not arguments.signed:
        print("Unsigned PR build checked; not an installable delivery artifact")
        return

    signature = run(arguments.build_tools / "apksigner", "verify", "--verbose", "--print-certs", arguments.apk)
    print(signature)
    # apksigner 37 labels verified scheme certificates as "V2 Signer:", etc.
    # Several schemes may report the same signing identity; also require one APK signer.
    certificates = {value.lower() for value in re.findall(
        r"^(?:V[0-9.]+ )?Signer[^\n]*certificate SHA-256 digest: ([0-9a-fA-F]{64})[ \t]*$",
        signature, re.M)}
    expected_certificate = (MODULE_ROOT / "docs/signing-certificate.sha256").read_text().strip()
    require(re.search(r"^Number of signers: 1$", signature, re.M) is not None,
            "APK must have exactly one signer")
    require(certificates == {expected_certificate},
            f"APK signer differs from the fixed module certificate: actual={sorted(certificates)}, expected={expected_certificate}")
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
        "settings=module-manager-only\nlauncher=false\nfilters=15\n"
    )
    (outputs / "BUILD_RECEIPT.txt").write_text(receipt)
    print(f"Fixed signer verified; prepared {name}, SHA-256 {info['sha256']}")


if __name__ == "__main__":
    main()
