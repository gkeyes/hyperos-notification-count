# HyperOS 通知数量 0.1.1

基于 libxposed API **102.0.0** 的最小模块，将 HyperOS 4 顶部通知 App 图标替换为一个通知数量图标。没有启动页或设置界面；启用模块后，在 SystemUI 下次加载模块时生效。

推荐作用域固定为「系统界面」`com.android.systemui`。`META-INF/xposed/scope.list` 中只有这一项，`module.prop` 设置 `staticScope=true`，要求支持 API 102 的管理器仅提供这个作用域。模块也在运行时检查包名、主进程和 Android 17 / API 37。

## 显示与计数

| 有效通知条数 | 显示 |
| --- | --- |
| 0 | 隐藏 |
| 1–9 | 用户提供的空心圈数字图标 |
| 超过 9 | 用户提供的空心圈三点图标 `···` |

按当前空间及原生当前 profiles 的通知记录统计，包含折叠、静默和常驻通知。已取消、本地划掉及被父汇总划掉的通知排除；同 key 更新不额外加一。有效子通知所在组的汇总不重复计数，孤立汇总保留为一条。这里表示系统通知条目总数，各 App 的真实未读消息数量不在此口径内。

10 个实际显示向量来自用户的 `notification-count-icons.zip`，SVG 路径直接映射到 Android VectorDrawable，保留 16 × 16 画布与奇偶填充。0 状态不需要图片。原始资产及逐路径校验见 [design/source](design/source/mapping-verification.md)。

## 实现位置

- `HideNotifsForOtherUsersCoordinator.attach(NotifPipeline)` 完成后捕获原生 pipeline / user manager，读取 `getAllNotifs()`，在主线程创建快照。
- `NotifCollection.dispatchEventsAndRebuildList(String)`、本地划掉和清除完成后刷新，同时注册原生 collection、before-render 和 user/profile 监听，合并重复刷新。监听注册中途失败可以重试，重新挂载复用同一代理避免重复注册。计数不会跟随普通页/折叠页切换。
- `HomeStatusBarViewBinderImpl.bind(...)` 只标记顶部通知容器，在原有 `NotificationIconContainer` 上添加 Drawable overlay，保留原生子节点、绑定和父容器迁移。
- `StatusBarIconView.onDraw(Canvas)` 仅在父容器已被标记、计数准备完成且向量健康时临时裁剪原图标和原溢出点的像素，仍执行原方法及其他模块的 Hook 链，并在 finally 恢复画布。通知面板、锁屏 shelf、AOD 及系统右侧图标不属于此替换目标。
- 原生 `DarkIconDispatcher` 提供黑白 tint，原生灵动岛 monitor 提供可用宽度；不够容纳一枚完整图标时隐藏数字。布局遵循 RTL 和父级 MeasureSpec。
- 加载、计数或绘制异常时回退原图标；计数源短暂异常后可在下次有效更新恢复，损坏的显示层在新绑定时重建。字体/显示密度变化会重新计算矢量绘制尺寸。日志仅记录阶段及异常类型，不记录通知内容或 key。

## 构建与签名

使用 JDK 21、Android SDK 37.0、Gradle 9.4.1、Android Gradle Plugin 9.2.1。在 `local.properties` 设置本机 `sdk.dir`，运行：

```sh
./gradlew :app:testReleaseUnitTest :app:assembleRelease
```

模块 API 依赖为 `compileOnly`，不会把 libxposed 实现打进 APK。JUnit / Robolectric 只用于测试，不属于模块 APK。

包名 `dev.hyperos.notificationcount`。已交付的首个本地测试包是 `0.1.0` / versionCode `1`，保留原文件及校验值；源码现为 `0.1.1` / versionCode `2`，用于首个 GitHub Actions 版本，关闭 Java 项目不需要的内置 Kotlin 依赖。

后续检查与编译在 GitHub Actions 执行，本地不再启动构建。云端使用本模块专属固定签名，私钥通过仓库加密 Secrets 传入，不提交到 Git。首次从本地 `0.1.0` 测试包转到云端包时，两者签名不同，需要手动卸载旧测试包后安装；后续云端包可连续更新。

## 验证边界

开发样本为 `OS4.0.0.44.XPBCNXM` / SystemUI `17.03.260226.r`，原始 APK SHA-256 为 `7da817a4c65299f7362fd530d72c09c0a4785c98cf2732395c721f2740e7662a`。Hook 签名及字段依据本地提取的 DEX 检查。

本地测试覆盖计数与去重、状态映射、原方法执行及异常传播，以及 Android 17 的 VectorDrawable 加载、Skia 渲染、黑白切换和 overlay 子节点隔离。具体通过数量及构建记录以交付收据为准。

用户已自行安装首个 `0.1.0` 测试 APK，并反馈实际效果可用。这证明首版已在该手机生效，尚未逐项完成通知分组、清除、空间切换及所有主题的真机回归。云端 `0.1.1` 的改动以 Actions 检查为依据，开发过程未操作手机。

## 接口依据

- [LSPosed 现代模块入口与作用域说明](https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API)
- [libxposed API 102.0.0 官方 Maven 发布](https://central.sonatype.com/artifact/io.github.libxposed/api/102.0.0)，元数据与接口以该版本源码为准。
- [AGP 9.2 支持 Android API 37.0](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
- [Robolectric 4.17 支持 SDK 37](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.17)
