#!/system/bin/sh
# One-shot, root-only Android icon probe. No APK install, app launch or setting changes.
set -u
if [ "$(id -u 2>/dev/null)" != 0 ]; then
    printf '%s\n' '请在 MT 管理器中以 root 身份执行，或先在终端输入 su。' >&2
    exit 1
fi
probe_here=$(CDPATH= cd "$(dirname "$0")" && pwd) || exit 1
if [ ! -f "$probe_here/classes.dex" ] || [ ! -f "$probe_here/SHA256SUMS.txt" ]; then
    printf '%s\n' '请先解压整个 ZIP，再运行其中的 run_icon_probe.sh。' >&2
    exit 1
fi
if ! (cd "$probe_here" && sha256sum -c SHA256SUMS.txt >/dev/null 2>&1); then
    printf '%s\n' '文件校验失败，或系统缺少 sha256sum；请重新解压完整 ZIP。' >&2
    exit 1
fi
if ! command -v app_process >/dev/null 2>&1; then
    printf '%s\n' '找不到 Android 的 app_process。' >&2
    exit 1
fi

# Explicit arguments are PACKAGE (user 0) or USER:PACKAGE.
if [ "$#" -eq 0 ]; then
    if ! probe_keys=$(cmd notification list </dev/null 2>/dev/null); then
        printf '%s\n' '通知列表读取失败。可在命令后手动指定 com.tencent.mm。' >&2
        exit 2
    fi
    probe_seen='|'
    probe_total=0
    while IFS= read -r probe_key; do
        case "$probe_key" in *'|'*'|'*) ;; *) continue ;; esac
        probe_user=${probe_key%%|*}
        probe_tail=${probe_key#*|}
        probe_package=${probe_tail%%|*}
        case "$probe_user" in ''|*[!0-9]*) continue ;; esac
        case "$probe_package" in ''|*[!A-Za-z0-9_.]*) continue ;; esac
        probe_spec="$probe_user:$probe_package"
        case "$probe_seen" in *"|$probe_spec|"*) continue ;; esac
        probe_seen="$probe_seen$probe_spec|"
        set -- "$@" "$probe_spec"
        probe_total=$((probe_total + 1))
        if [ "$probe_total" -ge 32 ]; then break; fi
    done <<PROBE_KEYS
$probe_keys
PROBE_KEYS
fi
if [ "$#" -eq 0 ]; then
    printf '%s\n' '没有当前通知。可手动运行：sh run_icon_probe.sh com.tencent.mm' >&2
    exit 2
fi
probe_specs=
for probe_argument do
    case "$probe_argument" in *:*) probe_spec=$probe_argument ;; *) probe_spec="0:$probe_argument" ;; esac
    probe_user=${probe_spec%%:*}
    probe_package=${probe_spec#*:}
    case "$probe_user" in ''|*[!0-9]*) printf '%s\n' 'Android 用户编号无效。' >&2; exit 1 ;; esac
    case "$probe_package" in ''|*[!A-Za-z0-9_.]*) printf '%s\n' '包名无效。' >&2; exit 1 ;; esac
    if [ "${#probe_user}" -gt 5 ]; then printf '%s\n' 'Android 用户编号过长。' >&2; exit 1; fi
    probe_specs="$probe_specs $probe_spec"
done
# Each token above is restricted to ASCII digits, package characters and one colon.
set -f
set -- $probe_specs
if [ "$#" -gt 32 ]; then printf '%s\n' '单次最多检查 32 个 App。' >&2; exit 1; fi

umask 077
probe_work="/data/local/tmp/icon-color-probe-$$"
if ! mkdir "$probe_work"; then printf '%s\n' '无法创建独立运行目录。' >&2; exit 1; fi
probe_cleanup() {
    # Only this invocation's copied code file and newly created directory are removed.
    rm -f "$probe_work/classes.dex"
    rmdir "$probe_work" 2>/dev/null || true
}
trap probe_cleanup 0
trap 'exit 130' 2
trap 'exit 143' 15
if ! cp "$probe_here/classes.dex" "$probe_work/classes.dex" || ! chmod 0444 "$probe_work/classes.dex"; then
    printf '%s\n' '无法准备只读 DEX。' >&2
    exit 1
fi
probe_stamp=$(date '+%Y%m%d-%H%M%S')
probe_output="$probe_here/results/$probe_stamp-$$"
if ! mkdir -p "$probe_output"; then printf '%s\n' '无法创建报告目录。' >&2; exit 1; fi
printf '%s\n' '=== App 图标像素取色探针 0.2（单次） ===' \
    'dominant=整体主色；candidate=排除黑白后的候选色；NEUTRAL 表示缺少明显彩色。' \
    "输出目录：$probe_output"
CLASSPATH="$probe_work/classes.dex" app_process /system/bin dev.hyperos.tools.IconColorProbe --out "$probe_output" "$@"
probe_status=$?
printf '%s\n' "执行结果码：$probe_status"
if [ -f "$probe_output/colors.html" ]; then
    printf '%s\n' "MT 中打开：$probe_output/colors.html" \
        '可回传终端输出或 colors.tsv；PNG 和 HTML 可用于核对原图与颜色。'
else
    printf '%s\n' '探针初始化未完成，请回传终端输出。'
fi
exit "$probe_status"
