# ColorOS widget geometry

`ColorOsWidgetAppearance` owns the stock ColorOS launcher parameter overlay. It applies
background resources and the geometry below on OPPO devices whose default
home package is `com.android.launcher`, on Android 12 or later. The original widget
layouts and dial renderer remain the common source. The overlay changes only the
specified background layers and corner geometry, and hides the 2×1 picker entry.
The 2×2 card uses the generic narrow layout, typography, spacing, refresh icons,
and status rules. Larger cards and picker previews also reuse the current generic
source; there is no ColorOS typography or content-layout branch.

## One-row picker availability

The inspected launcher uses `WidgetAlignUtils.isAlignSpan` for both desktop labels
and stacking. Its one-row branch requires the provider class to appear in the OEM
`need_widget_optimize_componentName` list. `CodexDialWidget` is absent. These checks
use the current cell spans; they do not read `resizeMode` or minimum resize height.
Disabling vertical resizing therefore cannot give this provider a label or stacking
eligibility.

A native RPK registration probe was tested on PLP110 with UMS 17.20.25 and launcher
17.3.12 on 2026-10-04. The static card was built by official `hap-toolkit` 2.1.1 and
declared under the fixed-signature `me.pipi.codexmeter` package, using its own synthetic
card type. Normal APK replacement triggered scanning. The phase-only observation
reported `OWN_PACKAGE_AUTH_FILTERED`: `RpkCachedScanner` discovered the provider but
removed its package from the accepted set before `loadXmlMetaData`. This establishes
the current package's admission failure, not a rendering error or a general inability
to support third-party cards. The temporary provider, resources, and RPK are not shipped.
Revisit native integration if the application's platform admission changes. Refer to
OPPO's [card access overview](https://open.oppomobile.com/documentation/page/info?id=11726)
and [card debugging guide](https://open.oppomobile.com/documentation/page/info?id=12287);
their full text requires a platform login. The current-device result comes from the
installed scanner code and its package-filter event.

The fallback hides only the 2×1 picker entry on the stock ColorOS launcher. It uses
the public `WIDGET_FEATURE_HIDE_FROM_PICKER` flag through
[`updateAppWidgetProviderInfo`](https://developer.android.com/reference/android/appwidget/AppWidgetManager#updateAppWidgetProviderInfo(android.content.ComponentName,%20java.lang.String)).
The alternate XML is generated from the current generic provider and adds only that
flag. HOME eligibility, receiver, configuration activity, dimensions, previews,
existing IDs, and saved options remain intact. Removing HOME eligibility or disabling
the receiver could cause this launcher to delete placed widgets and must not be used.
The existing `updateAll` path reconciles the flag only when its value differs; a later
update under another launcher restores the generic definition with a null metadata key.
Immediate restoration during a launcher switch is unverified.

The actual picker is AssistantScreen's `CardStoreActivity`, whose `AppWidgetDataManager`
also filters the public hide bit when it re-enumerates providers. Its cached list can
still show the previous entry while an already-open page remains active. On the test
device, the app's normal UID readback returned `features=3/category=1`; reopening the
card center then showed only the 2×2 entry. Existing bindings 8, 11, and 12 remained.
No component was disabled, and no launcher or card-center process was stopped.

## Source contract

The inspected ColorOS launcher is `com.android.launcher` 17.3.12 (170030012), on
ColorOS 17 / Android 17. Its `LayoutSettingsHelper.saveIconSize` publishes the
final launcher icon size, in dp, to `Settings.Secure.layout_icon_size`.
`IconProfile` writes the post-theme value during construction and updates;
`LayoutSettingsHelper` also writes the final target-grid value after layout
animation. This is a read-only input for the app. It is not an atomic desktop
layout completion signal.

`LauncherCardView` derives the ordinary card radius from `i6.p` and truncates it
to an integer pixel value. For the active smooth-corner branch, the calculation
is:

```text
iconPx = round(iconDp * density)
baseRadiusPx = round(28dp * density)
radiusPx = int(iconPx / (56dp * density) * baseRadiusPx)
```

The app keeps a 28dp outer gradient drawable and scales only its ImageView by
`radiusPx / drawableRadiusPx` around the top-left origin. Its layout width and
height are divided by that scale, preserving the final visible bounds to pixel
rounding. The content, inner panels, text, icons, and progress bars are not
scaled. Placed widgets supply the host's actual content width and height in dp.

The [generated-preview API](https://developer.android.com/reference/android/appwidget/AppWidgetManager#setWidgetPreview(android.content.ComponentName,%20int,%20android.widget.RemoteViews))
does not supply the picker's eventual content bounds. The no-size overload
therefore retains MATCH_PARENT background and root dimensions and applies only
the ColorOS resources, colors, and opacity. It skips both inverse background
scaling and compact dial height. A fixed reference size would crop corners when
the picker later measures the preview at another size. Previews keep the resource
radius and follow their actual parent bounds; they do not promise the placed
widget's icon-size-dependent radius or portrait height.

The inspected OPPO framework's `OplusGradientDrawableExtImpl` accepts a
`smoothG2-weight` child with `android:gradientRadius="3.0"`. It selects the native
G2 drawable path with weight 3, matching the ordinary `TitleCardView` mask's
curve parameter. This is an OEM XML extension, not a public Android guarantee.
The branch does not use private reflection or copy the native curve algorithm.

For native one-row portrait cards, `IAreaWidget` / `SizeSpacingConfig` subtract
the cell padding until the visible height is bounded by the final launcher icon
size. The ColorOS dial root therefore uses `min(hostHeightDp, iconDp)` in portrait.
The outer 10000dp corner radius is a saturation parameter that makes a capsule
at its actual bounds, not an OEM radius constant. Landscape retains the existing
host height because the native landscape padding uses a separate source branch.

ColorOS responsive one-row variants retain both orientation-specific geometries
in a normal LinearLayout wrapper. A nested landscape/portrait RemoteViews pair
uses copies of the same rendered dial content; only the appearance geometry is
applied with each orientation. The wrapper centers the compact child without
changing the shared dial layout. `RemoteViews.addView` selects the nested pair
using the inflated wrapper's Context during synchronous and asynchronous
application, including reapply. A cached size map selects the new geometry when
that Context's Resources reflect the new orientation, or when the launcher
inflates a new wrapper. Passing a new Context to `reapply` alone does not update
the existing wrapper's Context.

The size-map child must remain a normal RemoteViews layout. In the inspected
framework and AOSP, only normal-layout Parcel records carry the child's ideal
size. Putting a landscape/portrait pair directly in the size map creates an
in-memory object, but loses its ideal size during Binder serialization and fails
when the map is read back. The ordinary wrapper keeps that size metadata and
stores the orientation pair in an add-view action, whose nested RemoteViews does
not require ideal-size metadata. Other launchers and non-dial variants retain
their existing responsive layouts.

## Platform boundary and validation

The standard [RemoteViews layout-size APIs](https://developer.android.com/reference/android/widget/RemoteViews#setViewLayoutHeight(int,%20float,%20int))
set dimensions on the inflated view. The
[AOSP AppWidgetHostView.prepareView implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/appwidget/AppWidgetHostView.java)
preserves the requested dimensions and centers the root. The inspected ColorOS
override delegates to it. Scale and pivot setters are `@RemotableViewMethod` in
the inspected framework.

Each apply resets root height, background layout size, scale, and pivot before
selecting the ColorOS geometry. Once the correct orientation leaf is selected,
its actions restore the intended dimensions on reapply. MATCH_PARENT uses pixel
units; concrete sizes use dp. Missing, inaccessible, non-finite, or non-positive
icon metadata keeps the unsized background geometry. Unrepresentable dimensions below one pixel
also keep that geometry. No settings values are written or logged.

Source and unit tests can establish action application, rounding, reapply, and
foreground isolation. Reading the metadata as the app, OEM G2 rendering,
weighted dial content at the compact height, and final launcher clipping require
device verification. An identical native card outline is unverified until that
host result is checked. Other OEM versions and launchers do not inherit this
source evidence.

Responsive orientation checks must Parcel-round-trip the entire size map and
apply it through the actual AppWidgetHostView or its nested add-view owning
action. Calling a compound size-map RemoteViews directly with `apply` selects
only one layer and is not evidence for AppWidgetHostView's extra selection step.

The current cached-orientation unit check updates the existing wrapper's
Resources before reapply. It proves nested selection and geometry under that
condition, not the launcher's configuration lifecycle. Cross-package inflation
in the inspected OPPO framework creates a resource Context with a complete copy
of the host Configuration. ResourcesManager applies that override after global
configuration updates, so its old orientation can remain until the resources
are updated or the view is reinflated. The inspected launcher also rebinds the
workspace when configuration changes include its screen-size/density mask
`0x1400`: `onIdpChanged` reaches `rebindCallbacks`, then `startBinding` clears
the old host views before creating new ones. That path reinflates the widget
with the current configuration, but it does not establish that every rotation
triggers rebinding. Cached portrait to landscape behavior on the actual ColorOS
host remains unverified until checked without a provider refresh or manually
changing wrapper Resources.
