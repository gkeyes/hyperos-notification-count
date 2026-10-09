# App 图标像素取色探针 0.2

这是一次性 root 调试工具：Shell 启动独立 Android `app_process`，读取已安装 App 的图标资源、绘制 64×64 缩略图、提取候选主色。无需安装 APK、启用模块或增加 LSPosed 作用域，不修改通知数量模块，也不启动目标 App。

## 手机执行

1. 将整个 `icon-color-probe-0.2.zip` 解压到手机 Download 文件夹，例如 `/sdcard/Download/icon-color-probe/`。保持 `classes.dex`、`run_icon_probe.sh`、`SHA256SUMS.txt`、README、src 和收据文件在原有目录结构中。
2. 在 MT 管理器中，以 root 身份执行 `run_icon_probe.sh`。默认按当前通知的包名及 Android 用户去重，最多取样 32 个 App，每次只运行一次。
3. 或者在 MT 终端运行：

```sh
su
sh /sdcard/Download/icon-color-probe/run_icon_probe.sh
```

也可以手动指定 App，无需先产生通知：

```sh
sh /sdcard/Download/icon-color-probe/run_icon_probe.sh com.tencent.mm com.openai.chatgpt com.xingin.xhs
```

普通包名按 Android 用户 0 查询；其他空间可用 `10:com.tencent.mm` 这样的参数。成功与否取决于该空间是否安装 App，以及 ROM 是否允许读取对应资源。

## 输出与含义

终端输出示例（非真机实测）：

```text
app=com.tencent.mm user=0 source=launcher-resource status=COLORFUL dominant=#07C160 candidate=#07C160 colorful_fraction=0.7000 visible_pixels=3000
```

- `dominant`：可见像素中最多的 RGB 色簇的平均色，可能是白底或黑色。
- `candidate`：排除接近黑色、低饱和度及半透明边缘后，最多的彩色色簇；彩色像素不足 2% 或不足 8 个时不提供候选。
- `colorful_fraction`：彩色像素在可见像素中的比例，便于判断候选是否只有极少噪点。
- `COLORFUL`：有彩色候选，仍需看原图是否符合预期，不能据此保证是品牌色。
- `NEUTRAL`：图标可读取，但没有足够明显的彩色像素。不会为灰黑图标编造颜色。
- `EMPTY` / `FAILED`：无有效像素，或初始化、权限、资源/文件读取失败；失败不会冒充黑色或有效颜色。

结果保存在工具目录下新的 `results/时间-进程号/`，包含原图 PNG、`colors.tsv` 和离线 `colors.html` 色卡。用 MT 打开 HTML 或 PNG，对照 App 图标与候选色。回传终端输出或 `colors.tsv` 即可，不需要通知正文。

## 取色来源与边界

优先读取启动 Activity 声明的图标资源，缺少时读 Application 图标资源；支持普通位图、VectorDrawable 和 AdaptiveIconDrawable。这里不调用 Launcher 的主题换图逻辑，可能与 HyperOS 主题替换后、黑白主题或快捷方式图标不同。本版不读取通知小图标，也不读取 `Notification.color`，用于独立验证 App 图标像素这条渠道。

自动模式只读取 `cmd notification list` 的包名/用户，不读取通知正文、标题和头像。输出只包含图标及颜色元数据。

DEX 在加载前复制到本次专用 `/data/local/tmp/icon-color-probe-进程号/` 并设为只读，退出时移除本次临时副本；结果目录会保留。程序只进行取样和写调试报告，不改变系统设置、通知或已安装应用。

## 构建和验证

只在 GitHub Actions 使用 Android SDK 37.0、Build Tools 37.0.0、JDK 21（Java 源码目标为 8）编译 Java 和 D8，运行取色算法断言并校验 DEX 入口/校验和。没有本地编译、模块 APK 构建、签名操作或模拟器验证。

`probe_version=0.2` 是此独立工具的内置版本，和通知数量模块的 APK 版本无关。构建来源见 `BUILD_RECEIPT.txt`；当前 `phone_runtime=not-tested`，真实 HyperOS root 环境的资源读取与绘制需要本次手机执行确认。

代码：`src/ColorSampler.java`、`src/IconColorProbe.java`。源码中的 ActivityThread / 多用户 Context 通过反射调用，ROM 不支持时会明确失败。

依据：[AOSP ActivityThread](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/ActivityThread.java)、[Android Drawable.draw](https://developer.android.com/reference/android/graphics/drawable/Drawable#draw(android.graphics.Canvas))、[动态代码的只读要求](https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading)。
