# 重构记录（2026-10）

本轮从上游 `BenItBuhner/Codex-Meter` 的 2.8.0（`2c1dbd3`）重新开始：先撤销此前的分支改动
（`e742364`），在上游代码上做只改结构、不改行为的重构，再重新实现分支需求。Wear OS（`android/wear/`）
和手机与手表共用的 `android/shared/` 未做任何改动。

## 1. 范围

上游手机端大量代码是反编译后留下的形态：JADX 注释、`r0`/`i2` 一类变量名、魔法数字、超长方法和
不可达分支。重构按模块分 6 次提交：

| 提交 | 模块 | 主要内容 |
|---|---|---|
| `37255f6` | 应用内更新与更新说明 | 拆分渲染、解析、安装的长方法；超时、缓冲区、任务期限、请求码等改为命名常量；删除 minSdk 26 下不可达的 `Html.fromHtml` 旧分支和未使用的 `UpdatePreferences.latestStable` |
| `bc41e63` | 数据与网络 | `UsageParser`、OAuth、后端请求改写为可读代码；`UsageApi` 统一一个带鉴权的请求通道，`ResetCreditApi` 复用它的 401 重试；请求头、超时、`NETWORK_LOCK` 和诊断事件不变 |
| `14cd5ba` | 三星锁屏小组件 | 拆分视图构建、删除不可达分支和未用重载；槽位解析移到 `LockMeterBinding`；`WidgetOptions`/`LockWidgetOptions` 使用真实参数名和常量 |
| `7169f2a` | 偏好、调度与通知 | `AppPreferences` 分段整理；`NowBarManager.post()` 拆为多个步骤；刷新任务统一结束路径；闹钟调度共用 `setWakeUpAlarm()` |
| `32bb3fd` | 首页、引导与 UI 辅助 | 删除首页中不可达的旧卡片和相关代码路径；`Ui` 合并恒为真的 One UI 分支；匿名类改为 lambda |
| `43fd8f7` | 设置与用量历史 | 原 1500 行的设置 Fragment 按页面拆为 `Settings*Fragment`；图表绘制按图层拆分 |

另有两处修正：调试源码集中的 `UsagePaceDemoActivity` 少传一个参数导致调试构建无法编译
（`75e3454`）；按钮图标原本按英文文字推断，但现有按钮文字都不匹配，改为由调用方显式指定
（`d602b04`），避免翻译后失效。

## 2. 保证行为不变的方法

- 用户可见文字、偏好键、请求码、通知 ID、广播内容、JSON 字段和副作用顺序保持不变。
- 更新器的校验顺序和失败处理逐项对照；版本比较、渠道、解析、校验和与 Markdown 输出与旧代码做了
  差分随机测试。
- 每次提交都通过 `android/run-tests.sh`（纯 Java 自测和源码检查）；重构完成后的 `43fd8f7` 在
  GitHub Actions 中通过了 `:app:testDebugUnitTest`、lint 和发布构建。
- 由于本环境无法访问 Google Maven，本地使用 javac 对照 Android API 存根编译全部手机源码和测试，
  最终以 CI 结果为准。

## 3. 重构中发现、未在重构中修改的上游问题

重构只改结构，以下问题按原样保留，记录在此：

- `UpdateInstaller`：校验文件下载、“校验文件未列出 APK”和存储空间检查在 `try` 之外，失败时不会记录
  `update_prepare_failed`；`open(url)` 失败也不会记录 `request_failed`。
- `UpdateActivity` / `ReleaseHistoryActivity`：`ReleaseVersion.compare` 遇到非 SemVer 的已安装版本会
  抛异常（正式构建的版本号总是 SemVer，实际不会触发）。
- `ReleaseHistoryActivity` 在后台线程完成后直接 `runOnUiThread` 渲染，没有检查页面是否已销毁。
- 三星锁屏圆环和表盘样式的位图相同（`SamsungLockGraphics` 未使用样式参数）；锁屏编辑器预览总是
  绘制宽表盘布局。
- `UsageRefreshJobService`：成功路径中若 `jobFinished` 或下一次调度抛异常，会再走一次失败路径。
- `NowBarManager`：重启后恢复的预览使用没有重置时间的示例周期，与首次预览不同。
- `ResetNotificationManager`：调度失败被静默吞掉。
- 设置导出页把所选导出项只保存在 Fragment 字段中，文件选择器期间进程被回收会丢失选择。

在后续提交中修复的问题见 [README.md](README.md) 第 3 节。
