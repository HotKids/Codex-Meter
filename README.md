# Codex Meter

Codex Meter 是一款用于查看 ChatGPT 账户 Codex 用量的开源 Android 应用。它将会话限额、每周或每月限额、重置时间及剩余额度集中呈现在首页，也可通过桌面小组件和实时通知查看。

本项目由 [HotKids/Codex-Meter](https://github.com/HotKids/Codex-Meter) 维护，基于 [BenItBuhner/Codex-Meter](https://github.com/BenItBuhner/Codex-Meter) 开发，并保留 GitHub 分叉关系。

## 当前版本

| 项目 | 信息 |
|---|---|
| 版本 | `0.1`，版本代码 `1` |
| 应用 ID、Java 命名空间及组件包名 | `me.pipi.codexmeter` |
| 最低系统版本 | Android 8.0（API 26） |
| 编译与目标 SDK | Android 17（API 37） |
| 更新渠道 | 稳定版；测试版为暂未接入的占位选项 |

应用内更新仅检查本项目发布的安装包。版本记录见 [CHANGELOG.md](CHANGELOG.md)。

## 用量与提醒

- **首页显示**：查看会话限额、每周限额、重置时间及剩余额度；账户使用每月周期时显示每月限额。可调整各模块的显示状态和顺序。
- **剩余额度**：以“62,438 剩余”等形式显示，最多保留两位小数。
- **使用记录**：在本机保存用量记录，用于查看历史变化、估算消耗速度和剩余可用时间；预计耗尽时间使用“预计 1d 14h 后耗尽”等紧凑表达。
- **用量提醒**：根据用量设置预警，也可按已获取的重置时间安排提醒。重置提醒触发后会刷新数据，实际恢复情况以服务器返回结果为准。
- **重置额度**：以“2 次可用”等形式显示可用数量，配合时钟图标，按最近到期顺序展示最多三项 `d/h/m` 到期倒计时；支持该功能的账户可通过“使用重置额度”执行重置。

首页、小组件和实时通知统一使用“重置额度”作为界面名称，仍以可用次数计数，与购买额度的剩余数量分别显示。数值时长中的天、小时使用 `d`、`h` 表示。

数据由 ChatGPT/Codex 接口直接提供。部分账户可能没有剩余额度、重置额度或某个限额周期，应用按实际返回的数据显示。

## 桌面小组件

小组件选择页提供 **Codex 用量** 与 **Codex 仪表盘** 两种组件。

| 小组件 | 布局与内容 |
|---|---|
| Codex 仪表盘（2×1） | 固定宽度的仪表盘，可选择会话、每周或每月、重置；不能横向拉伸，不显示重置额度数量 |
| Codex 用量（2×2 及以上） | 可调整大小的卡片，除上述内容外还可选择剩余额度；用量使用进度条，剩余额度直接显示数值 |

- 关闭某项显示后，其设置开关仍保留，可随时重新开启；已选内容可以拖动排序。
- 小组件标题以粗体显示套餐名：`Free`、`Plus`、`Team`、`Pro 100`、`Pro 200`、`Pro 500`。这些名称按本项目维护者选定的规则映射。
- 卡片中重置进度条上方使用 `6d 11h` 等倒计时；会话和每周限额的重置详情保留 `MM/dd HH:mm` 精确日期。
- 卡片在重置进度条下方以“重置额度：可用 2”等形式显示数量。4×2、5×2 布局还会在刷新按钮前显示上次成功刷新时间，例如 `12:34`。
- 卡片统一内容行间距，窄布局按空间调整文字大小；选择页的示例同步生成。
- 支持系统动态取色、背景显示和透明度设置。点击刷新按钮可更新数据，点击其他区域可打开应用。

兼容的 Samsung One UI 设备还可使用锁屏与息屏显示组件；具体展示能力取决于系统固件。

## 实时通知

开启实时监控后，可持续查看当前限额及重置信息，并可随时停止。支持的系统可将通知显示为实时活动或状态栏胶囊；Samsung 设备还可能显示在 Now Bar 中。

通知标题为 **Codex Meter**，第二行跟随最近一次保存的小组件内容与顺序，其中重置使用 `6d 11h` 等倒计时，并以“重置额度：可用 2”等形式显示可用数量。首页关闭会话限额显示后，实时通知也会隐藏该项。胶囊保留上游英文标记和紧凑时间单位。

展开的通知使用应用图标；胶囊和小组件保留 Codex 标志。应用操作图标采用 Material Symbols Outlined，自适应应用图标使用蓝色渐变背景与白色前景。

## 外观与设置

应用采用 One UI 风格界面，提供简体中文与英文资源。设置页包含账号、外观设置、首页显示、数据刷新、用量提醒、实时通知、应用更新、备份迁移、隐私说明和关于页面。

外观支持跟随系统及动态取色。备份迁移可导入或导出设置；诊断功能可记录并导出排查所需的信息。中文与英文之间保留一个空格，紧凑时长与中文说明也使用空格分隔，例如“预计 1d 14h 后耗尽”。

## 登录与数据存储

登录通过系统浏览器完成，使用 OAuth 授权码与 PKCE。访问令牌和刷新令牌由 Android Keystore 支持的 AES-GCM 加密存储在设备上。

应用直接访问 ChatGPT/Codex 接口，没有应用中转服务器，也不包含广告或使用统计服务。本机用量记录、设置和诊断数据的说明可在应用的“隐私说明”中查看。

## 从源码构建

需要 JDK 21、Android SDK Platform `37.0`、Android Build Tools `36.x`，并设置 `ANDROID_SDK_ROOT`。项目使用提交的 Gradle Wrapper。

One UI / SESL 依赖已有本地缓存。仅在缓存不完整或需要更新 GitHub Packages 依赖时，才需配置 `GH_USERNAME` 和具备 `read:packages` 权限的 `GH_ACCESS_TOKEN`。

在项目根目录运行：

```bash
./run-tests.sh
./android/gradlew --project-dir android :app:testDebugUnitTest
./lint.sh
./build.sh
```

`run-tests.sh` 执行核心自测与源码检查；Robolectric 测试包含小组件渲染，示例图片输出到 `android/app/build/reports/widget-previews/`。测试渲染用于检查布局，不替代真机验证。

正式安装包使用固定签名。构建前需恢复本机忽略目录 `android/.local-signing/` 中的签名材料；缺失时构建失败，不会生成替代密钥。公开证书指纹固定在 `android/ci/phone-signing-certificate.sha256`，恢复方法见 [签名说明](docs/android-signing.md)。

输出文件位于 `android/dist/`：

- `CodexMeter-me.pipi.codexmeter-<versionName>.apk`
- `SHA256SUMS.txt`

修改小组件布局、字体或间距后，需运行 `android/tools/widget-card-shadow.sh` 同步生成阴影布局与选择页示例。更多约定见 [贡献说明](CONTRIBUTING.md) 和 [小组件文档](docs/ai-usage-widgets.md)。

## 版本发布

分支和拉取请求由 GitHub Actions 执行测试、lint 及 Debug 构建，生成的安装包仅用于测试，不使用正式签名材料。

维护者明确授权后，推送基于 `main` 的稳定版 `v*` 标签会触发 `.github/workflows/android-release.yml`。首个手机正式版本为 `v0.1`，保留版本代码 `1`，不启用测试渠道。流程先在无签名材料的环境中完成测试与 lint，再从仓库 Secrets `SIGNING_KEYSTORE_BASE64` 和 `SIGNING_STORE_PASSWORD` 恢复现有固定签名材料，调用既有 `./build.sh` 构建。

核对签名指纹、包名、版本、目标 SDK 与校验和后，流程上传 APK 和 `SHA256SUMS.txt`，由独立发布任务创建 GitHub Release。发布说明取自 [CHANGELOG.md](CHANGELOG.md) 中对应的版本记录。本地固定签名、桌面备份和本地构建方式继续保留；旧版加密签名文件不参与新流程。详情见 [签名说明](docs/android-signing.md)。

已发布版本可在 [GitHub Releases](https://github.com/HotKids/Codex-Meter/releases) 查看。

## 许可与项目关系

项目采用 [MIT 许可证](LICENSE)。Codex Meter 并非 OpenAI 或 Samsung 的官方应用，也不受其背书。OpenAI、ChatGPT、Codex、Samsung、Galaxy、One UI 等名称和标志归各自权利人所有。

ChatGPT/Codex 接口及 Samsung 锁屏集成能力可能随服务或系统更新发生变化。
