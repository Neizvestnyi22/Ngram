package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;

/**
 * A sheet of "name — value" lines: details of a message, of a person, statistics of a chat.
 * Tapping a line copies its value; the button under the list copies all of them.
 */
public final class InfoSheet {

    /** The lines of a sheet, in the order they are added. Empty values are left out. */
    public static final class Rows {
        final ArrayList<String[]> items = new ArrayList<>();

        public Rows add(String label, String value) {
            if (!TextUtils.isEmpty(value)) {
                items.add(new String[]{label, value});
            }
            return this;
        }

        public Rows add(String label, long value) {
            return add(label, Long.toString(value));
        }

        /** A heading between groups of lines. */
        public Rows section(String title) {
            items.add(new String[]{title, null});
            return this;
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }

        /** Everything as plain text, the way "copy all" hands it over. */
        public String text() {
            final StringBuilder sb = new StringBuilder();
            for (String[] item : items) {
                if (item[1] == null) {
                    sb.append('\n').append(item[0]).append('\n');
                } else {
                    sb.append(item[0]).append(": ").append(item[1]).append('\n');
                }
            }
            return sb.toString().trim();
        }
    }

    private InfoSheet() {
    }

    public static void show(BaseFragment fragment, String title, Rows rows) {
        show(fragment, title, rows, null, null);
    }

    /**
     * @param actionTitle when not null, a second button next to "copy all" that runs {@code action}
     */
    public static void show(BaseFragment fragment, String title, Rows rows, String actionTitle, Runnable action) {
        final Context context = fragment.getContext();
        if (context == null) {
            return;
        }
        final Theme.ResourcesProvider rp = fragment.getResourceProvider();
        final BottomSheet sheet = new BottomSheet(context, false, rp);
        sheet.fixNavigationBar();

        final int textColor = Theme.getColor(Theme.key_dialogTextBlack, rp);
        final int greyColor = Theme.getColor(Theme.key_dialogTextGray2, rp);
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton, rp);

        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        final TextView heading = new TextView(context);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        heading.setTypeface(AndroidUtilities.bold());
        heading.setTextColor(textColor);
        heading.setText(title);
        column.addView(heading, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 18, 22, 6));

        final ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((int) (AndroidUtilities.displaySize.y * 0.6f), MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, dp(6));

        for (String[] item : rows.items) {
            if (item[1] == null) {
                final TextView section = new TextView(context);
                section.setText(item[0]);
                section.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                section.setTypeface(AndroidUtilities.bold());
                section.setTextColor(accent);
                list.addView(section, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 12, 22, 2));
                continue;
            }
            final LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(7), dp(14), dp(7));
            row.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(textColor, 0.08f), Theme.RIPPLE_MASK_ROUNDRECT_6DP));

            final TextView label = new TextView(context);
            label.setText(item[0]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            label.setTextColor(greyColor);
            row.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            final TextView value = new TextView(context);
            value.setText(item[1]);
            value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            value.setTextColor(textColor);
            value.setMaxLines(6);
            value.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(value, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

            final String copied = item[1];
            row.setOnClickListener(v -> copy(context, copied));
            list.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 0, 8, 0));
        }
        scroll.addView(list, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        final TextView copyAll = button(context, rp, tr("Скопировать всё", "Copy All"), action == null);
        buttons.addView(copyAll, LayoutHelper.createLinear(0, 46, 1f, 0, 0, action == null ? 0 : 8, 0));
        copyAll.setOnClickListener(v -> {
            copy(context, rows.text());
            sheet.dismiss();
        });
        if (action != null) {
            final TextView act = button(context, rp, actionTitle, true);
            buttons.addView(act, LayoutHelper.createLinear(0, 46, 1f));
            act.setOnClickListener(v -> {
                sheet.dismiss();
                action.run();
            });
        }
        column.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 16, 6, 16, 14));

        sheet.setCustomView(column);
        fragment.showDialog(sheet);
    }

    private static void copy(Context context, String text) {
        // Android 13 and newer show their own "copied" note; older versions get ours.
        if (AndroidUtilities.addToClipboard(text) && AndroidUtilities.shouldShowClipboardToast()) {
            Toast.makeText(context, tr("Скопировано", "Copied"), Toast.LENGTH_SHORT).show();
        }
    }

    public static TextView button(Context context, Theme.ResourcesProvider rp, String text, boolean filled) {
        final TextView view = new TextView(context);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setTypeface(AndroidUtilities.bold());
        view.setSingleLine(true);
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton, rp);
        if (filled) {
            view.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, rp));
            view.setBackground(Theme.createRoundRectDrawable(dp(23), accent));
        } else {
            view.setTextColor(accent);
            view.setBackground(Theme.createRoundRectDrawable(dp(23), Theme.multAlpha(accent, 0.12f)));
        }
        ScaleStateListAnimator.apply(view);
        return view;
    }
}
