package org.ngram.messenger.ui.ai;

import static org.ngram.messenger.N.tr;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.ngram.core.NgramMotion;
import org.ngram.messenger.ai.Ai;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * The assistant's panel: what it can do with the thing in front of it, laid out as tiles under
 * a quote of that thing, with a line for asking a question in your own words. It replaces a
 * plain list of names: the frequent actions are one tap away and it is clear what they act on.
 */
public final class AiPanel {

    /** One tile. A tile with a null action is a heading for the tiles after it. */
    public static final class Action {
        final int icon;
        final String title;
        final Runnable run;

        public Action(int icon, String title, Runnable run) {
            this.icon = icon;
            this.title = title;
            this.run = run;
        }

        public static Action heading(String title) {
            return new Action(0, title, null);
        }
    }

    private AiPanel() {
    }

    /**
     * @param quote   what the actions work on, shown at the top; may be empty
     * @param askHint the text of the question line, or null for no such line
     * @param onAsk   runs when the question line is tapped
     */
    public static void show(BaseFragment fragment, String quote, ArrayList<Action> actions, String askHint, Runnable onAsk) {
        final Context context = fragment.getContext();
        if (context == null) {
            return;
        }
        final Theme.ResourcesProvider rp = fragment.getResourceProvider();
        final BottomSheet sheet = new BottomSheet(context, false, rp);
        sheet.fixNavigationBar();

        final int textColor = Theme.getColor(Theme.key_dialogTextBlack, rp);
        final int gray = Theme.getColor(Theme.key_dialogTextGray2, rp);
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton, rp);

        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        // Title on the left, the provider and model in use on the right: a tap opens the settings.
        final FrameLayout header = new FrameLayout(context);
        final TextView heading = new TextView(context);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        heading.setTypeface(AndroidUtilities.bold());
        heading.setTextColor(textColor);
        heading.setText(tr("Ассистент", "Assistant"));
        header.addView(heading, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        final TextView model = new TextView(context);
        final Ai.Provider provider = Ai.active();
        model.setText(provider == null || !provider.ready() ? tr("не настроен", "not set up") : provider.model);
        model.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        model.setTextColor(accent);
        model.setSingleLine();
        model.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        model.setGravity(Gravity.CENTER);
        model.setPadding(dp(11), 0, dp(11), 0);
        model.setMaxWidth(dp(190));
        model.setBackground(Theme.createRoundRectDrawable(dp(14), Theme.multAlpha(accent, 0.12f)));
        NgramMotion.apply(model);
        model.setOnClickListener(v -> {
            sheet.dismiss();
            fragment.presentFragment(new AiSettingsFragment());
        });
        header.addView(model, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        column.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 34, 22, 16, 18, 6));

        if (!TextUtils.isEmpty(quote)) {
            final TextView quoted = new TextView(context);
            final String flat = quote.trim().replace('\n', ' ');
            quoted.setText(flat.length() > 220 ? flat.substring(0, 220) + "…" : flat);
            quoted.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
            quoted.setTextColor(gray);
            quoted.setMaxLines(3);
            quoted.setEllipsize(TextUtils.TruncateAt.END);
            quoted.setPadding(dp(14), dp(10), dp(14), dp(10));
            quoted.setBackground(Theme.createRoundRectDrawable(dp(14), Theme.multAlpha(textColor, 0.05f)));
            column.addView(quoted, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 4, 16, 6));
        }

        final ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((int) (AndroidUtilities.displaySize.y * 0.55f), MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(12), dp(2), dp(12), dp(4));

        LinearLayout row = null;
        for (Action action : actions) {
            if (action.run == null) {
                row = null;
                final TextView section = new TextView(context);
                section.setText(action.title);
                section.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                section.setTypeface(AndroidUtilities.bold());
                section.setTextColor(accent);
                grid.addView(section, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 10, 10, 10, 4));
                continue;
            }
            if (row == null || row.getChildCount() == 2) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, 0, 4, 0, 4));
            }
            row.addView(tile(context, rp, action, sheet), LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, 4, 0, 4, 0));
        }
        if (row != null && row.getChildCount() == 1) {
            // An odd tile keeps the width of half a row instead of stretching across.
            row.addView(new View(context), LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, 4, 0, 4, 0));
        }
        scroll.addView(grid, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (askHint != null && onAsk != null) {
            // Looks like a message field and behaves like its doorway: a tap opens the keyboard dialog.
            final FrameLayout ask = new FrameLayout(context);
            ask.setBackground(Theme.createRoundRectDrawable(dp(23), Theme.multAlpha(textColor, 0.06f)));
            final TextView hint = new TextView(context);
            hint.setText(askHint);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            hint.setTextColor(gray);
            hint.setSingleLine();
            ask.addView(hint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 18, 0, 52, 0));
            final ImageView send = new ImageView(context);
            send.setScaleType(ImageView.ScaleType.CENTER);
            send.setImageResource(R.drawable.msg_arrow_forward);
            send.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
            send.setBackground(Theme.createRoundRectDrawable(dp(18), accent));
            ask.addView(send, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 5, 0));
            NgramMotion.apply(ask);
            ask.setOnClickListener(v -> {
                sheet.dismiss();
                onAsk.run();
            });
            column.addView(ask, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 16, 8, 16, 14));
        } else {
            column.addView(new View(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 10));
        }

        sheet.setCustomView(column);
        fragment.showDialog(sheet);
    }

    private static View tile(Context context, Theme.ResourcesProvider rp, Action action, BottomSheet sheet) {
        final int textColor = Theme.getColor(Theme.key_dialogTextBlack, rp);
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton, rp);
        final LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(12), 0, dp(10), 0);
        tile.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.multAlpha(textColor, 0.06f)));

        final ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImageResource(action.icon);
        icon.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
        tile.addView(icon, LayoutHelper.createLinear(26, 26, 0, 0, 10, 0));

        final TextView title = new TextView(context);
        title.setText(action.title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        NgramMotion.apply(tile);
        tile.setOnClickListener(v -> {
            sheet.dismiss();
            action.run.run();
        });
        return tile;
    }
}
