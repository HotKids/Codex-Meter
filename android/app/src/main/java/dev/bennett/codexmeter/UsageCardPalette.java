package dev.bennett.codexmeter;

import android.graphics.Color;

/**
 * Card colours from AI-Usage's widget theme (iOS 26 system colours). Translucent colours are
 * split into an opaque colour plus an alpha because RemoteViews tints images with
 * {@code setColorFilter}, which only blends correctly with opaque colours.
 */
final class UsageCardPalette {
    final boolean dark;
    final int background;
    final int primary;
    final int secondary;
    final int track;
    final int trackAlpha;
    final int border;
    final int watermark;
    final int green;
    final int orange;
    final int red;

    /** Shared by light and dark so one alpha works with day/night colour pairs. */
    static final int BORDER_ALPHA = 0x16;
    static final int WATERMARK_ALPHA = 0x16;
    /** Translucent black scrim AI-Usage draws behind transparent widgets (15%). */
    static final int SCRIM_ALPHA = 0x26;

    static final UsageCardPalette LIGHT = new UsageCardPalette(false, Color.WHITE, Color.BLACK,
            0x993C3C43, 0xFFC7C8CC, 0xFF, Color.BLACK, 0xFF232326,
            0xFF34C759, 0xFFFF8D28, 0xFFFF383C);
    static final UsageCardPalette DARK = new UsageCardPalette(true, 0xFF1C1C1E, Color.WHITE,
            0x99EBEBF5, 0xFF55565C, 0xFF, Color.WHITE, 0xFFF5F5F7,
            0xFF30D158, 0xFFFF9230, 0xFFFF4245);
    /** Background turned off: white copy on a light scrim over the wallpaper. */
    static final UsageCardPalette TRANSPARENT = new UsageCardPalette(true, Color.BLACK,
            Color.WHITE, 0xE0FFFFFF, Color.WHITE, 0x29, Color.WHITE, Color.WHITE,
            0xFF30D158, 0xFFFF9F0A, 0xFFFF453A);

    private UsageCardPalette(boolean dark, int background, int primary, int secondary, int track,
            int trackAlpha, int border, int watermark, int green, int orange, int red) {
        this.dark = dark;
        this.background = background;
        this.primary = primary;
        this.secondary = secondary;
        this.track = track;
        this.trackAlpha = trackAlpha;
        this.border = border;
        this.watermark = watermark;
        this.green = green;
        this.orange = orange;
        this.red = red;
    }

    /** Warning colour for error lines. */
    int warn() {
        return orange;
    }

    /**
     * Bar colour by consumption, as AI-Usage colours it: red at 85% used (15% left), orange at
     * 60% used (40% left), otherwise green.
     */
    int severity(UsageWindow window) {
        if (window == null) {
            return green;
        }
        if (window.usedPercent >= 85) {
            return red;
        }
        if (window.usedPercent >= 60) {
            return orange;
        }
        return green;
    }
}
