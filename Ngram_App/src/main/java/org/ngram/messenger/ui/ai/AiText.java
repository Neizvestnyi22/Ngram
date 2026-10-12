package org.ngram.messenger.ui.ai;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.widget.TextView;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

import io.noties.markwon.Markwon;

/** Text of the assistant: Markdown drawn into an ordinary TextView. */
final class AiText {

    private static Markwon markwon;

    private AiText() {
    }

    static TextView view(Context context, Theme.ResourcesProvider rp) {
        final TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, rp));
        text.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, rp));
        text.setLineSpacing(dp(2), 1f);
        text.setTextIsSelectable(true);
        text.setMovementMethod(LinkMovementMethod.getInstance());
        return text;
    }

    /** Shows Markdown; while an answer is still being written an unfinished fence or list is fine. */
    static void set(TextView view, String text, boolean markdown) {
        if (!markdown || text == null || text.isEmpty()) {
            view.setText(text == null ? "" : text);
            return;
        }
        try {
            if (markwon == null) {
                markwon = Markwon.create(ApplicationLoader.applicationContext);
            }
            markwon.setMarkdown(view, text);
        } catch (Throwable e) {
            view.setText(text);
        }
    }
}
