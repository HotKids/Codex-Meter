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

The source SVG viewport starts at y=-960; the vector group translates it by 960.
The local `ic_ms_*` names isolate app UI replacements from upstream widget and
notification graphics. Brand marks, contributor avatars, the launcher and native
control indicators retain their own identities. No runtime icon font is loaded.

See [Apache-2.0 license](Material-Symbols-LICENSE.txt).
