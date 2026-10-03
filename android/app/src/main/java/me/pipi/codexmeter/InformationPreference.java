package me.pipi.codexmeter;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/** Read-only information with the same title hierarchy as interactive settings. */
public final class InformationPreference extends Preference {
    public InformationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        // SESL otherwise gives nonselectable titles the summary color after inflation.
        TextView title = (TextView) holder.findViewById(android.R.id.title);
        TypedArray colors = getContext().obtainStyledAttributes(
                new int[] {android.R.attr.textColorPrimary});
        try {
            if (title != null) {
                title.setTextColor(colors.getColorStateList(0));
            }
        } finally {
            colors.recycle();
        }
    }
}
