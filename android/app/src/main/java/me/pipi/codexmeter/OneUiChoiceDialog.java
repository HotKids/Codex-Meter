package me.pipi.codexmeter;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import dev.oneuiproject.oneui.widget.RadioItemView;
import dev.oneuiproject.oneui.widget.RadioItemViewGroup;

/** OneUI-native single-choice dialog shared by app-widget configuration surfaces. */
final class OneUiChoiceDialog {
    interface OnChoiceSelected {
        void onSelected(int position);
    }

    private OneUiChoiceDialog() {
    }

    /**
     * Shows {@code labels} as radio rows with {@code selected} (clamped to the valid range)
     * checked. Picking a row reports its position and dismisses the dialog.
     */
    static void show(Context context, String title, String[] labels, int selected,
            OnChoiceSelected listener) {
        RadioItemViewGroup group = new RadioItemViewGroup(context);
        group.setOrientation(LinearLayout.VERTICAL);
        int[] rowIds = new int[labels.length];
        for (int index = 0; index < labels.length; index++) {
            RadioItemView row = new RadioItemView(context);
            rowIds[index] = View.generateViewId();
            row.setId(rowIds[index]);
            row.setTitle(labels[index]);
            row.setShowTopDivider(index > 0);
            group.addView(row);
        }
        if (rowIds.length > 0) {
            int checkedIndex = Math.max(0, Math.min(labels.length - 1, selected));
            group.check(rowIds[checkedIndex]);
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(group)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        group.setOnCheckedChangeListener((ignored, checkedId) -> {
            for (int index = 0; index < rowIds.length; index++) {
                if (rowIds[index] == checkedId) {
                    listener.onSelected(index);
                    dialog.dismiss();
                    return;
                }
            }
        });
        dialog.show();
    }
}
