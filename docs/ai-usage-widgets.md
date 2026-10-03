# Home widgets

The phone exposes a fixed-width 2×1 dial widget and a resizable Clear card. There is no 3×1
provider. One-row placements use the upstream two-dial geometry with Clear system colors and
remaining percentages. Larger cards use compact title/value rows, progress bars for usage,
and numeric remaining credits without a bar. Narrow cards stack the first two selected
meters; wide cards show up to four in two columns.

All content switches remain visible when disabled or when data is absent. The one-row editor
offers five-hour usage, weekly/monthly usage and reset. Larger cards add remaining credits.
Reset uses the earliest future core-window reset. The Clear reset meter's value uses upstream
compact countdown units such as `6d 11h`, `2h 50m` and `45m` in every locale. The followed
live notification's reset meter uses the same countdown. Widget usage reset details retain
exact `MM/dd HH:mm` times. Clear places
available resets below the bar; dials keep their existing English countdowns and omit reset
inventory. All Clear card sizes share the same spacing between title, bar and detail rows;
the fixed-width 2×1 dial layout remains unchanged.
Wide cards show successful-refresh `HH:mm` before the refresh icon. Header plan labels are
bold with the selected Free, Plus, Team, Pro 100, Pro 200 and Pro 500 casing, and reuse
the capsule Codex mark. The static picker uses Plus. The 100/200/500 numbers are
maintainer-selected display names, not price claims; the former 20X alias is unsupported.

The live notification follows the most recently saved widget selection and order. Home's
five-hour visibility still applies. Saving the followed widget and its options is atomic;
background refresh, cancellation and another widget's restore do not change the selection.
The capsule retains upstream English text and its existing monochrome Codex mark.
The expanded Samsung notification uses the launcher icon. Android SystemUI supplies the
application icon for the notification row; no large icon is added on the right.

Regenerate shadow and picker layouts with `android/tools/widget-card-shadow.sh` after editing
`widget_material.xml` or the renderer's typography and spacing metrics. The picker generator
reads these metrics from `MaterialCardRenderer` so previews follow the installed card design.
Unit tests render production RemoteViews with synthetic data under
`android/app/build/reports/widget-previews/`. These previews do not replace device acceptance.
The Android phone app owns this widget presentation contract.
