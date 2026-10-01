# Hook ABI 与模块打包静态校验

`tools/verify_module.py` 使用 Python 标准库直接读取 APK 的 DEX `class_defs`、`class_data`、field IDs 和 method prototypes。只比较**声明/定义**，不会把方法引用误认为目标声明，也不以反编译 Java 作为最终 ABI 证据。它不调用 Gradle、javac、测试或手机命令。

## 两种独立入口

在模块目录执行云端打包检查：

```sh
python3 tools/verify_module.py --apk app/build/outputs/apk/debug/app-debug.apk
```

此模式只需要模块 APK；不会寻找 `research` 目录、系统 APK、反编译文件或 Android SDK。适合 GitHub Actions。私有 SystemUI APK 与反编译代码不需要提交。

显式提供本地系统 APK时核对宿主 ABI：

```sh
python3 tools/verify_module.py --systemui-apk /private/path/MiuiSystemUI.apk
```

可追加 `--apk` 一并检查模块；可追加 `--json` 输出逐项声明证据。SDK 父类的反射方法可以单独加入 SDK `javap` 检查：

```sh
python3 tools/verify_module.py \
  --systemui-apk /private/path/MiuiSystemUI.apk \
  --sdk-jar "$ANDROID_HOME/platforms/android-37.0/android.jar" \
  --json
```

未提供 `--sdk-jar` 时，输出会明确标注 SDK 方法在该次运行中未核对，不伪装成 SystemUI DEX 校验项目。任何缺项、签名变化或打包规则失败均以非零状态退出；检查不依赖会被 Python `-O` 禁用的 `assert` 语句。

## 当前宿主声明结果

2026-10-01 对已提取 SystemUI APK 的原始 DEX 校验通过：32 个 `HostAccess.type` 类型、25 个反射字段、30 个宿主方法；其中 15 个 Hook 方法、2 个反优化方法都有具体 DEX 方法体。SDK `View.setMeasuredDimension` 另计 1 个。

SystemUI APK SHA-256：

```text
7da817a4c65299f7362fd530d72c09c0a4785c98cf2732395c721f2740e7662a
```

脚本读取 `SystemUiHooks.java`、`StatusBarRenderer.java`、`HostAccess.java`、`NotificationClassifier.java`。它从 String 常量、Class 变量和反射调用提取目标，并与脚本内的小型 ABI 清单比较；新增或替换目标而没有更新清单时会失败。清单不包含系统实现代码。

0.1.2 的分类新增 10 个字段与 9 个方法，完整类型/描述符保存在脚本的 ABI 清单中。`FocusUtils` owner 是 `notification.utils.FocusUtils`；高优先级会话方法参数是 `PipelineEntry`。新增 3 个 Hook 为 `NotificationUtil.setFold(NotificationEntry, boolean)`、`HeadsUpManagerImpl.setEntryPinned(HeadsUpEntry, PinnedStatus, String)`、`RenderNotificationListInteractor.setRenderedList(List)`，全部完成原调用后刷新。分类口径见 [通知过滤说明](notification-filters.md)。以下表格保留原显示层的 15 个字段及 21 个方法。

下表的名称使用以下前缀缩写：

- `BAR` = `com.android.systemui.statusbar.`
- `COLLECTION` = `BAR` + `notification.collection.`
- `BINDER` = `BAR` + `pipeline.shared.ui.binder.`
- `PLUGINS` = `com.android.systemui.plugins.`

### 字段声明（15 项）

| 宿主类 | 字段 | DEX 类型 |
| --- | --- | --- |
| COLLECTION.NotifPipeline | mNotifCollection | COLLECTION.NotifCollection |
| COLLECTION.coordinator.HideNotifsForOtherUsersCoordinator | mLockscreenUserManager | BAR.NotificationLockscreenUserManager |
| BAR.NotificationLockscreenUserManagerImpl | mListeners | java.util.List |
| COLLECTION.NotificationEntry | mSbn | BAR.notification.ExpandedNotification |
| COLLECTION.NotificationEntry | mDismissState | COLLECTION.NotificationEntry$DismissState |
| COLLECTION.NotificationEntry | mCancellationReason | int |
| COLLECTION.NotificationEntry$DismissState | NOT_DISMISSED | COLLECTION.NotificationEntry$DismissState（static） |
| BINDER.HomeStatusBarViewBinderImpl | mInjector | BINDER.HomeStatusBarViewBinderInjector |
| BAR.IslandMonitor$NotificationContainerIslandMonitor | container | BAR.phone.NotificationIconContainer |
| BINDER.HomeStatusBarViewBinderInjector | mNotificationIconAreaInner | android.view.View |
| BINDER.HomeStatusBarViewBinderInjector | darkIconDispatcher | PLUGINS.DarkIconDispatcher |
| BAR.phone.NotificationIconContainer | mInject | BAR.phone.NotificationIconContainerInject |
| BAR.phone.NotificationIconContainerInject | showNotificationIcons | int |
| BAR.phone.NotificationIconContainerInject | _islandMonitor | BAR.IslandMonitor$NotificationContainerIslandMonitor |
| BAR.IslandMonitor$NotificationContainerIslandMonitor | islandWidth | int |

字段解析按 `HostAccess.field` 的 superclass 搜索规则核对；本批目标均能找到声明，并逐项比较字段描述符。还核对 `ExpandedNotification` 的 DEX superclass 链到 `android.service.notification.StatusBarNotification`，对应源码中的类型兼容性检查。

### 方法声明（21 项）

所有方法在指定宿主类上有精确声明，符合 `HostAccess.method` 使用 `getDeclaredMethod` 的规则。下表未标返回值的返回 `void`；`getTint` 另核对为 static。

| 宿主类 | 方法和参数 | 用途/返回值 |
| --- | --- | --- |
| COLLECTION.NotifPipeline | addCollectionListener(COLLECTION.notifcollection.NotifCollectionListener) | 注册 collection listener |
| COLLECTION.NotifPipeline | addOnBeforeRenderListListener(COLLECTION.listbuilder.OnBeforeRenderListListener) | 注册 render listener |
| COLLECTION.NotifPipeline | getAllNotifs() | java.util.Collection |
| BAR.NotificationLockscreenUserManagerImpl | isCurrentProfile(int) | boolean |
| COLLECTION.coordinator.HideNotifsForOtherUsersCoordinator | attach(COLLECTION.NotifPipeline) | Hook |
| COLLECTION.NotifCollection | dispatchEventsAndRebuildList(String) | Hook |
| COLLECTION.NotifCollection | dismissNotifications(java.util.List, boolean) | Hook / 反优化目标 |
| COLLECTION.NotifCollection | dismissAllNotifications(int) | Hook / 反优化目标 |
| BINDER.HomeStatusBarViewBinderImpl | bind(BAR.phone.PhoneStatusBarView, BAR.pipeline.shared.ui.viewmodel.HomeStatusBarViewModel, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1) | Hook |
| BINDER.HomeStatusBarViewBinderInjector | onUnbind() | Hook |
| BAR.phone.NotificationIconContainer | onMeasure(int, int) | Hook |
| BAR.phone.NotificationIconContainer | onLayout(boolean, int, int, int, int) | Hook |
| BAR.phone.NotificationIconContainer | onConfigurationChanged(android.content.res.Configuration) | Hook |
| BAR.phone.NotificationIconContainer | setMaxIconsAmount(int) | Hook |
| BAR.StatusBarIconView | onDraw(android.graphics.Canvas) | Hook |
| BAR.IslandMonitor$NotificationContainerIslandMonitor | updateContainerSize(android.graphics.Rect, boolean, boolean) | Hook |
| PLUGINS.DarkIconDispatcher | addDarkReceiver(PLUGINS.DarkIconDispatcher$DarkReceiver) | 注册 receiver |
| PLUGINS.DarkIconDispatcher | removeDarkReceiver(PLUGINS.DarkIconDispatcher$DarkReceiver) | 移除 receiver |
| PLUGINS.DarkIconDispatcher | getTint(java.util.Collection, android.view.View, int) | static int |
| BAR.phone.NotificationIconContainer | getActualPaddingStart() | float |
| BAR.phone.NotificationIconContainer | getActualPaddingEnd() | float |

四个 Proxy 目标 `NotificationLockscreenUserManager$UserChangedListener`、`NotifCollectionListener`、`OnBeforeRenderListListener`、`DarkIconDispatcher$DarkReceiver` 均是 DEX interface，有声明的 callback，callback 返回值全部为 void。`DarkReceiver.onDarkChanged(java.util.ArrayList, float, int)` 还按参数完整签名单独核对。

原显示层 20 个类型还包括以上类、`NotificationEntry$DismissState`、`PhoneStatusBarView`、`HomeStatusBarViewModel` 和 `kotlin.jvm.functions.Function1`；新增分类与刷新类型另计 12 个。类型/成员清单在脚本内可直接审阅，`--json` 会列出成员所属 DEX 与 access flags。

### Android SDK 父类例外

`StatusBarRenderer` 使用独立的 `HostAccess.method(View.class, "setMeasuredDimension", int.class, int.class)`。该方法不在 SystemUI APK 中声明，不以容器继承关系冒充直接声明。

本地 SDK 37.0 的只读 `javap -p android.view.View` 输出确认：

```java
protected final void setMeasuredDimension(int, int);
```

对应描述符 `(II)V`。这次校验使用 `platforms/android-37.0/android.jar`，没有运行编译。

## 模块 APK ZIP/DEX 结果

首版归档 `app/build/outputs/apk/debug/app-debug.apk` 已做以下 ZIP/DEX 校验，SHA-256（不是 0.1.2 的交付校验值）：

```text
57ea94e49e653625653b7cf1354be02f00ac09ecd085720c2afcf5dfa0cc14e2
```

必要检查均通过：

- 现代 Java 入口恰为 `dev.hyperos.notificationcount.NotificationCountModule`，且该类在模块 DEX 中定义并继承框架 `XposedModule`。
- `META-INF/xposed/scope.list` 恰为 `com.android.systemui`。
- `module.prop` 的 `minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`、`autoHotReload=false`。
- 必要 ZIP 文件存在且不重复；没有旧 `assets/xposed_init` 等入口。
- 模块 DEX/散装 class 没有打包 `io.github.libxposed.api` 或旧 `de.robv.android.xposed` 的类定义。DEX 对框架 API 的引用允许存在。

**Manifest 的 Android 组件、权限及旧 manifest 元数据由 `verify_release.py` 使用 aapt XML 另查。** 只允许模块设置 Activity 与官方 libxposed 服务 provider，要求只有管理器的设置入口、没有桌面入口或额外权限。0.1.3 的 Miuix / AndroidX 依赖另允许两条指定名称且 `required=false` 的 Window 扩展库声明，不增加组件。`verify_module.py` 本身只确认 AndroidManifest.xml 存在，也不检查 APK 签名、安装状态或当前源代码是否与某次已构建 APK 逐项一致。交付的固定签名、版本、commit 与 APK 校验值以 Actions 生成收据为准。

0.1.4 开启 R8 后，模块自有小包保持类名及成员，依赖库参与裁剪和混淆。产物检查进一步要求模块入口及两个 Hooker 是 public / concrete，入口具有 public 无参构造，API 102 的 `onModuleLoaded(ModuleLoadedParam)`、`onPackageReady(PackageReadyParam)` 及两个 `intercept(Chain): Object` 都是 public 实例方法且含实际 DEX 代码。未将 compileOnly 的框架 API 打包。

APK 的 DEX 使用 DEFLATED，native `.so` 仍保留原包装。所取证的 Vector 提交 `efb82883071643ca16128ecd588be7c40c1e45e6` 使用 [ZIP 解压流读取 classes.dex](https://github.com/JingMatrix/Vector/blob/efb82883071643ca16128ecd588be7c40c1e45e6/daemon/src/main/kotlin/org/matrix/vector/daemon/data/FileSystem.kt#L358)，再从 SharedMemory 的 ByteBuffer 创建模块 ClassLoader，没有要求 DEX 必须是 ZIP STORED。这是框架源码兼容性依据，不能替代用户设备加载该版本模块的运行证据。

## 证据范围

这些结果只证明指定 APK 的类/字段/方法声明及模块入口打包约定满足当前代码要求。它们不证明 Vector/LSPosed 实际加载、ART Hook 命中、反优化成功、通知生命周期表现、状态栏交互或真实设备运行。云端 `--apk` 通过也不代表完成私有宿主 DEX 复核。
