# 额度周期的解析与选择

本文说明手机端如何从 `/wham/usage` 响应得到各个额度周期，以及首页小组件如何引用它们。
相关代码：`UsageParser`（解析）、`UsageSnapshot` / `UsageLimit` / `WidgetMeters`（`android/shared/`，冻结）、
`UsageCardWindows`（小组件选择）。

## 1. 解析

`UsageParser.parse` 只读取下列字段，其余字段忽略：

| 字段 | 用途 |
|---|---|
| `plan_type` | 套餐，交给 `UsageFormat.planLabel` 显示（`pro` / `pro20x` / `pro10x` 显示为 Pro 10x） |
| `rate_limit.allowed`、`rate_limit.limit_reached` | 账户是否受限 |
| `rate_limit.primary_window`、`rate_limit.secondary_window` | 标准周期 |
| `additional_rate_limits[]` | 模型专属额度（如 Codex Spark）；根节点没有时回退到 `rate_limit.additional_rate_limits` |
| `rate_limit_reset_credits.available_count` | 可用重置券数量 |
| `credits` | 余额信息 |

### 1.1 标准周期按时长归类

primary / secondary 不按位置固定含义，而是按 `limit_window_seconds` 归类，每个周期只归入一类：

| 类别 | 目标时长 | 接受范围 |
|---|---|---|
| 5 小时 | 5 小时 | 3–8 小时 |
| 每周 | 7 天 | 5–9 天 |
| 每月 | 30 天 | 10–45 天 |

同一类有多个候选时取时长最接近目标的一个，相同时取先出现的。Free 套餐只返回约 30 天的周期，
因此 `weekly` 为空、`monthly` 有值；`UsageSnapshot.longWindow()` 在这种情况下返回月周期。

### 1.2 模型专属额度

`additional_rate_limits` 中每一项读取自身的 `rate_limit`（没有时把这一项本身当作 `rate_limit`），
取其中的 primary / secondary 周期；两个周期都缺失的项会被丢弃。

额度 ID 保持上游规则：`limit_id`、`limit_name`、`metered_feature` 中第一个非空值，再加上
`-<数组下标>`，例如 `codex_bengalfox-0`。嵌套位置的回退不改变 ID，因此首页卡片排序、显示设置和
小组件选择不需要迁移。

## 2. 小组件中的周期键

每个小组件保存一个有序的周期键列表（`WidgetOptions.visibleMeters`，按小组件 ID 分别保存）：

| 键 | 对应周期 | 标题（zh-CN / en） |
|---|---|---|
| `five_hour` | 5 小时周期 | 5 小时 / 5-hour |
| `weekly` | 长周期：有每周时为每周，只有每月时为每月 | 每周 / Weekly，或 每月 / Monthly |
| `monthly` | 每月周期，仅在每周和每月同时存在时单独提供 | 每月 / Monthly |
| `limit:<identity>:primary` / `:secondary` | 模型专属额度的两个周期 | 例如 Codex Spark 5 小时 / Codex Spark 每周 |

`<identity>` 由 `WidgetMeters.limitIdentity` 生成（额度 ID 转小写，逗号替换为下划线）。
名称或 `metered_feature` 含 `spark` 的额度前缀显示为 “Codex Spark”，否则使用额度名称、功能名，
最后回退为 “Codex 附加额度”（en: Codex extra limit）。
模型额度标题后半部分按实际时长生成（5 小时、每周、N 天），时长未知时按
primary = 5 小时、secondary = 每周处理。

### 2.1 选择规则（`UsageCardWindows`）

- `availableKeys`：当前快照实际返回的周期，顺序为 5 小时、长周期、每月（仅双长周期时）、各模型额度。
- `defaultKeys`：未保存选择时，取前两个可用周期；没有数据时为 `five_hour`、`weekly`。
- `resolve`：只保留周期键（上游的“下次重置”“重置券”等辅助项会被忽略），去重并保持保存顺序，
  最多 4 个；全部无效时回退到默认值。当前缺失的周期仍保留位置，卡片显示 “—”。
- `catalog`：编辑器列表，先列已选项（保持顺序），再列其余可用周期。

重置时间和重置券不是周期：重置时间显示在每个周期的“X后重置”中，可用重置券（数量 > 0）
自动显示在卡片底部，并给出最近的到期时间。
