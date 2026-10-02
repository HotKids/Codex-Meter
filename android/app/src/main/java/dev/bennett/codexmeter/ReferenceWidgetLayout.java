package dev.bennett.codexmeter;

/** Coordinates from AI-Usage SmallLayouts.tsx / MediumLayouts.tsx (MIT).
 * The source canvas is 158pt high. Only undersized Android hosts scale it down;
 * small widgets keep the source's vertical centering, medium widgets stay top-aligned.
 */
final class ReferenceWidgetLayout {
    final boolean wide, single, stacked;
    final int count;
    final float scale, offset, padding;
    final float badge, firstTitle, secondTitle, value, progress, reset, optional, fetched;
    final float titleSize, valueSize, detailSize, metaSize, progressHeight;

    ReferenceWidgetLayout(int width, int height, int count, boolean hasOptional) {
        this.wide = width >= 240;
        this.count = count;
        this.single = count <= 1;
        stacked = wide && count >= 3;
        scale = Math.min(1f, Math.min(height / 158f, width / (wide ? 240f : 114f)));
        offset = wide ? 0 : Math.max(0, (height - 158 * scale) / 2);
        padding = (wide ? 20 : 12) * scale;
        titleSize = (wide ? 16 : 14) * scale;
        valueSize = (single ? wide ? 40 : 32 : wide ? 14 : 12) * scale;
        detailSize = (wide ? 12 : 10) * scale;
        metaSize = (wide ? 10.5f : 10) * scale;
        progressHeight = (single ? 7 : stacked ? 5.5f : wide ? 6.5f : 6) * scale;
        if (single) {
            badge = y(wide ? hasOptional ? 9 : 10 : 11);
            firstTitle = y(wide ? hasOptional ? 36 : 39 : hasOptional ? 35 : 36);
            value = y(wide ? hasOptional ? 56 : 62 : hasOptional ? 53 : 55);
            progress = y(wide ? hasOptional ? 104 : 111 : hasOptional ? 92 : 96);
            reset = y(wide ? hasOptional ? 118 : 128 : hasOptional ? 106 : 112);
            optional = reset + (wide ? 16 : 15.5f) * scale;
            fetched = wide ? badge + 3.5f * scale
                    : hasOptional ? optional + 15.5f * scale : reset + 17 * scale;
            secondTitle = 0;
        } else if (stacked) {
            badge = y(count == 4 ? 9 : 18);
            firstTitle = y(count == 4 ? 32 : hasOptional ? 42 : 44);
            secondTitle = firstTitle + (count == 4 ? 31 : 34) * scale;
            value = firstTitle;
            progress = firstTitle + 19 * scale;
            fetched = badge + 4 * scale;
            reset = 0;
            optional = y(145);
        } else {
            badge = y(wide ? hasOptional ? 9 : 10 : hasOptional ? 8 : 11);
            firstTitle = y(wide ? hasOptional ? 34 : 38 : hasOptional ? 32 : 38);
            secondTitle = y(wide ? hasOptional ? 87 : 96 : hasOptional ? 79 : 89);
            value = firstTitle + (wide ? 20 : 18) * scale;
            progress = firstTitle + (wide ? 40 : 35) * scale;
            fetched = wide ? badge + 3.5f * scale : y(hasOptional ? 140 : 136);
            reset = 0;
            optional = y(wide ? 140 : 124.5f);
        }
    }

    float rowTop(int index) { return index == 0 ? firstTitle : stacked ? firstTitle + index * (count == 4 ? 31 : 34) * scale : secondTitle; }

    float y(float sourceY) { return sourceY * scale + offset; }
}
