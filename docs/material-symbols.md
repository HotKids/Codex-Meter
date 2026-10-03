# Phone icons

Application action and information icons use Material Symbols Outlined, with fill 0,
weight 400, grade 0 and optical size 24. The Android VectorDrawables are converted
from Google source commit `737e3324305806514d7909874fa1818ae1808232`:
https://github.com/google/material-design-icons/tree/737e3324305806514d7909874fa1818ae1808232/symbols/web

The One UI dependency row in About uses fill 1 through the separate
`ic_ms_widgets_filled.xml` asset, converted from
[`widgets_fill1_24px.svg`](https://github.com/google/material-design-icons/blob/737e3324305806514d7909874fa1818ae1808232/symbols/web/widgets/materialsymbolsoutlined/widgets_fill1_24px.svg)
at the same commit. Other uses of `ic_ms_widgets.xml` retain fill 0.

The default tint follows primary text in light and dark themes. Explicitly disabled
controls keep their disabled state.

The source SVG viewport starts at y=-960; the vector group translates it by 960.
The local `ic_ms_*` names isolate app UI replacements from upstream widget and
notification graphics. Brand marks, contributor avatars, the launcher and native
control indicators retain their own identities. No runtime icon font is loaded.

See [Apache-2.0 license](Material-Symbols-LICENSE.txt).
