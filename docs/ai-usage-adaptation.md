# AI-Usage 小组件适配

手机包名 `me.pipi.usage`，版本 `2.8.0` / `30`。本次在现有小组件编辑器恢复完整项目选择及拖动排序，不修改 App 本体页面、导航或图标；没有提交、推送、PR、标签或发布。

## 窗口读取与编辑

之前的数据模型把普通额度压成五小时和一个周／月窗口，附加额度只识别 primary／secondary，导致参考源码支持的有效窗口在进入编辑器之前丢失。

现在按 AI-Usage 的 `providers/codex/usage-parser.ts` 保留完整窗口数组：主额度与附加额度均读取七种窗口键；保留每周与每月同时存在、任意周期、百分比字段别名、仅有有效重置时间的窗口。来源 ID、标签、去重及排序跟随参考实现。余额、趋势和重置券不伪装成额度窗口。

完整窗口经账号隔离缓存供编辑器和渲染使用。编辑列表保留 Codex-Meter 上游的四个固定项目：五小时、每周（仅月度账号显示每月）、距离重置、重置券；当前没有数据也保留开关。接口额外返回的月度及模型额度追加到列表，已保存但暂时缺失的项目继续保留。五小时及周额度的接口别名映射到固定项目，避免重复行。

编辑器沿用上游开关及拖动手柄，至少选一项，选择数量不设上限。小尺寸显示已选顺序的前两项，中尺寸显示前四项；缺少数据的已选项目占据原位置并显示「—」。拖动顺序经保存、重新打开及数据刷新后保留。显式选择的月额度不会因周额度暂时缺失而被改成周额度。保留现有保存／取消操作，未增加自动保存。

选择同账号共用，彩色／Clear 及刷新间隔为全局小组件设置。刷新可选手动、5、15、30、60 分钟，实际执行受 Android 后台调度影响。只有旧 App 缓存时先显示已有数据，同时补取完整响应；不影响原 App 的刷新设置。

## 布局与范围

- 2×1 仍使用未改动的上游 `widget_rings.xml`、原版方向盘和英文。
- 其他尺寸保留当前 AI-Usage 布局，普通模式保留右下水印；透明模式依参考隐藏水印。
- 距离重置及重置券作为可排序项目显示，数值分别为倒计时与实际数量，不显示百分号。2×1 使用上游英文标签及图标。
- 小组件整体打开原 App，参考布局的时间为展示文字；2×1 保留原版刷新按钮。
- 应用页面、导航、图标、Wear OS、shared 模块未改动。涉及小组件请求的凭据条件保存用于避免旧请求覆盖已退出或切换的账号；主 App 原有刷新入口保持原行为。

## 参考与许可证

参考 [StarYunLee / AI-Usage.scripting](https://raw.githubusercontent.com/StarYunLee/Scripting/main/AI-Usage.scripting) 1.7.2，文件 SHA-256：`0b9794a00a8e99058670d21267bf53b1b999df34c0884c5f9ee79c364229a740`。已阅读提取的源码，没有执行完整参考脚本。

布局依据 `widget/SmallLayouts.tsx`、`widget/MediumLayouts.tsx`；完整窗口读取依据 `providers/codex/usage-parser.ts`。完整列表、开关与排序依据 Codex-Meter 原版 `WidgetConfigActivity` 和 `WidgetMeters`；这是 Android 上按用户要求保留的交互，与 AI-Usage 动态窗口列表不同。完整 MIT 许可证随 APK 位于 `assets/AI-Usage-LICENSE.txt`。

## 验证与安装包

- `./run-tests.sh`：核心自测和源码检查通过。
- Android 单元及 Robolectric 测试：160 项，0 失败、0 错误、0 跳过。覆盖固定目录、真实拖动回调、超过四项的选择保存、重新打开和旋转、缺数据占位、旧配置迁移、月额度身份保留、辅助项目数值及原版 2×1。
- 手机和 Wear lint 通过；手机有 155 条警告，未修改基线。
- `./build.sh`：签名 release 构建和校验通过。包名、版本、签名保持不变。
- Wear APK SHA-256：`d7632a309d320e198179aa2177ff1389ff82706f9379f262afaabddcfb4a696b`，与本轮之前完全相同。
- [手机 APK](../android/dist/PipiUsage-2.8.0-widget-order-4dc25911.apk)，SHA-256：`4dc259117cee8652445496fb5de4346dd41588b26acfdabaf6160eebf4166ca9`。
- [编辑页测试预览](../android/dist/widget-upstream-editor-4dc25911.png)：标注 TEST DATA / OFFLINE FIXTURE；缓存只有每周数据，仍列出四个基础项目及拖动手柄。
- 本轮改动与起始文件哈希比较：App 非小组件文件、导航、图标未改；Wear、shared 及 `widget_rings.xml` 与 Git 原版一致。

预览由 Robolectric 原生图形生成，使用离线响应夹具，不能代替设备真实返回数据或启动器实机验证。
