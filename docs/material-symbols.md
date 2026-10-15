# Phone icons

Application action and information icons use Material Symbols Outlined, with fill 0,
weight 400, grade 0 and optical size 24. The Android VectorDrawables are converted
from Google source commit `737e3324305806514d7909874fa1818ae1808232`:
https://github.com/google/material-design-icons/tree/737e3324305806514d7909874fa1818ae1808232/symbols/web

Widget cards use `ic_ms_sync.xml` for their refresh action and replace it with
`ic_ms_sync_problem.xml` when a refresh fails. Both use the same Outlined parameters
and source commit:

- [`sync_24px.svg`](https://github.com/google/material-design-icons/blob/737e3324305806514d7909874fa1818ae1808232/symbols/web/sync/materialsymbolsoutlined/sync_24px.svg)
- [`sync_problem_24px.svg`](https://github.com/google/material-design-icons/blob/737e3324305806514d7909874fa1818ae1808232/symbols/web/sync_problem/materialsymbolsoutlined/sync_problem_24px.svg)

In the translated 960-unit viewport, Sync spans x=160–800 and y=160–800;
Sync Problem spans x=120–840 and y=160–800. Both are centered at (480, 480).
The shared card renderer shifts each glyph by its horizontal inset so the visible
end aligns with the content panel. Equal and opposite padding retains the same
fitCenter scale and click target; the shift is mirrored in RTL layouts.

The default tint follows primary text in light and dark themes. Explicitly disabled
controls keep their disabled state.

Live-notification actions use notification-specific white `stop_circle` and `sync`
vectors without a theme tint. Both retain a 24dp canvas; Sync is scaled by 1.25
around the center to match the stop-circle's 20dp visible bounds. Their system
button containers and the app/widget icon resources remain unchanged. The stop
icon comes from the same Google source commit:

- [`stop_circle_24px.svg`](https://github.com/google/material-design-icons/blob/737e3324305806514d7909874fa1818ae1808232/symbols/web/stop_circle/materialsymbolsoutlined/stop_circle_24px.svg)

The sign-in foreground notification uses white `notifications` and `close` vectors
without a theme tint. The notification path matches `ic_ms_notifications`; the
cancel action comes from the same Google source commit:

- [`close_24px.svg`](https://github.com/google/material-design-icons/blob/737e3324305806514d7909874fa1818ae1808232/symbols/web/close/materialsymbolsoutlined/close_24px.svg)

History selection uses `check_circle`. Desktop and lock-screen dial glyphs use
`schedule`, `calendar_today` and `alarm`, including their editor and picker
previews. Static lock-screen picker glyphs retain the previous light/dark
colors (`#FF010101` / `#FFFAFAFA`); runtime lock-screen glyphs retain their
explicit foreground tint. Reset reminders use a white `alarm` vector without a theme tint;
reset-credit expiry notifications and actions use a white `restart_alt` vector.
These paths come from the same Google source commit. The dial geometry, progress
graphics and system control indicators retain the existing One UI implementation.

The source SVG viewport starts at y=-960; the vector group translates it by 960.
The local `ic_ms_*` names isolate app UI replacements from upstream widget and
notification graphics. Brand marks, branded notification small icons, contributor avatars,
the launcher and native control indicators retain their own identities. No runtime
icon font is loaded.

See [Apache-2.0 license](Material-Symbols-LICENSE.txt).
