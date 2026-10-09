# HyperOS 通知数量 0.1.7

基于 libxposed API **102.0.0**，将 HyperOS 4 顶部通知 App 图标替换为一个通知数量图标。设置页从 LSPosed / Vector 的模块设置入口打开，**不显示桌面图标**。启用模块或安装新版后，在 SystemUI 下次加载模块时生效；新版加载后的过滤开关通过框架实时更新。

推荐作用域固定为「系统界面」`com.android.systemui`。`META-INF/xposed/scope.list` 中只有这一项，`module.prop` 设置 `staticScope=true`，要求支持 API 102 的管理器仅提供这个作用域。模块也在运行时检查包名、主进程和 Android 17 / API 37。

## 显示与计数

| 有效通知条数 | 显示 |
| --- | --- |
| 0 | 隐藏 |
| 1–9 | 实心圆徽标，数字镂空 |
| 超过 9 | 实心圆徽标，三点 `···` 镂空 |

按当前空间及原生当前 profiles 的通知记录统计，默认包含折叠、静默和常驻通知。已取消、本地划掉及被父汇总划掉的通知排除；同 key 更新不额外加一。有效子通知所在组的汇总不重复计数，孤立汇总保留为一条。类型过滤前先确认组内孩子，过滤孩子不会使汇总重新计数。这里表示系统通知条目总数，各 App 的真实未读消息数量不在此口径内。

## 过滤设置

设置页使用 [Miuix 0.9.4](https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4) 的 `TopAppBar`、`Card`、`SmallTitle`、`SwitchPreference`、`OverlayDropdownPreference` 和按钮组件，跟随系统深浅色与字体大小。原有 15 项过滤及 API 102 配置格式保持兼容，可从固定签名的 0.1.1–0.1.5 直接更新。

15 个开关全部默认关闭，**开启表示从计数中排除**。只修改数量，不取消通知、不影响通知面板，也不改超级岛的展示。条目可能同时属于多类，命中任一开启的开关即排除。建议每次只开一项测试，然后再组合。

可测试的类型：焦点通知、包含超级岛内容、可更新的焦点通知、已提升的 Android 实时通知、请求实时通知展示、系统固定通知、持续运行标记、禁止清除标记、前台服务通知、当前不可清除、悬浮通知固定中、媒体播放通知、通话类通知、静默分区通知、折叠通知。各项准确判据与重叠边界见 [通知过滤说明](docs/notification-filters.md)。

设置使用 `io.github.libxposed:service:102.0.0` 的框架远程偏好，SystemUI 读取 API 102 的只读偏好并监听变更。不额外请求权限、不使用可被其他应用读取的本地配置文件、不把模块自身添加到作用域。设置连接和保存放在串行后台线程；失败时页面恢复上次确认的开关并禁用编辑，重试确认成功后才重新开放。不同 Android 空间的配置由框架隔离，请在运行对应 SystemUI 的同一空间设置。

10 个实际显示向量源自用户的 `notification-count-icons.zip`。0.1.7 起改为实心圆徽标：占位仍为 16 × 16，圆直径 12（约为时钟数字高度的 1.2 倍），并在格内向时钟一侧靠 1（保留 1 单位边距）以收紧间距，圆心下移 0.7 与时钟数字视觉中线对齐；数字与三点按同比例缩放后与圆合并为一条奇偶填充路径，镂空处透出状态栏背景；保留 16 × 16 画布、单色可 tint。设置页「显示样式」另有三项：数字颜色（自动 / 白色 / 黑色 / 透明，仅彩色模式）、圆点对比度（柔和 3:1 / 标准 4.5:1 / 高 7:1，仅彩色模式）、数字粗细（普通 / 中等 / 最粗，默认普通）。中等和最粗由普通数字轮廓分别外扩 0.175 / 0.25（即加粗 0.35 / 0.5）生成，每档 10 个矢量，共 30 个。跟随图标颜色时，数字不再镂空，而在圆下方垫一层黑或白（按徽标颜色取对比度更高者）；系统黑白模式仍保持镂空。0 状态不需要图片。原始资产及逐路径校验见 [design/source](design/source/mapping-verification.md)。

## 实现位置

0.1.5 新增「跟随通知 App 图标颜色」开关，默认关闭。开启后，空心圈和数字统一使用最新参与计数通知的 App 图标候选色；数字继续表示通知总条数。颜色来源与计数共用过滤、去重及分组规则。首次启用按现存条目的系统 post time 排序，之后只给新 key 分配新到达顺序；同 key 进度更新不会抢色，最新条目移除后回到剩余最新条目。关开取色或更换 pipeline 后重新初始化顺序。

取色复用独立探针 0.2 的机制：优先读取 launch activity 声明的图标资源，否则读取 Application 图标资源，在后台软件绘制为 64×64，再用 RGB 直方图筛选彩色候选。不读取通知正文、头像，不启动 App，不请求额外权限；不读取桌面主题替换图标。无声明图标、近黑白或资源访问失败时只回退系统黑白 tint，不影响计数。用户实测探针读到微信 `#03D769`；独立 root 探针结果不等同于本模块在 SystemUI 中已通过真机验证。

每个 App / Android user 分开缓存，最多 64 项，仅保留色值，不缓存 Bitmap 或 Drawable。彩色色值缓存 15 分钟，失败或无彩色缓存 1 分钟；到期后由下次通知事件触发重取，无定时轮询或唤醒锁。后台最多同时读取一枚图标，快速切换来源时合并中间请求；迟到的结果不能覆盖当前来源或重新打开已关闭的取色。首次读取期间使用系统 tint。根据 DarkIconDispatcher 的原生黑白选择推断深浅背景，按预览调整亮度至黑/白背景对比度至少 4.5；不采样壁纸像素。

0.1.6 新增「临时变色」开关，默认关闭，旧版持续着色行为保留。开启取色和临时变色后，可在 Miuix 下拉选项选择 1 / 3 / 5 / 10 / 15 秒，默认 5 秒。启用或修改模式 / 时长会从当前通知重新计时；新到达且参与计数的通知重新计时，即使来自同一个 App。同 key 进度更新、主题变化、重复刷新和移除最新通知不会延长期限；到期后保留数字，恢复 DarkIconDispatcher 当前的系统黑白 tint。关闭临时模式回到持续取色，关闭取色总开关立即恢复系统 tint 并取消回调。关闭全部过滤只重置过滤，保留颜色设置。

临时模式只维护一项一次性到期回调，不增加每秒倒计时、定时轮询、唤醒锁或 Hook 点。期限使用 elapsed real time；灭屏期间已过期的颜色在首次重新绘制前清除，不等待暂停的 Handler uptime。后台取色迟到时也不能越过期限恢复彩色。清空 / 过滤全部通知后取消当前窗口，重新显示旧通知不会凭空触发新窗口；新通知或用户调整设置可重新触发。

取色、临时模式、时长和过滤配置作为一组保存；保存失败时回滚到最后确认状态。新版继续保留原签名、SystemUI 单一作用域、无桌面图标，以及 0.1.4 的 R8 / 资源裁剪 / DEX 压缩。

- `HideNotifsForOtherUsersCoordinator.attach(NotifPipeline)` 完成后捕获原生 pipeline / user manager，读取 `getAllNotifs()`，在主线程创建快照。
- `NotifCollection.dispatchEventsAndRebuildList(String)`、本地划掉和清除完成后刷新，同时注册原生 collection、before-render 和 user/profile 监听，合并重复刷新。监听注册中途失败可以重试，重新挂载复用同一代理避免重复注册。计数不会跟随普通页/折叠页切换。
- `RenderNotificationListInteractor.setRenderedList(List)` 完成后捕获原生分区判断器；`NotificationUtil.setFold(...)` 和 `HeadsUpManagerImpl.setEntryPinned(...)` 完成后刷新类型。只有启用的过滤类型会被求值，默认关闭时不解析岛参数。
- `HomeStatusBarViewBinderImpl.bind(...)` 只标记顶部通知容器，在原有 `NotificationIconContainer` 上添加 Drawable overlay，保留原生子节点、绑定和父容器迁移。
- `StatusBarIconView.onDraw(Canvas)` 仅在父容器已被标记、计数准备完成且向量健康时临时裁剪原图标和原溢出点的像素，仍执行原方法及其他模块的 Hook 链，并在 finally 恢复画布。通知面板、锁屏 shelf、AOD 及系统右侧图标不属于此替换目标。
- 原生 `DarkIconDispatcher` 提供黑白 tint，原生灵动岛 monitor 提供可用宽度；不够容纳一枚完整图标时隐藏数字。布局遵循 RTL 和父级 MeasureSpec。
- 加载、计数或绘制异常时回退原图标；计数源短暂异常后可在下次有效更新恢复，损坏的显示层在新绑定时重建。字体/显示密度变化会重新计算矢量绘制尺寸。日志仅记录阶段及异常类型，不记录通知内容或 key。

## 构建与签名

GitHub Actions 使用 JDK 21、Android SDK 37.0、Gradle 9.4.1、Android Gradle Plugin 9.2.1，以及 built-in Kotlin / Compose compiler 2.4.20，执行 release 单元测试和唯一的 release 构建。AndroidX Compose 通过 BOM 2026.09.00 对齐。本地不运行编译或 Java / Kotlin 测试。

0.1.4 开启 R8 代码优化及资源裁剪，裁掉未使用的 Miuix / Compose 依赖代码，并在 APK 内压缩 DEX。由 Android 正常安装流程解压代码，不修改签名后的 APK。保留本模块的小型自有代码包，包括现代入口、API 102 回调、Hooker、配置与设置 Activity；依赖库仍参与优化。云端核对 R8 mapping 与实际 DEX、10 个编译矢量、入口 ABI、作用域和固定签名，保留 mapping / usage / configuration 作为诊断附件。

模块 Hook API 依赖为 `compileOnly`，不把框架 API 类打进 APK。设置通信库及其官方 `XposedProvider` 随 APK 打包；编译后的 manifest 检查只允许这个 provider 和模块设置 Activity，并验证没有 LAUNCHER 或额外权限。定向移除设置页不使用的 AndroidX 启动 provider、profile receiver 和旧版动态接收器权限；仅允许两条 `required=false` 的 AndroidX Window 扩展库声明。JUnit / Robolectric / Compose UI test 只用于测试，不属于模块 APK。

包名 `dev.hyperos.notificationcount`。首个本地测试包为 `0.1.0` / versionCode `1`；固定云端签名始于 `0.1.1` / versionCode `2`；原设置页版本为 `0.1.2` / versionCode `3`；Miuix 设置页始于 `0.1.3` / versionCode `4`，压缩版为 `0.1.4` / versionCode `5`，可选取色版为 `0.1.5` / versionCode `6`，临时变色版为 `0.1.6` / versionCode `7`，当前实心徽标版为 `0.1.7` / versionCode `8`。保留既有交付文件及校验值。

后续检查与编译在 GitHub Actions 执行，本地不再启动构建。云端使用本模块专属固定签名，私钥通过仓库加密 Secrets 传入，不提交到 Git。首次从本地 `0.1.0` 测试包转到云端包时，两者签名不同，需要手动卸载旧测试包后安装；后续云端包可连续更新。

## 验证边界

开发样本为 `OS4.0.0.44.XPBCNXM` / SystemUI `17.03.260226.r`，原始 APK SHA-256 为 `7da817a4c65299f7362fd530d72c09c0a4785c98cf2732395c721f2740e7662a`。Hook 签名及字段依据本地提取的 DEX 检查。

云端测试覆盖计数、过滤和去重、配置持久化与连接时序、实际设置 Activity 的开关交互，以及原方法执行、异常传播、Android 17 的 VectorDrawable、黑白切换和 overlay 子节点隔离。具体通过数量及构建记录以交付收据为准。

压缩版保留 Android 17 release 模拟器测试，可在手动触发 Actions 时选择 `release_runtime_checks`。它使用真实 Application、provider 和 Miuix Activity，测试私有偏好模拟框架连接，检查 15 项开关的保存、重建、重置、断线禁用，以及数字 0 / 1–9 / 溢出在黑白 tint 下的绘制；启用时仍必须完整通过这 3 项测试。

当前 Google API 37 模拟器的 SurfaceFlinger 在模块安装前即出现 `hasReadColorBufferDma` 图形断言，连带重启系统进程，阻止安装和测试执行；因此本次不以该实验环境阻塞发布，收据明确标记 `release_runtime=not-tested`。云端 release 功能单测、实际 APK 的入口 ABI / 作用域、Miuix / 矢量保留、优化和固定签名检查仍按原要求执行。模拟器没有 HyperOS / LSPosed，真机 SystemUI Hook 回归也仍未执行。

用户已自行安装测试 APK，并反馈当前 0.1.2 使用效果可用，继续自行调试分类关系。0.1.3 重构设置界面，0.1.4 优化打包体积，Hook 与计数分类保持原版。新版云端检查不能替代该 ROM 上的页面和通知逐项测试，开发过程未操作手机。

## 接口依据

- [LSPosed 现代模块入口与作用域说明](https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API)
- [libxposed API 102.0.0 官方 Maven 发布](https://central.sonatype.com/artifact/io.github.libxposed/api/102.0.0)，元数据与接口以该版本源码为准。
- [libxposed service 102.0.0 官方 Maven 发布](https://central.sonatype.com/artifact/io.github.libxposed/service/102.0.0)
- [AGP 9.2 支持 Android API 37.0](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
- [Robolectric 4.17 支持 SDK 37](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.17)
- [Miuix 0.9.4 组件源码](https://github.com/compose-miuix-ui/miuix/tree/v0.9.4)
- [Android R8 与资源裁剪](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
- [AGP 9.2 DEX 压缩配置](https://developer.android.com/reference/tools/gradle-api/9.2/com/android/build/api/dsl/DexPackaging)
