#!/usr/bin/env bash
# GitHub Actions only. No Gradle/module build or signing credentials are used.
set -euo pipefail
cd "$(dirname "$0")"
probe_sdk="${ANDROID_HOME:?}/platforms/android-37.0/android.jar"
probe_tools="${ANDROID_HOME:?}/build-tools/37.0.0"
test -f "$probe_sdk"
mkdir -p build/test build/classes build/dex build/bundle/src outputs

javac --release 8 -d build/test src/dev/hyperos/tools/ColorSampler.java test/dev/hyperos/tools/ColorSamplerTest.java
java -cp build/test dev.hyperos.tools.ColorSamplerTest | tee build/algorithm-test.txt
javac --release 8 -cp "$probe_sdk" -d build/classes src/dev/hyperos/tools/*.java
mapfile -t probe_classes < <(find build/classes -name '*.class' -type f | sort)
"$probe_tools/d8" --min-api 28 --lib "$probe_sdk" --output build/dex "${probe_classes[@]}"
"$probe_tools/dexdump" build/dex/classes.dex > build/dex-info.txt
python3 - <<'PY'
from pathlib import Path
import hashlib, re, struct, zlib
dex=Path('build/dex/classes.dex').read_bytes()
assert dex.startswith(b'dex\n') and dex[7] == 0
assert struct.unpack_from('<I',dex,8)[0] == zlib.adler32(dex[12:]) & 0xffffffff
assert dex[12:32] == hashlib.sha1(dex[32:]).digest()
assert b'Ldev/hyperos/tools/IconColorProbe;' in dex
dump=Path('build/dex-info.txt').read_text()
assert re.search(r"name\s*:\s*'main'\s+type\s*:\s*'\(\[Ljava/lang/String;\)V'\s+access\s*:\s*0x0009",dump)
assert b'Ldev/hyperos/tools/ColorSamplerTest;' not in dex
print('PASS executable DEX checksum, entry descriptor and test exclusion')
PY
cp build/dex/classes.dex run_icon_probe.sh README.md build/bundle/
cp src/dev/hyperos/tools/*.java build/bundle/src/
cp build/algorithm-test.txt build/bundle/ALGORITHM_TEST.txt
{
    printf '%s\n' 'probe_version=0.2' "commit=$GITHUB_SHA" \
        "run=https://github.com/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID" \
        'build_location=github-actions' 'dex_min_api=28' 'runtime=app_process' \
        'phone_runtime=not-tested' 'apk_install=false' 'module_modified=false'
} > build/bundle/BUILD_RECEIPT.txt
(
    cd build/bundle
    sha256sum classes.dex run_icon_probe.sh README.md src/*.java ALGORITHM_TEST.txt BUILD_RECEIPT.txt > SHA256SUMS.txt
    zip -q -r ../../outputs/icon-color-probe-0.2.zip .
)
(cd outputs && sha256sum icon-color-probe-0.2.zip > SHA256SUMS.txt)
