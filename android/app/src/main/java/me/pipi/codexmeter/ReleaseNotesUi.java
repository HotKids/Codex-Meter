package me.pipi.codexmeter;

import android.content.Context;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.TextAppearanceSpan;
import android.widget.TextView;

/** Shared TextView wiring for rendered GitHub release notes. */
public final class ReleaseNotesUi {
    private ReleaseNotesUi() {
    }

    public static TextView create(Context context, String markdown, boolean dark) {
        TextView view = Ui.text(context, "", 14, Ui.mainText(context, dark));
        view.setLineSpacing(Ui.dp(context, 3), 1.12f);
        apply(view, markdown);
        return view;
    }

    public static void apply(TextView view, String markdown) {
        String html = ReleaseNotesMarkdown.toHtml(markdown);
        if (html.isEmpty()) {
            view.setText("");
            return;
        }
        // Separate version sections while keeping consecutive list items together.
        SpannableStringBuilder text = new SpannableStringBuilder(Html.fromHtml(html,
                Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM));
        float headingScale = new TextAppearanceSpan(view.getContext(),
                androidx.appcompat.R.style.TextAppearance_AppCompat_Subhead).getTextSize()
                / view.getTextSize();
        for (RelativeSizeSpan span : text.getSpans(0, text.length(), RelativeSizeSpan.class)) {
            text.setSpan(new RelativeSizeSpan(headingScale), text.getSpanStart(span),
                    text.getSpanEnd(span), text.getSpanFlags(span));
            text.removeSpan(span);
        }
        for (BulletSpan span : text.getSpans(0, text.length(), BulletSpan.class)) {
            text.setSpan(new BulletSpan(Ui.dp(view.getContext(), 8), view.getCurrentTextColor(),
                            Ui.dp(view.getContext(), 2)),
                    text.getSpanStart(span), text.getSpanEnd(span), text.getSpanFlags(span));
            text.removeSpan(span);
        }
        while (text.length() > 0 && text.charAt(text.length() - 1) == '\n') {
            text.delete(text.length() - 1, text.length());
        }
        view.setText(text);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setLinksClickable(true);
    }
}
