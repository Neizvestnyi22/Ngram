package org.ngram.messenger.features;

import static org.ngram.messenger.N.tr;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.ngram.messenger.Share;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Writes the messages of a chat that are already loaded on screen into a file: plain text, a
 * web page or JSON. Chats whose owner has switched copying off, and secret chats, are not
 * exported.
 */
public final class ChatExport {

    private static final int TXT = 0, HTML = 1, JSON = 2;

    private ChatExport() {
    }

    public static void start(ChatActivity chat) {
        if (chat.getContext() == null) {
            return;
        }
        if (chat.isSecretChat() || chat.isPeerNoForwards()) {
            BulletinFactory.of(chat).createSimpleBulletin(R.raw.error, tr(
                "В этом чате копирование запрещено, поэтому экспорт недоступен.",
                "Copying is switched off in this chat, so it cannot be exported.")).show();
            return;
        }
        final ArrayList<MessageObject> messages = usable(chat);
        if (messages.isEmpty()) {
            BulletinFactory.of(chat).createSimpleBulletin(R.raw.error, tr("Здесь пока нечего экспортировать.", "Nothing to export here yet.")).show();
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(chat.getContext(), chat.getResourceProvider());
        builder.setTitle(tr("Экспорт переписки", "Export Chat"));
        builder.setMessage(tr(
            "В файл попадут сообщения, которые уже загружены на экране: " + messages.size() + ". Чтобы захватить больше, сначала пролистайте чат вверх. Медиафайлы не вкладываются — только их описание.",
            "The file will hold the messages already loaded on screen: " + messages.size() + ". Scroll the chat up first to take more. Media files are not included, only their description."));
        builder.setItems(new CharSequence[]{
            tr("Текст (.txt)", "Plain text (.txt)"),
            tr("Веб-страница (.html)", "Web page (.html)"),
            "JSON (.json)"
        }, (dialog, which) -> export(chat, messages, which));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        chat.showDialog(builder.create());
    }

    /** Loaded messages, oldest first, without service lines, dates and protected ones. */
    private static ArrayList<MessageObject> usable(ChatActivity chat) {
        final ArrayList<MessageObject> result = new ArrayList<>();
        final ArrayList<MessageObject> all = chat.messages;
        if (all == null) {
            return result;
        }
        for (int i = all.size() - 1; i >= 0; i--) {
            final MessageObject m = all.get(i);
            if (m == null || m.messageOwner == null || m.isDateObject || m.isSponsored() || m.getId() == 0 || m.messageOwner.noforwards) {
                continue;
            }
            result.add(m);
        }
        return result;
    }

    private static void export(ChatActivity chat, ArrayList<MessageObject> messages, int format) {
        final String title = title(chat);
        final MessagesController controller = chat.getMessagesController();
        final long selfId = chat.getUserConfig().getClientUserId();
        final String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date());
        final String extension = format == HTML ? ".html" : format == JSON ? ".json" : ".txt";
        final String mime = format == HTML ? "text/html" : format == JSON ? "application/json" : "text/plain";
        final File file = Share.file(title + "_" + stamp + extension);

        // Names are looked up here, on the main thread; the writing itself happens off it.
        final ArrayList<Line> lines = new ArrayList<>(messages.size());
        for (MessageObject m : messages) {
            lines.add(new Line(m, sender(controller, m, selfId)));
        }
        Utilities.globalQueue.postRunnable(() -> {
            String error = null;
            try {
                final String text = format == HTML ? html(title, lines) : format == JSON ? json(title, lines) : txt(title, lines);
                Share.write(file, text);
            } catch (Throwable e) {
                FileLog.e(e);
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String failed = error;
            AndroidUtilities.runOnUIThread(() -> done(chat, file, mime, lines.size(), failed));
        });
    }

    private static void done(ChatActivity chat, File file, String mime, int count, String error) {
        if (chat.getContext() == null) {
            return;
        }
        if (error != null) {
            BulletinFactory.of(chat).createSimpleBulletin(R.raw.error, tr("Не удалось записать файл: ", "Could not write the file: ") + error).show();
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(chat.getContext(), chat.getResourceProvider());
        builder.setTitle(tr("Файл готов", "The File Is Ready"));
        builder.setMessage(tr("Сообщений: ", "Messages: ") + count + "\n" + file.getName());
        builder.setPositiveButton(tr("Поделиться", "Share"), (d, w) -> Share.send(chat.getContext(), file, mime, tr("Экспорт переписки", "Export Chat")));
        builder.setNeutralButton(tr("В «Загрузки»", "To Downloads"), (d, w) ->
            MediaController.saveFile(file.getAbsolutePath(), chat.getContext(), 3, file.getName(), mime, uri -> {
                if (chat.getContext() != null) {
                    BulletinFactory.of(chat).createSimpleBulletin(R.raw.ic_save_to_gallery, tr("Сохранено в «Загрузки»", "Saved to Downloads")).show();
                }
            }));
        builder.setNegativeButton(LocaleController.getString(R.string.Close), null);
        chat.showDialog(builder.create());
    }

    private static final class Line {
        final int id;
        final long date;
        final long edited;
        final String sender;
        final String text;
        final String media;
        final String forwarded;
        final int replyTo;
        final boolean out;

        Line(MessageObject m, String sender) {
            final TLRPC.Message o = m.messageOwner;
            this.id = o.id;
            this.date = o.date;
            this.edited = o.edit_date;
            this.sender = sender;
            this.out = m.isOut();
            if (o.action != null) {
                this.text = m.messageText == null ? "" : m.messageText.toString();
                this.media = tr("служебное", "service");
            } else {
                this.text = !TextUtils.isEmpty(o.message) ? o.message : m.caption == null ? "" : m.caption.toString();
                this.media = mediaOf(m);
            }
            this.forwarded = o.fwd_from == null ? null : !TextUtils.isEmpty(o.fwd_from.from_name) ? o.fwd_from.from_name : "";
            this.replyTo = o.reply_to == null ? 0 : o.reply_to.reply_to_msg_id;
        }
    }

    private static String mediaOf(MessageObject m) {
        switch (m.type) {
            case MessageObject.TYPE_PHOTO:
                return tr("фото", "photo");
            case MessageObject.TYPE_VOICE:
                return tr("голосовое", "voice message");
            case MessageObject.TYPE_VIDEO:
                return tr("видео", "video");
            case MessageObject.TYPE_GEO:
                return tr("место", "location");
            case MessageObject.TYPE_ROUND_VIDEO:
                return tr("видеосообщение", "video message");
            case MessageObject.TYPE_GIF:
                return "GIF";
            case MessageObject.TYPE_FILE:
                return tr("файл", "file") + (TextUtils.isEmpty(m.getDocumentName()) ? "" : " " + m.getDocumentName());
            case MessageObject.TYPE_CONTACT:
                return tr("контакт", "contact");
            case MessageObject.TYPE_STICKER:
            case MessageObject.TYPE_ANIMATED_STICKER:
                return tr("стикер", "sticker");
            case MessageObject.TYPE_MUSIC:
                return tr("музыка", "music") + (TextUtils.isEmpty(m.getDocumentName()) ? "" : " " + m.getDocumentName());
            case MessageObject.TYPE_PHONE_CALL:
                return tr("звонок", "call");
            case MessageObject.TYPE_POLL:
                return tr("опрос", "poll");
            case MessageObject.TYPE_TEXT:
            case MessageObject.TYPE_EMOJIS:
                return null;
            default:
                return tr("вложение", "attachment");
        }
    }

    private static String sender(MessagesController controller, MessageObject m, long selfId) {
        final long id = m.getSenderId();
        if (id == selfId || m.isOut() && id > 0) {
            return tr("Я", "Me");
        }
        if (id > 0) {
            final TLRPC.User user = controller.getUser(id);
            return user == null ? tr("Пользователь ", "User ") + id : UserObject.getUserName(user);
        }
        final TLRPC.Chat c = controller.getChat(-id);
        return c == null ? tr("Чат ", "Chat ") + (-id) : c.title;
    }

    private static String title(ChatActivity chat) {
        if (chat.getCurrentChat() != null) {
            return chat.getCurrentChat().title;
        }
        final TLRPC.User user = chat.getCurrentUser();
        if (user != null) {
            return UserObject.isUserSelf(user) ? tr("Избранное", "Saved Messages") : UserObject.getUserName(user);
        }
        return tr("Чат", "Chat");
    }

    private static SimpleDateFormat stampFormat() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    }

    // ---- formats ------------------------------------------------------------------------

    private static String txt(String title, ArrayList<Line> lines) {
        final SimpleDateFormat stamp = stampFormat();
        final StringBuilder sb = new StringBuilder(lines.size() * 80);
        sb.append(title).append('\n');
        sb.append(tr("Экспорт из Ngram, сообщений: ", "Exported from Ngram, messages: ")).append(lines.size()).append("\n\n");
        for (Line l : lines) {
            sb.append('[').append(stamp.format(new Date(l.date * 1000L))).append("] ").append(l.sender).append(": ");
            if (l.forwarded != null) {
                sb.append(tr("(переслано", "(forwarded")).append(l.forwarded.isEmpty() ? "" : tr(" от ", " from ") + l.forwarded).append(") ");
            }
            if (l.media != null) {
                sb.append('<').append(l.media).append("> ");
            }
            sb.append(l.text);
            if (l.edited != 0) {
                sb.append(tr(" (изменено)", " (edited)"));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String json(String title, ArrayList<Line> lines) throws Exception {
        final JSONObject root = new JSONObject();
        root.put("chat", title);
        root.put("exported_by", "Ngram");
        root.put("exported_at", System.currentTimeMillis() / 1000);
        final JSONArray array = new JSONArray();
        for (Line l : lines) {
            final JSONObject o = new JSONObject();
            o.put("id", l.id);
            o.put("date", l.date);
            if (l.edited != 0) {
                o.put("edited", l.edited);
            }
            o.put("from", l.sender);
            o.put("out", l.out);
            if (l.replyTo != 0) {
                o.put("reply_to", l.replyTo);
            }
            if (l.forwarded != null) {
                o.put("forwarded_from", l.forwarded);
            }
            if (l.media != null) {
                o.put("media", l.media);
            }
            o.put("text", l.text);
            array.put(o);
        }
        root.put("messages", array);
        return root.toString(2);
    }

    private static String html(String title, ArrayList<Line> lines) {
        final SimpleDateFormat stamp = stampFormat();
        final SimpleDateFormat day = new SimpleDateFormat("d MMMM yyyy", Locale.getDefault());
        final StringBuilder sb = new StringBuilder(lines.size() * 200);
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>")
            .append(escape(title)).append("</title><style>")
            .append("body{margin:0;background:#dfe7ee;font:15px/1.4 -apple-system,Roboto,Segoe UI,sans-serif;color:#111}")
            .append("header{position:sticky;top:0;padding:14px 18px;background:rgba(255,255,255,.82);backdrop-filter:blur(18px);font-weight:600;font-size:17px;border-bottom:1px solid rgba(0,0,0,.08)}")
            .append("main{max-width:760px;margin:0 auto;padding:12px}")
            .append(".d{text-align:center;margin:14px 0 8px;color:#55606b;font-size:13px}")
            .append(".m{max-width:78%;margin:4px 0;padding:7px 11px 5px;border-radius:17px;background:#fff;clear:both;float:left;box-shadow:0 1px 1px rgba(0,0,0,.08);white-space:pre-wrap;word-wrap:break-word}")
            .append(".m.o{float:right;background:#e3f6d3}")
            .append(".n{font-weight:600;color:#2e86ff;font-size:13px}.f{color:#55606b;font-size:13px}.a{color:#2e86ff}")
            .append(".t{float:right;margin:4px 0 0 10px;color:#7b8794;font-size:11px}")
            .append("footer{clear:both;padding:22px;text-align:center;color:#55606b;font-size:12px}")
            .append("@media(prefers-color-scheme:dark){body{background:#0e1114;color:#eee}header{background:rgba(28,28,30,.82)}.m{background:#1c1f24}.m.o{background:#2b5278}.d,.f,footer{color:#9aa4ad}}")
            .append("</style></head><body><header>").append(escape(title)).append("</header><main>");
        String lastDay = "";
        for (Line l : lines) {
            final Date date = new Date(l.date * 1000L);
            final String thisDay = day.format(date);
            if (!thisDay.equals(lastDay)) {
                sb.append("<div class=\"d\">").append(escape(thisDay)).append("</div>");
                lastDay = thisDay;
            }
            sb.append("<div class=\"m").append(l.out ? " o" : "").append("\" title=\"").append(stamp.format(date)).append(" · #").append(l.id).append("\">");
            if (!l.out) {
                sb.append("<div class=\"n\">").append(escape(l.sender)).append("</div>");
            }
            if (l.forwarded != null) {
                sb.append("<div class=\"f\">").append(escape(tr("Переслано", "Forwarded") + (l.forwarded.isEmpty() ? "" : tr(" от ", " from ") + l.forwarded))).append("</div>");
            }
            if (l.media != null) {
                sb.append("<div class=\"a\">").append(escape(l.media)).append("</div>");
            }
            sb.append(escape(l.text));
            sb.append("<span class=\"t\">").append(l.edited != 0 ? escape(tr("изм. ", "edited ")) : "").append(new SimpleDateFormat("HH:mm", Locale.US).format(date)).append("</span></div>");
        }
        sb.append("</main><footer>").append(escape(tr("Экспорт из Ngram · сообщений: ", "Exported from Ngram · messages: "))).append(lines.size()).append("</footer></body></html>");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
