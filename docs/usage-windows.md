# Usage windows and widget selection

This document describes how the Android phone app parses `/wham/usage` and
selects the usage windows shown on Home and widgets. `UsageParser` parses the
response; `UsageSnapshot`, `UsageLimit` and `WidgetMeters` provide shared Java
models and selection policies. `WidgetRenderer` and `WidgetMeter` own phone
widget selection and presentation.

Additional model limits can still be parsed into the snapshot, but they are not
displayed or configured on the phone. Legacy widget selections for those models
migrate to the standard phone meters.

## Response parsing

| Field | Purpose |
|---|---|
| `plan_type` | Plan identifier formatted by `UsageFormat.planLabel`; `prolite`/`pro5x`/`pro100` display Pro 100, `pro`/`pro10x`/`pro200` display Pro 200, and `pro25x`/`pro500` display Pro 500; the former `pro20x` alias is unsupported |
| `rate_limit.allowed`, `rate_limit.limit_reached` | Whether the account is currently limited |
| `rate_limit.primary_window`, `rate_limit.secondary_window` | Standard usage windows |
| `additional_rate_limits[]` | Additional model windows; when absent at the root, parsing falls back to `rate_limit.additional_rate_limits` |
| `rate_limit_reset_credits.available_count` | Available reset count |
| `credits` | Purchased usage-credit balance |

Primary and secondary windows are classified by `limit_window_seconds`, rather
than by their position in the response. Each window is assigned to at most one
cadence.

| Cadence | Target duration | Accepted range |
|---|---|---|
| Session | 5 hours | 3–8 hours |
| Weekly | 7 days | 5–9 days |
| Monthly | 30 days | 10–45 days |

When several windows qualify, the one nearest the target duration wins; ties
keep the first candidate. A Free account can report only a roughly 30-day
window. In that case `weekly` is absent, `monthly` is present and
`UsageSnapshot.longWindow()` returns the monthly window.

Each additional model entry reads its own `rate_limit`, or the entry itself
when that object is absent. Entries without either primary or secondary windows
are discarded. The ID uses the first non-empty `limit_id`, `limit_name` or
`metered_feature`, followed by the array index; nested-array fallback does not
change this identity rule.

## Phone meter selection

Each widget stores its ordered selection in `WidgetOptions.visibleMeters`.

| Key | Presentation |
|---|---|
| `five_hour` | Session usage; 会话 / Session |
| `weekly` | Weekly usage, or monthly when weekly is absent; 每周 / Weekly or 每月 / Monthly |
| `next_reset` | Earliest future reset among the session and long windows; 重置 / Reset |
| `usage_credits` | Numeric remaining credits on cards; 剩余额度 / Credits remaining |
| `reset_credits` | Legacy standalone reset count, migrated to `next_reset` |

The fixed-width 2×1 dial editor offers session, weekly/monthly and reset meters.
Larger cards also offer remaining credits. `WidgetOptions.effectiveVisibleMeters`
merges a legacy reset-count selection into the reset meter, preserves its first
position and removes duplicates. `WidgetRenderer.selectedKeys` resolves the
selection against the phone catalog and keeps the saved order.

The editor lists selected keys before the other available keys. Disabling a
meter does not remove its switch. Missing usage retains its selected slot with
a dash.

## Values and reset details

`WidgetMeter` presents usage values and progress as remaining percentages. The
reset meter considers only future, timed standard windows and ignores additional
model windows. Its progress represents the remaining fraction of the selected
window's duration.

On cards, the reset-meter value uses compact countdowns such as `6d 11h` in all
locales. Usage reset details retain exact `MM/dd HH:mm` dates. Available reset
counts appear below the reset bar; they do not have a separate progress bar.
Remaining credits are numeric and use at most two fractional digits. The 2×1
dials preserve their compact countdowns and omit reset inventory.

The followed live notification's reset meter uses the compact countdown, while
widget usage details retain exact dates. See
[ai-usage-widgets.md](ai-usage-widgets.md) for the layout, spacing and preview
synchronization contract.
