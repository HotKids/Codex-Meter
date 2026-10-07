package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.view.ContextThemeWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReleaseNotesUiTest {
    private static final String LONG_ITEM = "小组件内部卡片与进度条跟随不透明度设置，"
            + "长条目换行后仍与正文起点对齐，并保留全部说明内容。";
    private static final String NOTES = "## 0.3.1 — 2026-10-05\n\n"
            + "- 优化安装包压缩，减小下载体积。\n\n"
            + "## 0.3 — 2026-10-05\n\n"
            + "- 新增自动、原生与经典小组件配色。\n"
            + "- " + LONG_ITEM + "\n"
            + "- 查看 [详情](https://example.com/notes)。";

    @Test
    public void versionHeadingsHaveNativeHierarchyAndSeparateTheirLists() throws Exception {
        TextView view = ReleaseNotesUi.create(context(), NOTES, false);
        Spanned text = (Spanned) view.getText();
        RelativeSizeSpan[] headings = text.getSpans(0, text.length(), RelativeSizeSpan.class);
        assertEquals("Both published version headings must retain their hierarchy", 2, headings.length);
        for (RelativeSizeSpan heading : headings) {
            assertTrue(heading.getSizeChange() > 1f);
            int start = text.getSpanStart(heading);
            assertEquals("Headings must never inherit a preceding list marker", 0,
                    text.getSpans(start, start + 1, BulletSpan.class).length);
        }
        assertTrue(text.toString().contains("减小下载体积。\n\n0.3 —"));
        assertFalse("Trailing HTML paragraph breaks must not add empty card space",
                text.toString().endsWith("\n"));
        LinearLayout card = Ui.card(view.getContext(), false);
        card.addView(view);
        measure(card, Ui.dp(view.getContext(), 360));
        PreviewSheet sheet = new PreviewSheet("release-notes-light");
        sheet.add("360dp-light", card, card.getMeasuredWidth(), card.getMeasuredHeight());
        sheet.writeSheet();
    }

    @Test
    @Config(qualifiers = "zh-rCN-xxhdpi")
    public void nativeBulletsUseDensityAwareHangingIndentAcrossWrappedLines() {
        Context context = context();
        TextView view = ReleaseNotesUi.create(context, NOTES, false);
        measure(view, Ui.dp(context, 256));
        Spanned text = (Spanned) view.getText();
        BulletSpan[] bullets = text.getSpans(0, text.length(), BulletSpan.class);
        assertEquals(4, bullets.length);
        for (BulletSpan bullet : bullets) {
            assertTrue("The bullet marker must leave a readable gap at every density",
                    bullet.getLeadingMargin(true) >= Ui.dp(context, 12));
        }
        int start = text.toString().indexOf(LONG_ITEM);
        int first = view.getLayout().getLineForOffset(start);
        int last = view.getLayout().getLineForOffset(start + LONG_ITEM.length() - 1);
        assertTrue("The fixture must exercise actual wrapping", last > first);
        for (int line = first + 1; line <= last; line++) {
            assertEquals("Wrapped lines must align with the bullet body",
                    view.getLayout().getLineLeft(first), view.getLayout().getLineLeft(line), 0.5f);
        }
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        boolean visibleMarker = false;
        for (int y = view.getLayout().getLineTop(first);
                y < view.getLayout().getLineBottom(first); y++) {
            for (int x = 0; x < Ui.dp(context, 4); x++) {
                visibleMarker |= (bitmap.getPixel(x, y) >>> 24) != 0;
            }
        }
        assertTrue("The list marker must actually paint inside its reserved margin", visibleMarker);
    }

    @Test
    public void linksAndEscapedContentSurviveFormattingAndEmptyReapply() {
        TextView view = ReleaseNotesUi.create(context(),
                "## 当前版本 & <测试>\n\n- **保留重点** 与 [详情](https://example.com/notes)", false);
        Spanned text = (Spanned) view.getText();
        assertTrue(text.toString().contains("当前版本 & <测试>"));
        assertTrue(text.toString().contains("保留重点"));
        URLSpan[] links = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals(1, links.length);
        assertEquals("https://example.com/notes", links[0].getURL());
        assertTrue(view.getMovementMethod() instanceof LinkMovementMethod);
        ReleaseNotesUi.apply(view, "");
        assertEquals("", view.getText().toString());
    }

    @Test
    public void narrowCardsGrowWithLargeText() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        PreviewSheet sheet = new PreviewSheet("release-notes-large-text");
        for (boolean dark : new boolean[] {false, true}) {
            Configuration configuration = new Configuration(app.getResources().getConfiguration());
            configuration.fontScale = 2f;
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            Context context = new ContextThemeWrapper(app.createConfigurationContext(configuration),
                    R.style.AppTheme);
            TextView notes = ReleaseNotesUi.create(context, NOTES, dark);
            LinearLayout card = Ui.card(context, dark);
            card.addView(notes);
            measure(card, Ui.dp(context, 300));
            assertTrue(notes.getLayout().getHeight() <= notes.getHeight()
                    - notes.getCompoundPaddingTop() - notes.getCompoundPaddingBottom());
            for (int line = 0; line < notes.getLayout().getLineCount(); line++) {
                assertEquals(0, notes.getLayout().getEllipsisCount(line));
                assertTrue(notes.getLayout().getLineRight(line) <= notes.getWidth() + 1);
            }
            sheet.add("300dp-200-percent-" + (dark ? "dark" : "light"), card,
                    Ui.dp(context, 300), card.getMeasuredHeight());
        }
        sheet.writeSheet();
    }

    private static Context context() {
        return new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme);
    }

    private static void measure(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, width, view.getMeasuredHeight());
    }
}
