package dev.bennett.codexmeter;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Pilot: proves RemoteViews render to PNG in CI, including CJK glyphs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetPreviewPipelineTest {
    @Test
    public void rendersUpstreamRingsAndCjkText() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        float density = context.getResources().getDisplayMetrics().density;
        PreviewSheet sheet = new PreviewSheet("pipeline");

        RemoteViews rings = new RemoteViews(context.getPackageName(), R.layout.widget_rings);
        View ringsView = rings.apply(context, new FrameLayout(context));
        sheet.add("upstream rings 2x1", ringsView, px(180, density), px(90, density));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Color.WHITE);
        for (String sample : new String[] {"每周 72% 剩余", "5 小时 2小时50分后重置", "Weekly 72% left"}) {
            TextView line = new TextView(context);
            line.setText(sample);
            line.setTextColor(Color.BLACK);
            line.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            column.addView(line);
        }
        sheet.add("cjk text", column, px(180, density), px(90, density));

        assertTrue(sheet.writeSheet().isFile());
    }

    private static int px(int dp, float density) {
        return Math.round(dp * density);
    }
}
