package dev.bennett.codexmeter;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Renders every widget layout to PNG under {@code build/reports/widget-previews} so the cards
 * can be reviewed without a device. The images use fixture data, not a live account.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UsageCardPreviewTest {
    private static final int SMALL = 170;
    private static final int MEDIUM_WIDTH = 350;
    private static final int CARD_HEIGHT = 170;

    @Test
    @Config(qualifiers = "zh-rCN-xhdpi")
    public void rendersChineseLightCards() throws Exception {
        renderSheet("cards-zh-light", 2);
    }

    @Test
    @Config(qualifiers = "zh-rCN-night-xhdpi")
    public void rendersChineseDarkCards() throws Exception {
        renderSheet("cards-zh-dark", 2);
    }

    @Test
    @Config(qualifiers = "en-rUS-xhdpi")
    public void rendersEnglishCardsWithoutCredits() throws Exception {
        renderSheet("cards-en-light", 0);
    }

    @Test
    @Config(qualifiers = "zh-rCN-xhdpi")
    public void rendersTransparentAndPlanStyles() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        PreviewSheet sheet = new PreviewSheet("cards-styles");
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.snapshot("pro", 72, 91,
                true), 1);
        List<String> two = UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY);
        add(sheet, context, "transparent small", style(0), two, state, SMALL, CARD_HEIGHT);
        add(sheet, context, "transparent medium", style(0), two, state, MEDIUM_WIDTH,
                CARD_HEIGHT);
        add(sheet, context, "88% team", style(88), two,
                UsageCardFixtures.state(UsageCardFixtures.snapshot("team", 2, 63, false), 0),
                SMALL, CARD_HEIGHT);
        add(sheet, context, "signed out", style(100), two, UsageCardFixtures.signedOut(),
                SMALL, CARD_HEIGHT);
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
        RemoteViews two = DialWidgetRenderer.build(context, 1, options,
                UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY), state, 180f);
        sheet.add("2x1 dials", apply(context, two), px(180, density), px(90, density));
        RemoteViews four = DialWidgetRenderer.build(context, 1, options,
                UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY,
                        UsageCardFixtures.SPARK_FIVE_HOUR, UsageCardFixtures.SPARK_WEEKLY),
                state, 360f);
        sheet.add("4x1 dials", apply(context, four), px(360, density), px(90, density));
        assertTrue(sheet.writeSheet().isFile());
    }

    private void renderSheet(String name, int credits) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        PreviewSheet sheet = new PreviewSheet(name);
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), credits);
        UsageCardRenderer.Style style = style(100);
        add(sheet, context, "S1 single", style, UsageCardFixtures.keys(WidgetMeters.WEEKLY),
                state, SMALL, CARD_HEIGHT);
        add(sheet, context, "S2 dual", style,
                UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY), state,
                SMALL, CARD_HEIGHT);
        add(sheet, context, "S2 short 150x140", style,
                UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY), state,
                150, 140);
        add(sheet, context, "M1 single", style, UsageCardFixtures.keys(WidgetMeters.WEEKLY),
                state, MEDIUM_WIDTH, CARD_HEIGHT);
        add(sheet, context, "M2 dual", style,
                UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY), state,
                MEDIUM_WIDTH, CARD_HEIGHT);
        add(sheet, context, "M3 triple", style,
                UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY,
                        UsageCardFixtures.SPARK_WEEKLY), state, MEDIUM_WIDTH, CARD_HEIGHT);
        add(sheet, context, "M4 quad", style,
                UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY,
                        UsageCardFixtures.SPARK_FIVE_HOUR, UsageCardFixtures.SPARK_WEEKLY),
                state, MEDIUM_WIDTH, CARD_HEIGHT);
        add(sheet, context, "M2 tall 330x230", style,
                UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY), state,
                330, 230);
        assertTrue(sheet.writeSheet().isFile());
    }

    private void renderMaterialSheet(String name, int opacity) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        PreviewSheet sheet = new PreviewSheet(name);
        UsageCardState state = UsageCardFixtures.state(UsageCardFixtures.plus(), 2);
        UsageCardRenderer.Style style = style(opacity);
        List<String> one = UsageCardFixtures.keys(WidgetMeters.WEEKLY);
        List<String> two = UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY);
        List<String> three = UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY,
                UsageCardFixtures.SPARK_WEEKLY);
        List<String> four = UsageCardFixtures.keys(WidgetMeters.FIVE_HOUR, WidgetMeters.WEEKLY,
                UsageCardFixtures.SPARK_FIVE_HOUR, UsageCardFixtures.SPARK_WEEKLY);
        addMaterial(sheet, context, "single", style, one, state, SMALL, CARD_HEIGHT);
        addMaterial(sheet, context, "dual", style, two, state, SMALL, CARD_HEIGHT);
        addMaterial(sheet, context, "dual tall 180x200", style, two, state, 180, 200);
        addMaterial(sheet, context, "wide dual", style, two, state, MEDIUM_WIDTH, CARD_HEIGHT);
        addMaterial(sheet, context, "wide triple", style, three, state, MEDIUM_WIDTH,
                CARD_HEIGHT);
        addMaterial(sheet, context, "wide quad", style, four, state, MEDIUM_WIDTH, CARD_HEIGHT);
        addMaterial(sheet, context, "wide quad tall 330x230", style, four, state, 330, 230);
        addMaterial(sheet, context, "signed out", style, two, UsageCardFixtures.signedOut(),
                SMALL, CARD_HEIGHT);
        assertTrue(sheet.writeSheet().isFile());
    }

    private static void addMaterial(PreviewSheet sheet, Context context, String label,
            UsageCardRenderer.Style style, List<String> keys, UsageCardState state, int widthDp,
            int heightDp) throws Exception {
        float density = context.getResources().getDisplayMetrics().density;
        RemoteViews views = MaterialCardRenderer.build(context, 1, style, keys, state, widthDp,
                heightDp);
        sheet.add(label, apply(context, views), px(widthDp, density), px(heightDp, density));
    }

    private static UsageCardRenderer.Style style(int opacity) {
        return new UsageCardRenderer.Style(WidgetOptions.THEME_SYSTEM, opacity);
    }

    private static void add(PreviewSheet sheet, Context context, String label,
            UsageCardRenderer.Style style, List<String> keys, UsageCardState state, int widthDp,
            int heightDp) throws Exception {
        float density = context.getResources().getDisplayMetrics().density;
        RemoteViews views = UsageCardRenderer.build(context, 1, style, keys, state, widthDp,
                heightDp);
        sheet.add(label, apply(context, views), px(widthDp, density), px(heightDp, density));
    }

    private static View apply(Context context, RemoteViews views) {
        return views.apply(context, new FrameLayout(context));
    }

    private static int px(int dp, float density) {
        return Math.round(dp * density);
    }
}
