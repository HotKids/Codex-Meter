# Fork documentation

The current phone application ID, Java namespace and component classes are
`me.pipi.codexmeter`. Its version line starts at `0.1` (code 1), with compile and
target SDK 37. The current project is Android phone-only, with shared Java
models and policies used by the phone app.

| Document | Current contract |
|---|---|
| [phone-refresh.md](phone-refresh.md) | Approved phone behavior and device validation requirements |
| [ai-usage-widgets.md](ai-usage-widgets.md) | Fixed-width 2×1 dials, larger cards, reset formatting, credits and synchronized picker previews |
| [android-signing.md](android-signing.md) | Fixed phone signer, recovery and authorized tag-release publication |
| [localization.md](localization.md) | Official terminology references and approved phone copy |
| [material-symbols.md](material-symbols.md) | Action icon sources and preserved brand identities |
| [launcher-icon.md](launcher-icon.md) | Adaptive launcher icon layers |
| [usage-windows.md](usage-windows.md) | Usage-window parsing and phone widget selection |
| [refactor.md](refactor.md) | Earlier refactor scope and findings |

GitHub Actions runs core and Robolectric tests, lint and a Debug APK build on
branch pushes, pull requests and manual runs without release signing material.
After explicit authorization, stable `v*` tags on `main` trigger
`.github/workflows/android-release.yml`. Tests and lint precede restoration of
the same fixed signer from repository Secrets; the existing `./build.sh`
produces verified phone artifacts for a separate publish job. Local builds and
the signing backup remain available, while the retained legacy ciphertext is
unused. See [CONTRIBUTING.md](../CONTRIBUTING.md) for the release procedure.

## Historical migration notes

The original record below describes an earlier migration from upstream 2.8.0.
It is retained as history, not as the current product or release contract. Its
old package and namespace, 3×1 widget, plan casing, update channels, CI release
steps, companion-module freeze, provisional percentages and device-validation limitations have been
superseded by the current documents above. Historical build results do not
establish the verification status of a later release.

### Original branch change record

本目录记录 `HotKids/Codex-Meter` 相对上游 `BenItBuhner/Codex-Meter` 2.8.0 的改动。此前三份记录
（AI-Usage 适配、窗口解析修复、重构检查）对应的是已撤销的旧实现，已由下列文档替代：

| 文档 | 内容 |
|---|---|
| [refactor.md](refactor.md) | 重构范围、保证行为不变的方法、重构中发现的上游问题 |
| [ai-usage-widgets.md](ai-usage-widgets.md) | Home widgets: upstream one-row dials, Clear cards, three controls with reset-credit details, and tests |
| [usage-windows.md](usage-windows.md) | 额度周期的解析与小组件中的周期选择 |
| [android-signing.md](android-signing.md) | Fixed phone signing identity, recovery backup, and CI migration boundary |
| [localization.md](localization.md) | Official terminology references and phone localization rules |

### 1. 改动总览

按提交顺序：

1. **恢复上游基线**：撤销旧分支提交，从上游 2.8.0 重新开始。
2. **CI 与测试**：`build-apk.yml` 在 `main`、`alpha` 推送时运行，并执行 Robolectric 单元测试
   （`./gradlew :app:testDebugUnitTest`）；修正调试源码集的编译错误。
3. **移除 iOS**：删除 `ios/` 与 `ios-ci.yml`。
4. **品牌**：蓝色额度环启动图标；Now Bar / 实时更新使用的 Codex 图标。
5. **重构**：见 [refactor.md](refactor.md)。
6. **首页小组件**：见 [ai-usage-widgets.md](ai-usage-widgets.md)。
7. **包名与更新源**：
   - 应用 ID 改为 `me.pipi.usage`，Java 包名仍为 `dev.bennett.codexmeter`。
   - 广播动作、OAuth 服务动作和签名级内部权限由 `BuildConfig.APPLICATION_ID` 生成（清单中为
     `${applicationId}`），因此可与上游版本同时安装，不会因权限重名而安装失败。
   - 应用内更新、关于页和“在 GitHub 打开”指向 `HotKids/Codex-Meter`，上游旧包名的 APK 不会被当作更新。
   - Wear OS 保持 `dev.bennett.codexmeter` 不变（冻结），因此无法与改名后的手机端通过 Data Layer 配对。
8. **套餐名称**：`pro`（及旧缓存 `pro20x`）显示为 Pro 10X；`pro25x` 显示为 Pro 25X；新增 Team、Business、Enterprise、Premium。
9. **模型额度解析**：`additional_rate_limits` 位于 `rate_limit` 内时也能读取，额度 ID 不变。
   The phone display and settings have been removed; parsed model data remains for the frozen
   shared/Wear protocol.
10. **简体中文**：见第 2 节。
11. **上游问题修复**：见第 3 节。

### 2. 简体中文本地化

- 手机端全部界面、通知、通知渠道、小组件、OAuth 浏览器结果页都有 `values-zh-rCN` 资源，英文为默认回退。
  资源按模块分文件：`strings_dashboard`、`strings_auth`、`strings_updates`、`strings_settings`、
  `strings_alerts`、`strings_widget_card`、`strings_widget_editor`，以及 `settings_arrays`、`arrays`。
- 键名使用语义化的模块前缀，整句格式化（`%1$s` 位置参数），数量使用 `<plurals>`；英文输出与上游一致
  （唯一变化：数量为 1 时由 “1 minutes / Every 1 hours” 改为正确的单数）。
- 冻结的共享模块（`android/shared/`）只输出英文，手机端按稳定 ID 翻译：`SharedLabels`（首页分区、
  历史分区、套餐价格、小组件项）、`NowBarText`（与 `NowBarCopy` 规则一致，自测逐项比对英文输出）。
- 纯 Java 类（自测直接调用）保持英文，界面按错误代码或分区 ID 显示译文，例如
  `SettingsTransfer.Problem`、`UpdatePreferences.checkIntervalLabel`。
- 后端与登录错误使用 `OAuthClient.UserFacingException`：`getMessage()` 为英文（写入诊断日志），
  `getLocalizedMessage()` 为界面语言；界面一律显示后者。
- 保留英文的内容：套餐名（产品名）、实时活动胶囊、单行（2×1、3×1）圆环小组件、
  服务器或系统返回的错误原文、GitHub 发布说明原文、诊断日志、导出文件中的安全警告。
- 已保存的错误信息（上次错误、更新错误）保持保存时的语言，直到下一次刷新或检查。

术语：用量、额度、使用限额、周期、5 小时 / 每周 / 每月、剩余 / 已用、使用限额重置、重置次数、登录 / 退出登录、首页、
小组件 / 锁屏小组件、实时更新、更新渠道、稳定版 / Alpha 版。

### 3. 上游问题修复

重构时发现、单独提交修复的问题：

- **OAuth 回调端口泄漏**：绑定失败的端口 socket 未关闭就尝试下一个端口，现会先关闭。
- **用量图表滑动**：拖动结束后没有把滑动交还给页面，且未判断父视图为空，现已修正。
- **设置导入只导入一半**：原先每个分区在写入前才检查，后面的分区出错时前面的分区已被写入；现在写入前
  统一检查所选分区是否存在、提醒提前量是否合法、令牌是否可用。由 `SettingsImportTest` 覆盖。

其余仅记录、未修改的问题见 [refactor.md](refactor.md) 第 3 节。

### 4. 验证

- `./run-tests.sh`：纯 Java 自测与源码检查。
- `./gradlew :app:testDebugUnitTest`：Robolectric 测试，包括小组件模型、设置导入，以及小组件预览渲染
  （输出到 `android/app/build/reports/widget-previews/`）。
- `./lint.sh` 与 `./build.sh`：在 GitHub Actions 中执行。
- 本环境无法访问 Google Maven、无法安装 APK，所有界面只经过 Robolectric 渲染，**未在真机上验证**。

### 5. 待确认

- Clear（Material）样式右下角的小百分比，按参考截图实现为“周期已过去的时间比例”（截图中 5 小时周期剩余
  3 小时 59 分，对应 20%）；大号百分比与其他样式一致，显示剩余额度。
- 锁屏小组件中文缩写：“5H” 保持不变，“W/MO” 改为 “周/月”，重置券 “R%d” 改为 “券%d”，未在锁屏上实际检查宽度。
