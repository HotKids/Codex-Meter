# Home widgets

The phone exposes a fixed-width 2×1 dial widget and a resizable Clear card. There is no 3×1
provider. One-row placements use the upstream dial arcs with Clear system colors and
remaining percentages. Larger cards use compact title/value rows, progress bars for usage,
and numeric remaining credits without a bar. Narrow cards stack the first two selected
meters; wide cards show up to four in two columns.
Each card title reserves its native measured width; the value fills the remaining row
space. Renderer estimates may reduce value text size but never assign a fixed value
column that can truncate the title. Picker and transparent layouts use the same row contract.

All content switches remain visible when disabled or when data is absent. The one-row editor
offers session usage, weekly usage and reset. Larger cards add remaining credits.
For recognized Pro 100, Pro 200 and Pro 500 accounts, unconfigured home widgets start with
weekly usage and reset selected, with session usage disabled but still available. Other
accounts retain the existing defaults. Saved selections, including legacy metric-mode
preferences, override account defaults and survive later plan changes.
Reset uses the earliest future core-window reset. The Clear reset meter's value uses upstream
compact countdown units such as `6d 11h`, `2h 50m` and `45m` in every locale. The followed
live notification's reset meter uses the same countdown. Widget usage reset details retain
exact `MM/dd HH:mm` times. Clear places
available resets below the bar; dials keep their existing English countdowns and omit reset
inventory. All Clear card sizes share the same spacing between title, bar and detail rows;
the fixed-width 2×1 dial uses its separate adaptive geometry.
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

Dial graphics adapt to the host width and height while retaining the upstream arc path and stroke.
The shared XML reserves 4dp above and below the content and lets the weighted graphic
shrink when its natural size does not fit. Graphics stop growing at the original 56dp width.
Values stay on one line, use up to 14.5sp, and shrink only when the column is too narrow.
Their reserved height scales with the system font size. Arc masks exclude only the empty viewport below
the end caps; percentage insets place the original icons on the same cropped canvas.
The native picker and installed widget therefore use the same height adaptation.
Provider identity keeps the Clear card at its legal minimum height. When a dial shrinks
back to one row, a remaining-credits-only selection falls back to weekly usage in both
the renderer and editor.

The editor keeps a description below every display item, including when data is absent.
Dial usage descriptions identify the usage value; card usage descriptions also include
reset times. The dial reset description covers the next-reset countdown; the card reset
description also covers available reset credits. Missing data adds a status after the
description instead of removing the item. Editor changes take effect only after saving.
Dials describe manual refresh in the app; cards also describe their visible refresh button.

Home providers use the standard Android picker contract on all launchers. Android 12 and
later use one `previewLayout` per provider: the 2×1 dials or the populated 2×2 card. Larger
placements use the same card provider and its standard resize bounds. Home providers do not
declare Samsung-specific preview sizes or supported-cell metadata; Samsung lock-screen
providers retain their separate integration. Android 8–11 use the platform preview fallback.
Launcher chrome and preview measurement remain host-controlled and require device acceptance.
If a host omits its precise size list, rendering uses the portrait and landscape content
bounds from its MIN/MAX options. The predefined size buckets apply only when no usable
host dimensions are available.
