# Widget appearance

Color selection and launcher geometry are independent. Automatic selects the Material dynamic palette
only on Google Pixel devices and the classic palette elsewhere. Material and Classic explicitly select
their respective palettes on every device. The selection is stored per widget and survives editor
recreation and widget restoration. Default widget options are included in settings export/import.

Material (质感) keeps the stored identifier `native`. Clear cards use low-chroma system neutral2
surfaces: shell/panel tones 95/90 in light mode and 10/20 in dark mode. Their `widget_card_surface`
and `widget_card_panel` roles are separate from the original `widget_material_surface`, so
2×1 dials retain the Gmail-style secondary shell (light tone 95, dark 20); they have no inner
panels. The launcher resolves both sets from its current system palette.

Classic keeps the SmartThings-style shells (`#FCFCFF` / `#010102`). Clear card panels use the
softer `#F4F4F7` / `#1E1E21` pair; one-row shells and dials remain unchanged.
On the ColorOS stock launcher, only background colors and
existing corner parameters are overlaid. Its Clear shells stay `#FFFFFF` / `#373737`, with softened
panels `#F7F7F7` / `#414141`.

The original ColorOS visual reference is Consumer IR's QuickCard configuration preview, whose
shells are `#FFFFFF` / `#373737` and panels are `#F5F5F5` / `#474747`. The running launcher card
uses the separately distributed `com.oplus.consumerirapp.quickapp` template; its exact CSS remains
unverified. The reference preview does not establish a verified host color contract, and the
polished Clear panels deliberately differ from its colors.

For every host, card opacity applies to the outer background, inner panels and progress fills/tracks
in Material and Classic styles, including the editor preview. Pixel retains its stored 56/65/100% ticks;
Samsung and ColorOS keep their existing 30/66/100% conversion. Text and icons retain their original
opacity. Turning Background off hides the backgrounds but keeps progress at full opacity. The 2×1
dials have no inner card panels and retain their existing behavior.
ColorOS still hides the 2×1 picker entry and uses
the common narrow 2×2 layout. No launcher-specific fonts or content layouts are introduced.

Card typography scales with height, independent of width. At minimum heights, the common renderer
reduces internal padding and gaps using actual font line heights. Normal placements retain their
existing spacing and sizes. Meter labels use medium weight (500), while numeric values keep bold
weight (700); the one-row dials are unchanged. Widget-editor drafts survive activity recreation
and are persisted only by Save; Cancel leaves the placed widget unchanged.

Card refresh controls retain the header center line. Narrow cards below 250dp balance the glyph's
visible top and end gaps, including Sync Problem's wider bounds. Wider cards keep the glyph center
on the shell's right-corner axis (mirrored in RTL), using the actual system or ColorOS radius. Their independent
targets fit inside the card and end before the first content row; the centered foreground ripple
uses a 22dp circular mask and an 11dp radius. The framework DayNight theme keeps native feedback
visible above the image-based shell. Layout aliases reload previously cached foregrounds once.
Picker previews represent the narrow card and derive balanced gaps from the common header metrics.

Accepted manual refreshes show a native rotating version of the current status icon: Sync for
current data, Sync Problem for waiting, failed, or stale data. Rotation ends on completion,
failure, cancellation, or reconciliation of an abandoned job. State capture and host publication
share one serialized update boundary so a completed refresh cannot be overwritten by an older
busy render. Transient feedback has a separate
noncredential generation record; an older job cannot end a newer request's animation. Reconciliation
uses a nonexact alarm through the existing refresh receiver and may be delayed by the OS. Queued
feedback expires after five minutes without cancelling the underlying job; an active request
continues rotating until its terminal state. Old boot or invalid elapsed-time records are discarded.
Narrow cards below 250dp show no status text. Wider cards use complete status messages where they
fit; all nonnormal resting states use Sync Problem, and only normal idle data shows its timestamp.

Explicit refresh failures from the dashboard, widget, or live notification are shown immediately.
The 15-minute fresh-cache grace applies only to background failures. Once a manual failure is
visible, subsequent background failures keep it visible until a successful usage response clears
it; failed cards use Sync Problem and hide the last successful refresh time.

Classic usage severity follows
[AI-Usage](https://raw.githubusercontent.com/StarYunLee/Scripting/main/AI-Usage.scripting):
used below 60% is green, 60% to below 85% is orange, and 85% or above is red. Fill lengths and labels
continue to show the remaining allowance. Reset countdown progress is waiting time, not usage, so it
keeps the normal green. Missing usage has no visible fill. Clear card fills use gradients in all
three ranges: green `#6FCA4B` → `#09C265`, orange `#FFB74D` → `#FF9F0A`, and red `#F58B8F` →
`#ED5F64`. Their end colors retain the Samsung battery green/red and AI-Usage warning orange.
The 2×1 dials retain their existing green gradient and solid orange/red fills. Material uses the
same thresholds with system primary, secondary and tertiary fills, respectively (tone 40 in light mode, tone 80 in dark mode).
Its reset countdown keeps the primary fill. Palette resources are resolved by the launcher.

Classic card headers and live-notification capsules use the transparent
[colored Codex asset](https://zonalogo.com/codex-logo). Capsules use transparent PNGs generated by
native Android rendering of that vector at 95% scale, with a 24dp intrinsic size; Samsung's wrapper
keeps its existing 32dp size.
On the inspected ColorOS 17 build, `OplusNotificationFixHelper` preserves the original notification
icon in `oplus_small_icon`. The Live Alert plugin reads that icon and forces `VectorDrawable` to white
before rasterizing it, so capsule resources use `BitmapDrawable`. Other notification hosts may still
tint Android small icons to monochrome. The launcher icon, expanded activity icon and widget vector
retain their existing resources.

Picker previews are generated from the common layouts and renderer metrics. Samsung uses its
size-specific static preview metadata and classic palette. ColorOS gets a 146dp minimum square
preview because its picker measures RemoteViews without constraints and only scales down. These
preview-only sizes do not alter placed-widget bounds or resize contracts.
