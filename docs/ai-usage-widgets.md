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
The card header's logo aligns with the content panels' leading edge at every card size.
The refresh glyph aligns with their trailing edge: equal and opposite image padding
compensates for the official vector's transparent viewport inset without changing its
scale, height or click target. Runtime and static picker previews share that adjustment.
The header and panels share the outer content padding. Android 12 and newer explicitly
reset the header's start margin on reapply; its top, height, trailing edge and the body
remain unchanged. ColorOS overlays apply after the shared renderer, so they inherit
the same alignment. Wide-card time
and status share the native label-medium metrics: 12sp and the system 500-weight
`sans-serif-medium` alias. All visible failure statuses use the same metrics; narrow
non-failure status keeps its existing typography. Refresh failures use
the Sync Problem icon and show the complete status only when it fits beside the plan and icon;
the refresh button always exposes the full failure message to accessibility services.
Failure hides the previous successful-refresh timestamp; the next successful refresh restores
it. ColorOS one-column cards show only the failure icon, even when the status text would fit.

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

ColorOS background parameters live in `ColorOsWidgetAppearance` and independent resources.
They apply only to OPPO's stock launcher after the existing renderer builds its RemoteViews;
the original layouts, foreground and spacing remain shared. Cards use the same adaptive
typography for every launcher and column count, based on the generic 2×2 card at 170dp
height. The plan header shares the percentage value size, and all panel detail rows
share the smaller 9.5dp reference size. Font sizes follow height only, never width;
narrow cards have no separate enlargement or width-based text shrinking. A remaining
credit balance that cannot fit beside its label uses the existing second line at
the same numeric type size; a wider card keeps both fields on one line. The 2×2 ColorOS card
uses the generic narrow style. One-row backgrounds form a capsule, with portrait height
bounded by the launcher's published icon size. Cards scale their 28dp reference background
to the launcher's icon-size-derived radius and request the inspected framework's native
G2 curve. The original layouts remain unchanged. See
[the ColorOS source contract and platform boundary](coloros-widget-appearance.md) for the
read-only metadata, geometry, reapply rules and device validation requirements.
The stock ColorOS picker hides the 2×1 provider because its ordinary one-row widgets
require OEM admission for desktop labels and stacking. Existing instances remain enabled;
other launchers retain the generic entry. Its alternate provider definition is generated
from the current generic XML and changes only `hide_from_picker`.

The background layers follow the installed ColorOS calendar: a top-left to bottom-right
gradient (`#F2F3F4` to `#FAFAFA` in light mode; `#252627` to `#282929` in dark mode), with
inner panels using the existing system accent RGB and alpha `0x19`. The panel alpha belongs
to the drawable fill; an XML ImageView tint defaults to SRC_ATOP and would retain an opaque
white shape. Picker previews share the colors and drawable resources; their unknown host
bounds retain MATCH_PARENT geometry.
API 35 generated previews contain static samples only. APK replacement invalidates this
application's two home-screen previews; other widget categories remain untouched.

Home providers require configuration on first addition so launchers without an edit menu
still open the existing editor. Saving completes placement; cancellation returns the actual
instance ID, and recreation preserves it. Standard reconfiguration remains available on
hosts that support it. ColorOS 17.3.12 restricts its proprietary widget shortcut metadata
to built-in search providers, so that metadata cannot add an edit menu for Codex Meter.
