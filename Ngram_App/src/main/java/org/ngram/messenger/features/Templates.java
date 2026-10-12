package org.ngram.messenger.features;

import static org.ngram.messenger.N.tr;

import android.text.TextUtils;

import org.json.JSONArray;
import org.ngram.messenger.Opt;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Ready-made phrases kept on the device and put into the message field in two taps. Nothing is
 * sent by itself: a template only fills the field, and the user presses Send.
 *
 * A template may hold {name}, {date} and {time} (or {имя}, {дата}, {время}); they are replaced
 * when the template is used.
 */
public final class Templates {

    private static final String KEY = "templates";
    public static final int LIMIT = 50;

    private Templates() {
    }

    public static ArrayList<String> all() {
        final ArrayList<String> list = new ArrayList<>();
        try {
            final String saved = Opt.text(KEY);
            if (!saved.isEmpty()) {
                final JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length(); i++) {
                    final String item = array.optString(i, "");
                    if (!item.isEmpty()) {
                        list.add(item);
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        return list;
    }

    public static void save(ArrayList<String> list) {
        final JSONArray array = new JSONArray();
        for (String item : list) {
            array.put(item);
        }
        Opt.text(KEY, array.toString());
    }

    /** The template with its placeholders filled in for this chat. */
    public static String fill(String template, ChatActivity chat) {
        String name = "";
        final TLRPC.User user = chat.getCurrentUser();
        if (user != null) {
            name = UserObject.getFirstName(user);
        } else if (chat.getCurrentChat() != null) {
            name = chat.getCurrentChat().title;
        }
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        if (locale == null) {
            locale = Locale.getDefault();
        }
        final Date now = new Date();
        final String date = new SimpleDateFormat("d MMMM", locale).format(now);
        final String time = new SimpleDateFormat(LocaleController.is24HourFormat ? "HH:mm" : "h:mm a", locale).format(now);
        return template
            .replace("{name}", name).replace("{имя}", name)
            .replace("{date}", date).replace("{дата}", date)
            .replace("{time}", time).replace("{время}", time);
    }

    /** The list of templates over a chat; the chosen one goes into the message field. */
    public static void pick(ChatActivity chat) {
        if (chat.getContext() == null) {
            return;
        }
        final ArrayList<String> list = all();
        final AlertDialog.Builder builder = new AlertDialog.Builder(chat.getContext(), chat.getResourceProvider());
        builder.setTitle(tr("Шаблоны", "Templates"));
        if (list.isEmpty()) {
            builder.setMessage(tr(
                "Шаблонов пока нет. Добавьте готовые фразы в настройках: Ngram → Чаты → Шаблоны.",
                "No templates yet. Add ready-made phrases in Settings: Ngram → Chats → Templates."));
            builder.setPositiveButton(tr("Открыть настройки", "Open Settings"), (d, w) -> chat.presentFragment(new org.ngram.messenger.ui.TemplatesFragment()));
            builder.setNegativeButton(LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
            chat.showDialog(builder.create());
            return;
        }
        final CharSequence[] names = new CharSequence[list.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = preview(list.get(i));
        }
        builder.setItems(names, (dialog, which) -> {
            if (which >= 0 && which < list.size()) {
                insert(chat, fill(list.get(which), chat));
            }
        });
        builder.setNegativeButton(LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
        chat.showDialog(builder.create());
    }

    /** One line of a template for a list. */
    public static String preview(String template) {
        final String line = template.replace('\n', ' ').trim();
        return line.length() > 64 ? line.substring(0, 64) + "…" : line;
    }

    /** Adds the text to what is already typed, on a new word. */
    static void insert(ChatActivity chat, String text) {
        if (chat.getChatActivityEnterView() == null || TextUtils.isEmpty(text)) {
            return;
        }
        final CharSequence typed = chat.getChatActivityEnterView().getFieldText();
        final String before = typed == null ? "" : typed.toString();
        final String joined = before.trim().isEmpty() ? text : before + (before.endsWith(" ") || before.endsWith("\n") ? "" : " ") + text;
        chat.getChatActivityEnterView().setFieldText(joined);
    }
}
