package me.pipi.codexmeter;

import dev.bennett.codexmeter.WidgetMeters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.xmlpull.v1.XmlPullParser;

/** Widget previews use synthetic fixtures and render the production RemoteViews. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UsageCardPreviewTest {
    @Test
    @Config(qualifiers = "zh-rCN-xhdpi")
    public void pickerShowsTwoPopulatedClearRows() throws Exception {
        renderPickerPreview("picker-light");
    }

    @Test
    @Config(qualifiers = "zh-rCN-night-xhdpi")
    public void pickerUsesTheClearNightPalette() throws Exception {
        renderPickerPreview("picker-dark");
    }

    private static void renderPickerPreview(String name) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        int layout;
        try (XmlResourceParser metadata = context.getResources().getXml(R.xml.codex_widget_info)) {
            while (metadata.next() != XmlPullParser.START_TAG) {
            }
            layout = metadata.getAttributeResourceValue(
                    "http://schemas.android.com/apk/res/android", "previewLayout", 0);
        }
        View view = apply(context, new RemoteViews(context.getPackageName(), layout));
        assertEquals("Plus", ((TextView) view.findViewById(R.id.md_title)).getText().toString());
        assertEquals("会话", ((TextView) view.findViewById(R.id.md_name_0)).getText().toString());
        assertEquals("每周", ((TextView) view.findViewById(R.id.md_name_2)).getText().toString());
        assertEquals("64%", ((TextView) view.findViewById(R.id.md_value_0)).getText().toString());
        assertEquals("42%", ((TextView) view.findViewById(R.id.md_value_2)).getText().toString());
        assertEquals("重置时间：10/03 15:24",
                ((TextView) view.findViewById(R.id.md_reset_0)).getText().toString());
        assertEquals("重置时间：10/05 12:00",
                ((TextView) view.findViewById(R.id.md_reset_2)).getText().toString());
        assertEquals(View.GONE, view.findViewById(R.id.md_panel_1).getVisibility());
        assertEquals(View.GONE, view.findViewById(R.id.md_panel_3).getVisibility());
        assertEquals(6400, ((ImageView) view.findViewById(R.id.md_fill_0)).getDrawable().getLevel());
        assertEquals(4200, ((ImageView) view.findViewById(R.id.md_fill_2)).getDrawable().getLevel());
        assertEquals(context.getColor(R.color.widget_material_text),
                ((TextView) view.findViewById(R.id.md_name_0)).getCurrentTextColor());
        View production = apply(context, WidgetRenderer.build(context, 1,
                WidgetOptions.defaults().withVisibleMeters("five_hour,weekly"),
                UsageCardFixtures.state(UsageCardFixtures.plus(), 0), 140f, 200f, null));
        // XML dimensions round to pixels; RemoteViews can retain fractional text sizes.
        for (int id : new int[] {R.id.md_title, R.id.md_updated, R.id.md_name_0, R.id.md_name_2}) {
            assertEquals("Picker typography follows the narrow card",
                    ((TextView) production.findViewById(id)).getTextSize(),
                    ((TextView) view.findViewById(id)).getTextSize(), 0.5f);
        }
        for (int id : new int[] {R.id.md_bar_0, R.id.md_detail_0, R.id.md_bar_2, R.id.md_detail_2}) {
            assertEquals("Picker row spacing follows the installed card",
                    ((ViewGroup.MarginLayoutParams) production.findViewById(id).getLayoutParams()).topMargin,
                    ((ViewGroup.MarginLayoutParams) view.findViewById(id).getLayoutParams()).topMargin);
        }
        PreviewSheet sheet = new PreviewSheet(name);
        float density = context.getResources().getDisplayMetrics().density;
        sheet.add(name + " square", view, px(170, density), px(170, density));
        sheet.add(name + " tall", view, px(140, density), px(200, density));
        assertTrue(sheet.writeSheet().isFile());
    }

    @Test
    @Config(qualifiers = "zh-rCN-xhdpi")
    public void rendersChineseLightMaterialCards() throws Exception {
        renderMaterialSheet("material-zh-light", 100);
    }

    @Test
    @Config(qualifiers = "zh-rCN-night-xhdpi")
    public void rendersChineseDarkMaterialCards() throws Exception {
        renderMaterialSheet("material-zh-dark", 100);
    }

    @Test
    @Config(qualifiers = "en-rUS-xhdpi")
    public void rendersEnglishMaterialCards() throws Exception {
        renderMaterialSheet("material-en-light", 100);
    }

    @Test
    @Config(qualifiers = "zh-rCN-xhdpi")
    public void rendersTransparentMaterialCards() throws Exception {
        renderMaterialSheet("material-zh-transparent", 0);
    }

    @Test
    @Config(qualifiers = "zh-rCN-xhdpi")
    public void rendersOneRowDials() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        PreviewSheet sheet = new PreviewSheet("dials");
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), 2);
        WidgetOptions options = WidgetOptions.defaults();
        float density = context.getResources().getDisplayMetrics().density;
        RemoteViews two = WidgetRenderer.build(context, 1, options, state, 180f, 90f, null);
        assertEquals(R.layout.widget_rings, two.getLayoutId());
        sheet.add("2x1 dials", apply(context, two), px(180, density), px(90, density));
        RemoteViews four = WidgetRenderer.build(context, 1, options, state, 260f, 90f, null);
        assertEquals(R.layout.widget_rings, four.getLayoutId());
        assertTrue(sheet.writeSheet().isFile());
    }

    @Test
    public void samsungCellSpansOverrideContentDimensions() {
        Context context = RuntimeEnvironment.getApplication();
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), 2);
        WidgetOptions options = WidgetOptions.defaults();
        Bundle host = new Bundle();
        host.putInt("semAppWidgetRowSpan", 1);
        host.putInt("semAppWidgetColumnSpan", 3);
        assertEquals(R.layout.widget_rings,
                WidgetRenderer.build(context, 1, options, state, 220f, 90f, host).getLayoutId());
        host.putInt("semAppWidgetColumnSpan", 2);
        assertEquals(R.layout.widget_rings,
                WidgetRenderer.build(context, 1, options, state, 260f, 90f, host).getLayoutId());
        host.putInt("semAppWidgetRowSpan", 2);
        assertEquals(R.layout.widget_material,
                WidgetRenderer.build(context, 1, options, state, 260f, 90f, host).getLayoutId());
    }

    @Test
    public void oneRowBackgroundUsesTheClearSystemPalette() {
        assertDialSurfaceColor();
    }

    @Test
    @Config(qualifiers = "night")
    public void oneRowBackgroundUsesTheClearNightPalette() {
        assertDialSurfaceColor();
    }

    @Test
    public void oneRowBackgroundHonorsEnabledOpacityLevelsAndOff() {
        Context context = RuntimeEnvironment.getApplication();
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), 2);
        int[] opacity = {56, 88, 100, 0};
        int[] alpha = {143, 224, 255, 0};
        for (int index = 0; index < opacity.length; index++) {
            WidgetOptions options = new WidgetOptions("auto", "system", "app", opacity[index],
                    "hidden", "remaining").withVisibleMeters(
                            WidgetMeters.serialize(WidgetMeters.defaultVisible()));
            for (int width : new int[] {180, 260}) {
                assertEquals(alpha[index], Color.alpha(dialBackgroundPixel(context, state,
                        options, width)));
            }
        }
    }

    @Test
    public void singleUsageKeepsItsCompactAlignedTitleValueRow() {
        Context context = RuntimeEnvironment.getApplication();
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), 2);
        WidgetOptions options = WidgetOptions.defaults().withVisibleMeters(WidgetMeters.WEEKLY);
        View view = apply(context, WidgetRenderer.build(context, 1, options, state,
                140f, 200f, null));
        TextView title = view.findViewById(R.id.md_name_0);
        TextView value = view.findViewById(R.id.md_value_0);
        assertEquals(LinearLayout.HORIZONTAL, ((LinearLayout) title.getParent()).getOrientation());
        assertEquals(title.getBaseline(), value.getBaseline(), 2);
    }

    private static void assertDialSurfaceColor() {
        Context context = RuntimeEnvironment.getApplication();
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), 2);
        WidgetOptions options = WidgetOptions.defaults();
        for (int width : new int[] {180, 260}) {
            assertEquals(context.getColor(R.color.widget_material_surface),
                    dialBackgroundPixel(context, state, options, width));
        }
    }

    private static int dialBackgroundPixel(Context context, UsageCardState state,
            WidgetOptions options, int width) {
        View view = apply(context, WidgetRenderer.build(context, 1, options, state,
                width, 90f, null));
        float density = context.getResources().getDisplayMetrics().density;
        int widthPx = px(width, density);
        int heightPx = px(90, density);
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, widthPx, heightPx);
        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        int pixel = bitmap.getPixel(widthPx / 2, px(4, density));
        bitmap.recycle();
        return pixel;
    }

    private void renderMaterialSheet(String name, int opacity) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        PreviewSheet sheet = new PreviewSheet(name);
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), 2);
        WidgetOptions options = new WidgetOptions("auto", "system", "app", opacity,
                "hidden", "remaining");
        List<String> one = UsageCardFixtures.keys(WidgetMeters.WEEKLY);
        List<String> two = UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY);
        List<String> three = UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY,
                WidgetMeters.NEXT_RESET);
        List<String> four = WidgetMeters.defaultVisible();
        add(sheet, context, "single", options, one, state, 170, 170);
        add(sheet, context, "single narrow tall", options, one, state, 140, 200);
        add(sheet, context, "single wide short", options, one, state, 350, 140);
        add(sheet, context, "dual 2x2", options, two, state, 170, 170);
        add(sheet, context, "dual tall", options, two, state, 180, 200);
        add(sheet, context, "wide dual", options, two, state, 350, 170);
        add(sheet, context, "wide triple", options, three, state, 350, 170);
        add(sheet, context, "wide quad", options, four, state, 350, 170);
        add(sheet, context, "wide quad tall", options, four, state, 330, 230);
        add(sheet, context, "signed out", options, two, UsageCardFixtures.signedOut(), 170, 170);
        assertTrue(sheet.writeSheet().isFile());
    }

    private static void add(PreviewSheet sheet, Context context, String label,
            WidgetOptions options, List<String> keys, UsageCardState state, int widthDp,
            int heightDp) throws Exception {
        float density = context.getResources().getDisplayMetrics().density;
        WidgetOptions selected = options.withVisibleMeters(WidgetMeters.serialize(keys));
        RemoteViews views = WidgetRenderer.build(context, 1, selected, state, widthDp,
                heightDp, null);
        assertEquals(options.opacity <= 0 ? R.layout.widget_material_shadow
                : R.layout.widget_material, views.getLayoutId());
        sheet.add(label, apply(context, views), px(widthDp, density), px(heightDp, density));
    }

    private static View apply(Context context, RemoteViews views) {
        return views.apply(context, new FrameLayout(context));
    }

    private static int px(int dp, float density) {
        return Math.round(dp * density);
    }
}
