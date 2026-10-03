# Launcher icon

The launcher uses a full-layer gradient adaptive background with a white
allowance dial and terminal mark. Its palette follows the user-supplied 1280 × 1280 Codex
icon reference `1-16603.png` from October 3, 2026:

| Gradient offset | Color | Original PNG pixel |
| --- | --- | --- |
| 0 | `#B0A7FF` | `(640, 170)` |
| 0.5 | `#7C9DFF` | `(640, 620)` |
| 1 | `#3D47FF` | `(640, 1090)` |

`ic_launcher_foreground.xml` owns the white dial and terminal geometry, enlarged
by 12% around the center while remaining inside the adaptive safe area.
`ic_launcher_background.xml` owns the gradient that fills the entire adaptive
background layer. The Android 13+ `monochrome` element references the same
`ic_launcher_foreground` resource directly, so themed icons use the identical
white mask without a second copy of the geometry.

`LauncherIconPreviewTest` renders the production adaptive resource to
`android/app/build/reports/launcher-icon-previews/CodexMeter.png`.
