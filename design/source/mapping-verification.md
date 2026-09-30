# 通知数量 SVG 到 Android VectorDrawable 映射校验

原始 ZIP：`notification-count-icons.zip`。本目录的 ZIP 副本与用户提供文件逐字节相等，SHA-256 为 `199ed9a1127774a7827ab1e0f8d77d81353c9f19d579c16390949b023ce84784`。

ZIP 内的 10 个独立 SVG、矢量总览 `notification-counts.svg`、预览 `notification-count-preview.png` 和原始 `说明.txt` 均按原始字节保存。说明中的字体已转成固定路径；Android 资源不依赖字体文件或网络资源。

转换仅进行以下格式映射：SVG 的 `d` 原样赋值到 Android 的 `pathData`，`fill-rule="evenodd"` 对应 Android 的 `fillType="evenOdd"`，`fill="currentColor"` 对应可 tint 的 `fillColor="#FFFFFFFF"`。两条 path 的顺序、数字、空白和全部路径指令均保持不变；没有添加 stroke、背景、缩放或形状修改。

10 个 SVG 都是 `viewBox="0 0 16 16"`、宽高 16；对应 Android 资源都为 16 × 16 viewport、16dp × 16dp。第一条路径保留原始圆环，第二条路径保留原始数字或三颗中点。

## 逐路径字符串校验

下表对源 SVG 和已写入 XML 分别重新解析后比较 `d` 与 `android:pathData` 的完整字符串。20 条路径全部字符串全等；SHA-256 对完整 UTF-8 字符串计算，两端一致。全部源路径的规则都是 `evenodd`，全部目标路径的规则都是 `evenOdd`，奇偶填充语义完全一致。

| 源 SVG | path 序号 | 字符数 | 两端相同的 path 字符串 SHA-256 | 字符串比较 | 填充规则映射 |
| --- | --- | --- | --- | --- | --- |
| count-1.svg | 1 | 112 | `e882f5fb506e6ee698b596d24aab726c5490953adf41ef9ac61ed9219dad1cf8` | 完全相等 | evenodd → evenOdd |
| count-1.svg | 2 | 212 | `97665d38859941388501ddd355718837479d8b4590e21c2bd11ba99a42e91057` | 完全相等 | evenodd → evenOdd |
| count-2.svg | 1 | 112 | `e882f5fb506e6ee698b596d24aab726c5490953adf41ef9ac61ed9219dad1cf8` | 完全相等 | evenodd → evenOdd |
| count-2.svg | 2 | 941 | `464273228726c2f5ddd13e03bd11ec7a0cccbfd33c7434920a57b6e22138fc26` | 完全相等 | evenodd → evenOdd |
| count-3.svg | 1 | 112 | `e882f5fb506e6ee698b596d24aab726c5490953adf41ef9ac61ed9219dad1cf8` | 完全相等 | evenodd → evenOdd |
| count-3.svg | 2 | 1166 | `3938a6b02fc1a5018799cdd7c8e0a1fb7d72547265290b8990c7e9b60c12411c` | 完全相等 | evenodd → evenOdd |
| count-4.svg | 1 | 112 | `e882f5fb506e6ee698b596d24aab726c5490953adf41ef9ac61ed9219dad1cf8` | 完全相等 | evenodd → evenOdd |
| count-4.svg | 2 | 260 | `355d663a309490c018d06b23fb6a6410c3482b5a5f72b9869aacec9fb192a894` | 完全相等 | evenodd → evenOdd |
| count-5.svg | 1 | 112 | `e882f5fb506e6ee698b596d24aab726c5490953adf41ef9ac61ed9219dad1cf8` | 完全相等 | evenodd → evenOdd |
| count-5.svg | 2 | 905 | `653eafcd97af9a79f3af1959ba166dada694e4ca38c6809b4dbbab9c0d9b43d8` | 完全相等 | evenodd → evenOdd |
| count-6.svg | 1 | 112 | `e882f5fb506e6ee698b596d24aab726c5490953adf41ef9ac61ed9219dad1cf8` | 完全相等 | evenodd → evenOdd |
| count-6.svg | 2 | 1393 | `fc2052d080edef7e1aaaac1330004800da37c1c625b942c44d4df03be2d37692` | 完全相等 | evenodd → evenOdd |
| count-7.svg | 1 | 112 | `e882f5fb506e6ee698b596d24aab726c5490953adf41ef9ac61ed9219dad1cf8` | 完全相等 | evenodd → evenOdd |
| count-7.svg | 2 | 314 | `1479f036068ffc0f74c07b61d8b7a355d7dd86578849a7c834911982935faf89` | 完全相等 | evenodd → evenOdd |
| count-8.svg | 1 | 112 | `e882f5fb506e6ee698b596d24aab726c5490953adf41ef9ac61ed9219dad1cf8` | 完全相等 | evenodd → evenOdd |
| count-8.svg | 2 | 1093 | `234e9138a48b915efe74bfc92a54fe6f2baf75210b650de383c00cde8f7bdb01` | 完全相等 | evenodd → evenOdd |
| count-9.svg | 1 | 112 | `e882f5fb506e6ee698b596d24aab726c5490953adf41ef9ac61ed9219dad1cf8` | 完全相等 | evenodd → evenOdd |
| count-9.svg | 2 | 1331 | `d9fd244de2e3ed342f75f5383ba7f5628681f448718b71ff7ca92d2a0254d814` | 完全相等 | evenodd → evenOdd |
| count-overflow.svg | 1 | 112 | `e882f5fb506e6ee698b596d24aab726c5490953adf41ef9ac61ed9219dad1cf8` | 完全相等 | evenodd → evenOdd |
| count-overflow.svg | 2 | 163 | `c8a34bf7447c5e661ed7fcb1a85f9745e686286c25f1667b8cce0e4be28f6eff` | 完全相等 | evenodd → evenOdd |

## 资源编译

Android SDK build-tools 37.0.0 的 AAPT2 已成功编译这 10 个 Android VectorDrawable 资源，得到 10 个 `.flat` 输出；临时编译输出已清理。此项证明资源编译成功，不代表 SystemUI 或手机状态栏的运行验证。

对应文件位于 `../../app/src/main/res/drawable/notification_count_1.xml` 至 `notification_count_9.xml`，以及 `notification_count_overflow.xml`。
