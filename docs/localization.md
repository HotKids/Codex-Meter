# Phone localization

The Simplified Chinese resources were reviewed against the installed official
Codex desktop application's English and `zh-CN` locale assets on October 3, 2026
(26.930.21537, build 12776). Only static application assets were inspected.
The API terminology was checked against the official
[App Server documentation](https://learn.chatgpt.com/docs/app-server).

| English term | Chinese term | Meaning |
|---|---|---|
| Usage limit | 使用限额 | A rate or usage limit |
| 5-hour limit | 5 小时限额 | The short usage window |
| Weekly limit | 每周限额 | The weekly usage window |
| Credits | 额度 | Purchased usage credits |
| Usage limit resets | 使用限额重置 | The reset feature |
| No resets available | 无可用重置次数 | No resets remain |
| Use reset | 使用重置 | The reset action |

These reference terms are not an exhaustive list of the app's current labels.
The approved phone copy uses 会话限额 for the five-hour window, with the shorter
会话 label on cards, and uses 剩余额度 consistently for the credit-balance label.
重置时间 remains the reset-time label. Home shows the balance as `%1$s 剩余`,
for example `62,438 剩余`. Numeric duration labels use `d` for days and `h` for
hours; the Home depletion estimate uses `预计 %1$s 后耗尽`, for example
`预计 1d 14h 后耗尽`. The remaining application copy was revised for formal
written Chinese after review.

Insert one space between Chinese and Latin text, and between compact durations
and adjacent Chinese copy, such as `预计 1d 14h 后耗尽` or `6d 11h 后重置`.
Preserve official reference terms and historical quotations as recorded.

The maintainer selected 重置额度 as the reset feature's UI name throughout Home,
widgets, live notifications and lock screens, with 使用重置额度 for its action.
These are project-specific labels, not claims that
the official reference terms above have changed. Reset inventory remains a
count, expressed with 次 rather than 券 or 张, and is distinct from purchased
usage-credit balances. Home displays quantities such as `2 次可用`; a schedule
clock icon accompanies up to three expiry countdowns, ordered by nearest
expiry and formatted with `d/h/m` units.

Widgets and live notifications share `widget_material_reset_credits`, formatted
as `重置额度：可用 %1$d`, for example `重置额度：可用 2`.
`widget_reset_credits` is `Codex 重置额度`, and
`dashboard_lock_show_credits` is `显示重置额度`. Their English counterparts use
`Reset credits: %1$d available`, `Codex reset credits` and `Show reset credits`.
Home keeps its quantity format such as `2 次可用`. These labels and explanatory
sentences are local adaptations, not quotations from the official client.

The Home cards display these approved explanations at 12sp below their titles:

- 重置额度: `使用一次重置，即可恢复你的 5 小时会话限额、每周限额，或同时恢复两者`
- 剩余额度: `购买额度或开启自动充值，即可在达到使用限额后继续使用 Work 和 Codex。`

The signed-in settings account card is titled `ChatGPT`. Home-display editor
rows use short summaries: usage history uses `查看套餐用量与额度消耗` through
`dashboard_section_usage_history_summary`. The Home card retains its separate
approved Work/Codex/Chat explanation in `dashboard_history_card_detail`.

Session, weekly and monthly editor rows describe usage and reset times. Balance
rows explain that exhausted credits hide automatically, and reset rows explain
that they hide when no reset credits are available. Editor changes save
immediately; disabled items can be enabled again. Home only displays enabled
cards with data to show.

Reminder and live-notification settings label the long window as weekly or
monthly from the reported usage snapshot, retaining the saved `weekly` key.
The accelerated-start switch explains both prerequisites while disabled:
usage estimates must be enabled and warning sensitivity must not be Off.

`Credits` in an acknowledgements screen still means 致谢.

The requested plan labels are Free, Plus, Team, Pro 100, Pro 200 and Pro 500.
Widget headers keep these names bold, with their selected casing; the static
picker uses Plus. These are maintainer-selected display names. The numbers do
not represent a claim about current official prices.

| Response identifier | Selected display name |
|---|---|
| `free` | Free |
| `plus` | Plus |
| `team` | Team |
| `prolite`, `pro5x`, `pro100` | Pro 100 |
| `pro`, `pro10x`, `pro200` | Pro 200 |
| `pro25x`, `pro500` | Pro 500 |

The former `pro20x` alias is unsupported; it is not mapped to Pro 200.
These mappings do not imply that the endpoint currently emits every alias.

The capsule and one-row dials retain upstream English text and compact time
units. The card's reset-meter value and the followed live notification's reset
meter use countdowns such as `6d 11h` in every locale. Widget usage details
retain exact `MM/dd HH:mm` dates. The Android phone app translates output from its shared
Java models by stable identifiers. Server and system error text retains its
original form.
