package org.ngram.messenger.ui.ai;

import static org.ngram.messenger.N.tr;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.ngram.messenger.ai.AiTasks;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

/**
 * A sheet over a chat where one job of the assistant (explain, translate, summarize, suggest
 * a reply) is written out as it arrives.
 */
public final class AiSheet {

    private AiSheet() {
    }

    /**
     * @param useText when not null, the sheet offers to put the result into the message field
     */
    public static void run(BaseFragment fragment, String title, String prompt, Utilities.Callback<String> useText) {
        run(fragment, title, prompt, useText, false);
    }

    /**
     * @param pickLine the answer is a list of alternatives, one per line: each becomes a row to
     *                 tap, and the tapped one goes to {@code useText}
     */
    public static void run(BaseFragment fragment, String title, String prompt, Utilities.Callback<String> useText, boolean pickLine) {
        runJob(fragment, title, listener -> AiTasks.run(prompt, null, listener), useText, pickLine);
    }

    /** The same sheet for a prompt that comes with a picture (JPEG, Base64). */
    public static void runWithImage(BaseFragment fragment, String title, String prompt, String imageBase64) {
        runJob(fragment, title, listener -> AiTasks.run(prompt, imageBase64, listener), null, false);
    }

    /** Starts a job of the assistant and hands it the listener that writes into the sheet. */
    public interface Starter {
        AiTasks.Job start(AiTasks.Listener listener);
    }

    public static void runJob(BaseFragment fragment, String title, Starter starter, Utilities.Callback<String> useText, boolean pickLine) {
        final Context context = fragment.getContext();
        if (context == null) {
            return;
        }
        final Theme.ResourcesProvider rp = fragment.getResourceProvider();
        final BottomSheet sheet = new BottomSheet(context, false, rp);
        sheet.fixNavigationBar();

        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        final TextView heading = new TextView(context);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        heading.setTypeface(AndroidUtilities.bold());
        heading.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        heading.setText(title);
        column.addView(heading, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 18, 22, 8));

        final TextView body = AiText.view(context, rp);
        body.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        body.setText("…");
        body.setPadding(dp(22), 0, dp(22), dp(12));
        // As tall as the text, but never more than a little over half the screen.
        final ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((int) (AndroidUtilities.displaySize.y * 0.55f), MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        scroll.addView(content, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        final TextView copy = button(context, rp, tr("Копировать", "Copy"), false);
        final TextView use = useText == null || pickLine ? null : button(context, rp, tr("В поле ввода", "To the Message Field"), true);
        buttons.addView(copy, LayoutHelper.createLinear(0, 46, 1f, 0, 0, use == null ? 0 : 8, 0));
        if (use != null) {
            buttons.addView(use, LayoutHelper.createLinear(0, 46, 1f));
        }
        column.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 16, 4, 16, 14));
        copy.setEnabled(false);
        copy.setAlpha(0.5f);
        if (use != null) {
            use.setEnabled(false);
            use.setAlpha(0.5f);
        }

        sheet.setCustomView(column);

        final String[] result = {""};
        final AiTasks.Job job = starter.start(new AiTasks.Listener() {
            @Override
            public void onText(String text) {
                result[0] = text;
                // Alternatives come one per line, and Markdown would glue the lines together.
                AiText.set(body, text, !pickLine);
            }

            @Override
            public void onDone(String text, String error) {
                result[0] = text;
                if (error != null && !error.isEmpty()) {
                    final String shown = text.isEmpty() ? error : text + "\n\n" + error;
                    body.setTextColor(Theme.getColor(Theme.key_text_RedRegular, rp));
                    AiText.set(body, shown, false);
                } else if (pickLine && useText != null) {
                    body.setVisibility(android.view.View.GONE);
                    for (String line : text.split("\n")) {
                        final String option = line.trim().replaceFirst("^(\\d+[.)]|[-*•])\\s+", "");
                        if (option.isEmpty()) {
                            continue;
                        }
                        final TextView row = new TextView(context);
                        row.setText(option);
                        row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                        row.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
                        row.setPadding(dp(16), dp(12), dp(16), dp(12));
                        row.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack, rp), 0.06f)));
                        ScaleStateListAnimator.apply(row);
                        row.setOnClickListener(v -> {
                            useText.run(option);
                            sheet.dismiss();
                        });
                        content.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 16, 8));
                    }
                } else {
                    AiText.set(body, text, true);
                }
                if (!text.isEmpty()) {
                    copy.setEnabled(true);
                    copy.setAlpha(1f);
                    if (use != null) {
                        use.setEnabled(true);
                        use.setAlpha(1f);
                    }
                }
            }
        });

        copy.setOnClickListener(v -> {
            if (AndroidUtilities.addToClipboard(result[0])) {
                BulletinFactory.of(fragment).createCopyBulletin(tr("Скопировано", "Copied")).show();
            }
            sheet.dismiss();
        });
        if (use != null) {
            use.setOnClickListener(v -> {
                useText.run(result[0].trim());
                sheet.dismiss();
            });
        }
        // Leaving the sheet stops the request: nobody is going to read the rest.
        fragment.showDialog(sheet, d -> job.cancel());
    }

    private static TextView button(Context context, Theme.ResourcesProvider rp, String text, boolean filled) {
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
