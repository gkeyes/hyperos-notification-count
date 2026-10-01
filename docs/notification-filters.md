# 通知过滤开关的判据

适用研究样本：HyperOS `OS4.0.0.44.XPBCNXM` / SystemUI `17.03.260226.r` / Android 17。仅排除有效通知条目的计数，不操作原通知。所有开关默认关闭；开启多个开关时按并集排除。

| 开关 | 实际判据 |
| --- | --- |
| 焦点通知 | `ExpandedNotification.mIsFocusNotification` |
| 包含超级岛内容 | 三种焦点 extras JSON 的非空 `param_v2.param_island`，兼容顶层 `param_island` |
| 可更新的焦点通知 | 原生 `FocusUtils.isUpdatableFocusNotification(Notification)` |
| 已提升的 Android 实时通知 | `ExpandedNotification.mIsPromotedOngoing` |
| 请求实时通知展示 | 公开 SDK `Notification.isRequestPromotedOngoing()`，与已获批准分开 |
| 系统固定通知 | 原生 `ExpandedNotification.isPersistent()`；当前样本为系统应用 + `miui.isPersistent` |
| 持续运行标记 | `FLAG_ONGOING_EVENT` |
| 禁止清除标记 | `FLAG_NO_CLEAR` |
| 前台服务通知 | `FLAG_FOREGROUND_SERVICE` |
| 当前不可清除 | `!NotificationEntry.isClearable()`，包含原生焦点和分组规则 |
| 悬浮通知固定中 | `NotificationEntry.isRowPinned()`，系统固定或用户固定都匹配 |
| 媒体播放通知 | 原生 `NotificationUtil.isMiuiMediaNotification(entry)`，包含 Android 媒体及小米媒体会话扩展 |
| 通话类通知 | `CATEGORY_CALL` 或 `EXTRA_TEMPLATE` 为 `Notification.CallStyle` |
| 静默分区通知 | 原生 SectionStyleProvider 与高优先级会话判断，见下文 |
| 折叠通知 | `ExpandedNotification.mIsFold`；不是当前是否打开折叠页 |

岛内容检测读取 `miui.focus.param`、`miui.focus.param.custom`、`miui.focus.param.media`。不把空对象、数组、无效 JSON 当作岛内容。携带参数不代表参数能渲染，或通知此刻正在岛上显示；未提供没有可靠逐通知判据的“当前正在超级岛显示”开关。充电、音乐等系统卡片也不一定在普通通知集合里。

静默分区沿用原生模型：`entry.attachState.section == null` 时匹配；bucket 为 4 时排除高优先级会话，其他分区检查 `SectionStyleProvider.silentSections`。因此尚未分区或折叠过滤后的条目也可能匹配，不能等同于通知渠道关闭声音。分区判断器由 `RenderNotificationListInteractor.setRenderedList` 完成后捕获。

`NotificationUtil.setFold`、`HeadsUpManagerImpl.setEntryPinned` 完成后合并刷新。焦点、promotion、固定和媒体缓存随着通知更新读取；主线程只创建值快照，不持有通知内容或导出诊断列表。关闭全部过滤时跳过类型求值，保留原版计数口径。

## 设置入口与配置

设置 Activity 只声明 `MAIN` + `de.robv.android.xposed.category.MODULE_SETTINGS`，没有 LAUNCHER。已对齐用户框架 [Vector canary 3111](https://github.com/JingMatrix/Vector/releases/tag/canary-3111) 的 [模块设置查找实现](https://github.com/JingMatrix/Vector/blob/efb82883071643ca16128ecd588be7c40c1e45e6/manager/src/main/kotlin/org/matrix/vector/manager/ipc/DaemonClient.kt#L70-L96)。

API 102 远程偏好以原子 int mask 存储，位值在 enum 中固定，不使用 ordinal。SystemUI 强引用 listener 并把配置变化转到主线程；[Vector 的监听实现](https://github.com/JingMatrix/Vector/blob/efb82883071643ca16128ecd588be7c40c1e45e6/xposed/src/main/kotlin/org/matrix/vector/impl/VectorRemotePreferences.kt#L27-L87) 按 [Android 用户隔离分发](https://github.com/JingMatrix/Vector/blob/efb82883071643ca16128ecd588be7c40c1e45e6/daemon/src/main/kotlin/org/matrix/vector/daemon/ipc/InjectedModuleService.kt#L29-L93)。只有 `com.android.systemui` 的静态推荐作用域，不增加模块自身或其他应用。

这些判据有宿主 DEX 声明与源码路径依据；云端测试不能代替这版 ROM 上的真实通知测试。建议全关作为基线，每次只开一项，观察数字变化后恢复，再测试下一项。
