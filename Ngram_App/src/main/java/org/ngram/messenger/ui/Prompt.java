package org.ngram.messenger.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

/** A dialog that asks for one text, in the look of Telegram's own text prompts. */
public final class Prompt {

    public interface Done {
        void run(String text);
    }

    public static final int PLAIN = 0;
    /** Several lines, sentences start with a capital. */
    public static final int MULTILINE = 1;
    /** One line that is neither corrected nor learned by the keyboard: keys, addresses. */
    public static final int EXACT = 2;

    private Prompt() {
    }

    public static void show(BaseFragment fragment, String title, String info, String hint, String value, int kind, Done done) {
        final Context context = fragment.getContext();
        if (context == null) {
            return;
        }
        final Theme.ResourcesProvider rp = fragment.getResourceProvider();
        final Activity activity = AndroidUtilities.findActivity(context);

        final AlertDialog.Builder builder = new AlertDialog.Builder(context, rp);
        builder.setTitle(title);
        if (info != null) {
            builder.setMessage(info);
        }

        final EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        edit.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, rp));
        edit.setHint(hint);
        edit.setFocusable(true);
        if (kind == MULTILINE) {
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            edit.setMaxLines(8);
        } else if (kind == EXACT) {
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            edit.setMaxLines(1);
            edit.setSingleLine(true);
        } else {
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            edit.setMaxLines(1);
            edit.setSingleLine(true);
        }
        edit.setPadding(dp(16), dp(11), dp(16), dp(11));
        edit.setCursorWidth(1.5f);
        edit.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, rp));
        if (value != null) {
            edit.setText(value);
        }

        final GradientDrawable field = new GradientDrawable();
        field.setCornerRadius(dp(kind == MULTILINE ? 16 : 22));
        field.setColor(Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack, rp), 0.06f));
        edit.setBackground(field);

        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(edit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 9, 20, 9));

        builder.makeCustomMaxHeight();
        builder.setView(container);
        builder.setWidth(Math.min(dp(340), AndroidUtilities.displaySize.x * 88 / 100));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            done.run(edit.getText().toString().trim());
            dialog.dismiss();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());

        final AlertDialog dialog = builder.create();
        AndroidUtilities.requestAdjustNothing(activity, fragment.getClassGuid());
        dialog.setDismissDialogByButtons(false);
        dialog.setOnShowListener(d -> {
            edit.requestFocus();
            AndroidUtilities.showKeyboard(edit);
        });
        fragment.showDialog(dialog, d -> {
            AndroidUtilities.hideKeyboard(edit);
            AndroidUtilities.requestAdjustResize(activity, fragment.getClassGuid());
        });
        edit.setSelection(edit.getText().length());
    }
}
