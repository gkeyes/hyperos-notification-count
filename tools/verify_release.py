#!/usr/bin/env python3
"""Check the compiled release manifest and fixed signer, then prepare delivery receipts."""

import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile

from verify_module import ApkDex, MODULE_ROOT, require, verify_module_apk


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
        "uses-library": 2,
    }), "Only the module settings activity and official libxposed provider may be packaged; no permissions")
    by_name = {node["name"]: node for node in nodes}
    application = by_name["application"]
    require(application["attributes"].get("android:name") ==
            "dev.hyperos.notificationcount.settings.ModuleApplication", "Unexpected module application")
    require({child["name"] for child in application["children"]} == {"activity", "provider", "uses-library"},
            "Unexpected application child")
    libraries = [node for node in nodes if node["name"] == "uses-library"]
    require({node["attributes"].get("android:name") for node in libraries} ==
            {"androidx.window.extensions", "androidx.window.sidecar"}, "Unexpected optional window library")
    for library in libraries:
        require(library["attributes"].get("android:required", "").endswith("0x0"),
                "AndroidX window extensions must remain optional")
        require(not library["children"], "Unexpected window library configuration")
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


MIUIX_COMPONENTS = {
    "basic/CardKt": "Card",
    "basic/SmallTitleKt": "SmallTitle",
    "basic/TopAppBarKt": "TopAppBar",
    "basic/ButtonKt": "TextButton",
    "preference/SwitchPreferenceKt": "SwitchPreference",
    "preference/OverlayDropdownPreferenceKt": "OverlayDropdownPreference",
    "theme/MiuixThemeKt": "MiuixTheme",
}
TEST_PACKAGES = (
    "org.junit.", "junit.", "org.robolectric.", "androidx.compose.ui.test.",
    "androidx.test.", "org.hamcrest.", "kotlin.test.",
)


def dex_descriptor(name):
    return "L" + name.replace(".", "/") + ";"


def read_r8_mapping(path):
    """Keep inline frames with their residual owner/method; class names alone are insufficient."""
    require(path.is_file(), f"Missing R8 mapping file: {path}")
    text = path.read_text()
    require(re.search(r"^# compiler: R8\s*$", text, re.M), "Mapping must be produced by R8")
    classes, groups = {}, []
    owner, residual_owner, group = None, None, None
    method_pattern = re.compile(
        r"^\s+(?:(\d+):(\d+):)?(\S+) ([^\s(]+)\(([^)]*)\)"
        r"(?::\d+(?::\d+)?)? -> (\S+)$")
    for line in text.splitlines():
        declaration = re.fullmatch(r"(\S+) -> (\S+):", line)
        if declaration:
            owner, residual_owner = declaration.groups()
            require(owner not in classes or classes[owner] == residual_owner,
                    f"Conflicting R8 class mapping: {owner}")
            classes[owner] = residual_owner
            group = None
            continue
        method = method_pattern.fullmatch(line)
        if method:
            require(owner is not None, "R8 method mapping has no class owner")
            start, end, returns, qualified_name, parameters, residual_name = method.groups()
            original_owner, separator, name = qualified_name.rpartition(".")
            if not separator:
                original_owner, name = owner, qualified_name
            interval = (start, end)
            # Equal residual intervals describe an inline stack. No-position overloads
            # are separate methods, even when their obfuscated names are identical.
            if (group is None or start is None or group["interval"] != interval
                    or group["name"] != residual_name):
                group = {"owner": residual_owner, "name": residual_name,
                         "interval": interval, "frames": [], "signatures": set()}
                groups.append(group)
            group["frames"].append({"owner": original_owner, "name": name,
                                    "parameters": tuple(parameters.split(",")) if parameters else (),
                                    "returns": returns})
            continue
        if line.lstrip().startswith("# {"):
            metadata = json.loads(line.lstrip()[2:])
            if metadata.get("id") == "com.android.tools.r8.residualsignature" and group is not None:
                group["signatures"].add(metadata["signature"])
        elif " -> " in line:
            # A field mapping must not inherit the preceding method's metadata.
            group = None
    require(classes, "R8 mapping contains no class declarations")
    return classes, groups


def java_type_descriptor(name, classes):
    dimensions = 0
    while name.endswith("[]"):
        dimensions += 1
        name = name[:-2]
    primitive = {"void": "V", "boolean": "Z", "byte": "B", "short": "S", "char": "C",
                 "int": "I", "long": "J", "float": "F", "double": "D"}
    return "[" * dimensions + (primitive[name] if name in primitive
                                else dex_descriptor(classes.get(name, name)))


def mapping_method_witnesses(dex, classes, groups):
    """Only mapping frames backed by an actual DEX method body count as provenance."""
    witnesses = []
    for group in groups:
        owner = dex_descriptor(group["owner"])
        if owner not in dex.classes:
            continue
        _, methods = dex.classes[owner].members(owner)
        signatures = group["signatures"]
        if not signatures:
            # The last frame is the residual caller, not the inlined callee.
            caller = group["frames"][-1]
            signatures = {"(" + "".join(java_type_descriptor(p, classes)
                                         for p in caller["parameters"]) + ")"
                          + java_type_descriptor(caller["returns"], classes)}
        matches = [method for method in methods if method.name == group["name"]
                   and method.code_offset and "(" + "".join(method.parameters) + ")"
                   + method.returns in signatures]
        for method in matches:
            for frame in group["frames"]:
                witnesses.append((frame["owner"], frame["name"], owner, method))
    return witnesses


def verify_mapped_ui(dex, mapping, activity):
    classes, groups = read_r8_mapping(mapping)
    witnesses = mapping_method_witnesses(dex, classes, groups)
    for original, residual in classes.items():
        require(not (original.startswith(TEST_PACKAGES) and dex_descriptor(residual) in dex.classes),
                f"Test library source survived R8: {original} -> {residual}")
    require(not any(original.startswith(TEST_PACKAGES) for original, _, _, _ in witnesses),
            "Inlined test library source is packaged in the release")

    chain, current = set(), activity
    while current in dex.classes and current not in chain:
        chain.add(current)
        current = dex.classes[current].classes[current]["parent"]
    component_activity = "androidx.activity.ComponentActivity"
    require(dex_descriptor(component_activity) in chain or any(
        original == component_activity and owner in chain
        for original, _, owner, _ in witnesses),
        "Settings activity has no surviving/merged Compose ComponentActivity implementation")
    for component, function in MIUIX_COMPONENTS.items():
        original = "top.yukonga.miuix.kmp." + component.replace("/", ".")
        # Dp/Color value classes add a JVM name suffix; default bridges are also valid.
        function_pattern = re.compile(re.escape(function) + r"(?:-[A-Za-z0-9_-]+)?(?:\$default)?")
        require(any(source == original and function_pattern.fullmatch(name)
                    for source, name, _, _ in witnesses),
                f"Missing DEX method provenance for official Miuix component: {component}.{function}")


def vector_xml_nodes(xmltree):
    roots, stack = [], []
    for line in xmltree.splitlines():
        element = re.match(r"^(\s*)E:\s+([\w-]+)", line)
        if element:
            depth = len(element[1])
            while stack and stack[-1][0] >= depth:
                stack.pop()
            node = {"name": element[2], "attributes": {}, "children": []}
            (stack[-1][1]["children"] if stack else roots).append(node)
            stack.append((depth, node))
        attribute = re.match(r"^\s*A:\s+([\w:.-]+)(?:\([^)]*\))?=(.*)$", line)
        if attribute and stack:
            value = attribute[2].strip()
            raw = re.search(r'\(Raw: "([^"]*)"\)', value)
            if raw:
                value = raw[1]
            elif value.startswith('"'):
                value = value.split('"', 2)[1]
            require(attribute[1] not in stack[-1][1]["attributes"], "Duplicate compiled vector attribute")
            stack[-1][1]["attributes"][attribute[1]] = value
    require(len(roots) == 1 and roots[0]["name"] == "vector", "Resource is not a compiled Android vector")
    return roots[0]


def aapt_value_bits(value):
    match = re.fullmatch(r"(?:\(type 0x([0-9a-fA-F]+)\)\s*)?(0x[0-9a-fA-F]+)", value)
    require(match is not None, f"Unsupported compiled vector value: {value}")
    return int(match[1], 16) if match[1] else None, int(match[2], 16)


def verify_count_vector(xmltree, source):
    vector = vector_xml_nodes(xmltree)
    attributes = vector["attributes"]
    require(set(attributes) == {"android:width", "android:height", "android:viewportWidth",
                                "android:viewportHeight"}, "Unexpected compiled vector attributes")
    for key in ("android:width", "android:height"):
        kind, bits = aapt_value_bits(attributes[key])
        mantissa = bits & 0xFFFFFF00
        if mantissa & 0x80000000:
            mantissa -= 1 << 32
        dimension = mantissa * (1 / 256, 1 / 32768, 1 / 8388608, 1 / 2147483648)[(bits >> 4) & 3]
        require(kind == 5 and bits & 15 == 1 and dimension == 16,
                f"Compiled count vector must be 16dp: {key}")
    for key in ("android:viewportWidth", "android:viewportHeight"):
        kind, bits = aapt_value_bits(attributes[key])
        require(kind == 4 and struct.unpack("!f", struct.pack("!I", bits))[0] == 16,
                f"Compiled count vector must have a 16x16 viewport: {key}")
    expected = ET.parse(source).getroot()
    paths = vector["children"]
    require(len(paths) == len(expected) == 2, "Count vector must retain exactly two paths")
    android = "{http://schemas.android.com/apk/res/android}"
    for actual, original in zip(paths, expected):
        require(actual["name"] == original.tag == "path" and not actual["children"],
                "Unexpected compiled vector structure")
        require(set(actual["attributes"]) == {"android:fillColor", "android:fillType", "android:pathData"},
                "Unexpected compiled count path attributes")
        color_type, color = aapt_value_bits(actual["attributes"]["android:fillColor"])
        _, fill_type = aapt_value_bits(actual["attributes"]["android:fillType"])
        require(color_type in (28, 29, 30, 31) and color == 0xFFFFFFFF,
                "Count paths must remain white/tintable")
        require(fill_type == 1 and original.attrib[android + "fillType"] == "evenOdd",
                "Compiled count path must retain evenOdd fill")
        require(actual["attributes"]["android:pathData"] == original.attrib[android + "pathData"],
                f"Compiled count path differs from source: {source.name}")


def verify_count_resources(apk, build_tools):
    # AAPT2 resolves resource-table paths, which may differ after resource optimization.
    table = run(build_tools / "aapt2", "dump", "resources", apk)
    entries = list(re.finditer(r"^\s*resource (0x[0-9a-fA-F]+) (\S+).*?$", table, re.M))
    expected = {"notification_count_" + suffix for suffix in
                [*map(str, range(1, 10)), "overflow"]}
    found = {}
    for index, entry in enumerate(entries):
        name = entry[2].split(":")[-1]
        if not name.startswith("drawable/") or name[9:] not in expected:
            continue
        name = name[9:]
        require(name not in found, f"Duplicate count resource: {name}")
        end = entries[index + 1].start() if index + 1 < len(entries) else len(table)
        files = set(re.findall(r"\(file\)\s+(\S+\.xml)\b", table[entry.end():end]))
        require(files, f"Count resource has no compiled XML value: {name}")
        found[name] = int(entry[1], 16)
        for file in files:
            source = MODULE_ROOT / "app/src/main/res/drawable" / (name + ".xml")
            verify_count_vector(run(build_tools / "aapt", "dump", "xmltree", apk, file), source)
    require(set(found) == expected and len(set(found.values())) == 10,
            "APK must retain all ten independent notification count vector resources")
    print("Count vectors verified: all ten compiled resources, 16dp/16x16, exact paths, white/evenOdd")


def verify_settings_ui(apk, mapping=None, build_tools=None):
    with zipfile.ZipFile(apk) as archive:
        dex = ApkDex(archive)
        activity = "Ldev/hyperos/notificationcount/settings/SettingsActivity;"
        require(activity in dex.classes, "Missing settings activity implementation")
        if mapping is not None:
            verify_mapped_ui(dex, mapping, activity)
        else:
            require(dex.classes[activity].classes[activity]["parent"] == "Landroidx/activity/ComponentActivity;",
                    "Settings must use the Compose activity implementation")
            for component in MIUIX_COMPONENTS:
                require(f"Ltop/yukonga/miuix/kmp/{component};" in dex.classes,
                        f"Missing official Miuix component: {component}")
        require(not any(name[1:-1].replace("/", ".").startswith(TEST_PACKAGES)
                        for name in dex.classes), "UI test libraries must not be packaged in the release")
    print("Settings UI verified: Compose activity and official Miuix "
          + ("R8 method provenance" if mapping is not None else "component definitions") + "; no test classes")
    if build_tools is not None:
        verify_count_resources(apk, build_tools)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--build-tools", required=True, type=Path)
    parser.add_argument("--mapping", type=Path, help="Matching R8 mapping.txt for an optimized APK")
    parser.add_argument("--signed", action="store_true")
    arguments = parser.parse_args()
    info = verify_module_apk(arguments.apk)
    verify_settings_ui(arguments.apk, arguments.mapping, arguments.build_tools)
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
        "settings_ui=miuix\nmiuix_version=0.9.4\n"
        "icon_color=optional-default-off\nicon_color_source=declared-app-icon-resource\n"
        "icon_color_sample=64x64\nicon_color_cache=64-users-and-packages\n"
        "temporary_color=optional-default-off\ncolor_duration_seconds=1,3,5,10,15\n"
        "color_duration_default_seconds=5\ncolor_expiry=one-shot-elapsed-realtime\n"
    )
    (outputs / "BUILD_RECEIPT.txt").write_text(receipt)
    print(f"Fixed signer verified; prepared {name}, SHA-256 {info['sha256']}")


if __name__ == "__main__":
    main()
