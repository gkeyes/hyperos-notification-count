#!/usr/bin/env python3
"""Check R8 evidence and actual APK compression, recording the measured result."""

import argparse
import json
from pathlib import Path
import re
import zipfile

from verify_module import MODULE_ROOT, digest, require


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--mapping", required=True, type=Path)
    parser.add_argument("--configuration", required=True, type=Path)
    parser.add_argument("--signed", action="store_true")
    args = parser.parse_args()
    mapping = args.mapping.read_text()
    configuration = args.configuration.read_text()
    require(re.search(r"^# compiler: R8$", mapping, re.M), "Missing R8-generated mapping")
    require(not re.search(r"^\s*-(dontshrink|dontoptimize|dontobfuscate)\b", configuration, re.M),
            "R8 shrinking/optimization/obfuscation must not be globally disabled")
    require("-keep class dev.hyperos.notificationcount.**" in configuration,
            "Module callback/config package must be preserved")
    source = (MODULE_ROOT / "app/build.gradle.kts").read_text()
    require(re.search(r"isShrinkResources\s*=\s*true", source), "Resource shrinking is not enabled")
    with zipfile.ZipFile(args.apk) as archive:
        dex = [item for item in archive.infolist()
               if re.fullmatch(r"classes(?:[2-9][0-9]*|1[0-9]+)?\.dex", item.filename)]
        require(dex and all(item.compress_type == zipfile.ZIP_DEFLATED for item in dex),
                "All release DEX entries must use standard ZIP compression")
        native = [item for item in archive.infolist() if item.filename.endswith(".so")]
        require(native and all(item.compress_type == zipfile.ZIP_STORED for item in native),
                "Native libraries must retain their original uncompressed packaging")
    baseline = 24_083_111  # Verified fixed-signature 0.1.3 APK (SHA-256 below).
    size = args.apk.stat().st_size
    require(size < baseline, "Release APK did not shrink against 0.1.3")
    report = {
        "code_shrinking": "R8", "resource_shrinking_enabled": True,
        "dex_zip_compression": "DEFLATED", "native_zip_compression": "STORED",
        "apk_bytes": size, "dex_bytes": sum(item.file_size for item in dex),
        "dex_compressed_bytes": sum(item.compress_size for item in dex),
        "mapping_sha256": digest(args.mapping), "configuration_sha256": digest(args.configuration),
        "baseline_version": "0.1.3", "baseline_apk_bytes": baseline,
        "baseline_apk_sha256": "585358b32d1859bd160ad512e3b336c9d119b34930a9f995aa0c8f029c1b9f52",
        "saved_bytes": baseline - size, "reduction_percent": round(100 * (baseline - size) / baseline, 2),
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if args.signed:
        outputs = MODULE_ROOT / "outputs"
        require((outputs / "BUILD_RECEIPT.txt").exists(), "Fixed-signature receipt must exist first")
        (outputs / "OPTIMIZATION_INFO.json").write_text(json.dumps(report, indent=2) + "\n")
        with (outputs / "BUILD_RECEIPT.txt").open("a") as receipt:
            receipt.write("code_shrinking=R8\nresource_shrinking=true\ndex_zip_compression=DEFLATED\n")
            receipt.write(f"apk_bytes={size}\nmapping_sha256={report['mapping_sha256']}\n")


if __name__ == "__main__":
    main()
