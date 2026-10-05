package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.widget.Button;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.graphics.ColorUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OneUiThemeIntegrationTest {
    @Test
    @Config(qualifiers = "night")
    public void darkPagesUseOneUiSurfacesAndSemiboldTypography() {
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(),
                R.style.AppTheme);
        assertEquals(Color.rgb(1, 1, 2), Ui.background(context, true));
        assertEquals(Color.rgb(23, 23, 26), Ui.cardColor(context, true));
        assertEquals(600, Ui.mediumTypeface(context).getWeight());
        assertEquals(400, Ui.regularTypeface(context).getWeight());
    }

    @Test
    @Config(qualifiers = "night")
    public void dynamicPrimaryButtonsRetainThemeAccentAndReadableLabels() {
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(),
                R.style.AppTheme_MaterialYou);
        AppPreferences.setMaterialYouEnabled(context, true);
        int accent = context.getColor(android.R.color.system_accent1_200);
        for (Button button : new Button[]{Ui.nativePrimaryButton(context, "Continue"),
                Ui.button(context, "Continue", true, true)}) {
            TypedArray attributes = button.getContext().obtainStyledAttributes(
                    new int[]{androidx.appcompat.R.attr.colorButtonNormal});
            ColorStateList fill;
            try {
                fill = attributes.getColorStateList(0);
            } finally {
                attributes.recycle();
            }
            assertEquals(accent, fill.getColorForState(new int[]{android.R.attr.state_enabled}, 0));
            assertTrue(Color.alpha(fill.getColorForState(new int[]{-android.R.attr.state_enabled}, 0))
                    < Color.alpha(accent));
            assertTrue(ColorUtils.calculateContrast(button.getCurrentTextColor(), accent) >= 4.5);
            assertEquals(17f, button.getTextSize()
                    / context.getResources().getDisplayMetrics().scaledDensity, 0.01f);
        }
    }
}
