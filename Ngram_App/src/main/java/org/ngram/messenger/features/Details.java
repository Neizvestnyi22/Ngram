package org.ngram.messenger.features;

import static org.ngram.messenger.N.tr;

import android.text.TextUtils;

import org.ngram.messenger.ui.InfoSheet;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * "Details" sheets: everything the app already knows about a message, a person or a chat, laid
 * out as lines that can be copied. Nothing is requested from Telegram for them — they show what
 * has already arrived.
 */
public final class Details {

    private Details() {
    }

    // ---- a message ----------------------------------------------------------------------

    public static void showMessage(ChatActivity chat, MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return;
        }
        InfoSheet.show(chat, tr("Подробности сообщения", "Message Details"), message(chat, message));
    }

    static InfoSheet.Rows message(ChatActivity chat, MessageObject message) {
        final TLRPC.Message m = message.messageOwner;
        final MessagesController controller = chat.getMessagesController();
        final InfoSheet.Rows rows = new InfoSheet.Rows();

        rows.add(tr("Номер сообщения", "Message ID"), m.id);
        rows.add(tr("Тип", "Kind"), kind(message));
        rows.add(tr("Отправлено", "Sent"), date(m.date));
        if (m.edit_date != 0) {
            rows.add(tr("Изменено", "Edited"), date(m.edit_date));
        }
        rows.add(tr("Отправитель", "Sender"), peer(controller, message.getSenderId()));
        final long dialogId = message.getDialogId();
        if (dialogId != message.getSenderId()) {
            rows.add(tr("Чат", "Chat"), peer(controller, dialogId));
        }
        if (!TextUtils.isEmpty(m.post_author)) {
            rows.add(tr("Подпись автора", "Author's Signature"), m.post_author);
        }
        if (m.via_bot_id != 0) {
            rows.add(tr("Через бота", "Via Bot"), peer(controller, m.via_bot_id));
        }
        if (m.reply_to != null && m.reply_to.reply_to_msg_id != 0) {
            rows.add(tr("Ответ на сообщение", "Reply to Message"), m.reply_to.reply_to_msg_id);
        }
        if (m.grouped_id != 0) {
            rows.add(tr("Номер альбома", "Album ID"), m.grouped_id);
        }
        if (m.views > 0) {
            rows.add(tr("Просмотры", "Views"), m.views);
        }
        if (m.forwards > 0) {
            rows.add(tr("Пересылки", "Forwards"), m.forwards);
        }
        if (m.ttl_period != 0) {
            rows.add(tr("Удалится через", "Deletes In"), LocaleController.formatTTLString(m.ttl_period));
        }
        final ArrayList<String> flags = new ArrayList<>();
        if (m.out) flags.add(tr("исходящее", "outgoing"));
        if (m.silent) flags.add(tr("без звука", "silent"));
        if (m.pinned) flags.add(tr("закреплено", "pinned"));
        if (m.post) flags.add(tr("пост канала", "channel post"));
        if (m.noforwards) flags.add(tr("пересылка запрещена", "forwarding restricted"));
        if (m.from_scheduled) flags.add(tr("отложенное", "was scheduled"));
        if (m.mentioned) flags.add(tr("вас упомянули", "mentions you"));
        if (m.media_unread) flags.add(tr("не прослушано", "not listened to"));
        if (!flags.isEmpty()) {
            rows.add(tr("Отметки", "Flags"), TextUtils.join(", ", flags));
        }

        final TLRPC.MessageFwdHeader fwd = m.fwd_from;
        if (fwd != null) {
            rows.section(tr("Переслано", "Forwarded"));
            if (fwd.from_id != null) {
                rows.add(tr("Откуда", "From"), peer(controller, MessageObject.getPeerId(fwd.from_id)));
            } else if (!TextUtils.isEmpty(fwd.from_name)) {
                rows.add(tr("Откуда", "From"), fwd.from_name + tr(" (профиль скрыт)", " (hidden profile)"));
            }
            if (fwd.date != 0) {
                rows.add(tr("Написано", "Written"), date(fwd.date));
            }
            if (fwd.channel_post != 0) {
                rows.add(tr("Номер исходного поста", "Original Post ID"), fwd.channel_post);
            }
            if (!TextUtils.isEmpty(fwd.post_author)) {
                rows.add(tr("Автор", "Author"), fwd.post_author);
            }
        }

        final String text = !TextUtils.isEmpty(m.message) ? m.message : message.caption == null ? "" : message.caption.toString();
        if (!text.isEmpty()) {
            rows.section(tr("Текст", "Text"));
            rows.add(tr("Знаков", "Characters"), text.codePointCount(0, text.length()));
            rows.add(tr("Слов", "Words"), text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length);
            if (m.entities != null && !m.entities.isEmpty()) {
                rows.add(tr("Элементов оформления", "Formatting Entities"), m.entities.size());
            }
        }

        media(chat, message, rows);
        return rows;
    }

    private static void media(ChatActivity chat, MessageObject message, InfoSheet.Rows rows) {
        final TLRPC.Message m = message.messageOwner;
        final TLRPC.Document document = message.getDocument();
        final TLRPC.Photo photo = m.media == null ? null : m.media.photo;
        if (document == null && photo == null) {
            return;
        }
        rows.section(tr("Файл", "File"));
        if (document != null) {
            final String name = FileLoader.getDocumentFileName(document);
            rows.add(tr("Имя", "Name"), name);
            rows.add(tr("Размер", "Size"), AndroidUtilities.formatFileSize(document.size) + " (" + document.size + ")");
            rows.add(tr("Вид данных", "MIME Type"), document.mime_type);
            int w = 0, h = 0;
            String title = null, performer = null;
            if (document.attributes != null) {
                for (TLRPC.DocumentAttribute attribute : document.attributes) {
                    if (attribute instanceof TLRPC.TL_documentAttributeVideo || attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                        w = attribute.w;
                        h = attribute.h;
                    } else if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                        title = attribute.title;
                        performer = attribute.performer;
                    }
                }
            }
            if (w > 0 && h > 0) {
                rows.add(tr("Размеры", "Dimensions"), w + " × " + h);
            }
            final double duration = message.getDuration();
            if (duration > 0) {
                rows.add(tr("Длительность", "Duration"), AndroidUtilities.formatShortDuration((int) Math.round(duration)));
            }
            rows.add(tr("Название", "Title"), title);
            rows.add(tr("Исполнитель", "Performer"), performer);
            rows.add(tr("Дата-центр", "Data Center"), "DC" + document.dc_id);
            rows.add(tr("Номер файла", "File ID"), document.id);
        } else {
            TLRPC.PhotoSize biggest = null;
            if (photo.sizes != null) {
                for (TLRPC.PhotoSize size : photo.sizes) {
                    if (size != null && (biggest == null || size.w * size.h > biggest.w * biggest.h)) {
                        biggest = size;
                    }
                }
            }
            if (biggest != null) {
                rows.add(tr("Размеры", "Dimensions"), biggest.w + " × " + biggest.h);
                if (biggest.size > 0) {
                    rows.add(tr("Размер", "Size"), AndroidUtilities.formatFileSize(biggest.size));
                }
            }
            rows.add(tr("Дата-центр", "Data Center"), "DC" + photo.dc_id);
            rows.add(tr("Номер фото", "Photo ID"), photo.id);
        }
        try {
            final File file = FileLoader.getInstance(chat.getCurrentAccount()).getPathToMessage(m);
            final boolean loaded = file != null && file.exists();
            rows.add(tr("На устройстве", "On This Device"), loaded ? tr("загружен", "downloaded") : tr("не загружен", "not downloaded"));
            if (loaded && !chat.isPeerNoForwards() && !m.noforwards) {
                rows.add(tr("Путь", "Path"), file.getAbsolutePath());
            }
        } catch (Throwable ignore) {
        }
    }

    private static String kind(MessageObject message) {
        if (message.messageOwner.action != null) {
            return tr("служебное", "service");
        }
        switch (message.type) {
            case MessageObject.TYPE_TEXT:
                return message.isWebpage() ? tr("текст со ссылкой", "text with a link") : tr("текст", "text");
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
                return tr("файл", "file");
            case MessageObject.TYPE_CONTACT:
                return tr("контакт", "contact");
            case MessageObject.TYPE_STICKER:
                return tr("стикер", "sticker");
            case MessageObject.TYPE_ANIMATED_STICKER:
                return tr("анимированный стикер", "animated sticker");
            case MessageObject.TYPE_MUSIC:
                return tr("музыка", "music");
            case MessageObject.TYPE_PHONE_CALL:
                return tr("звонок", "call");
            case MessageObject.TYPE_POLL:
                return tr("опрос", "poll");
            case MessageObject.TYPE_EMOJIS:
                return tr("эмодзи", "emoji");
            case MessageObject.TYPE_STORY:
            case MessageObject.TYPE_STORY_MENTION:
                return tr("история", "story");
            case MessageObject.TYPE_GIVEAWAY:
            case MessageObject.TYPE_GIVEAWAY_RESULTS:
                return tr("розыгрыш", "giveaway");
            case MessageObject.TYPE_PAID_MEDIA:
                return tr("платное медиа", "paid media");
            default:
                return tr("другое", "other") + " (" + message.type + ")";
        }
    }

    // ---- a person or a chat -------------------------------------------------------------

    public static void showPeer(BaseFragment fragment, long dialogId, long topicId) {
        final InfoSheet.Rows rows = dialogId > 0 ? user(fragment, dialogId) : chat(fragment, -dialogId, topicId);
        if (rows == null || rows.isEmpty()) {
            return;
        }
        InfoSheet.show(fragment, tr("Сведения", "Details"), rows);
    }

    private static InfoSheet.Rows user(BaseFragment fragment, long userId) {
        final MessagesController controller = fragment.getMessagesController();
        final TLRPC.User user = controller.getUser(userId);
        if (user == null) {
            return null;
        }
        final InfoSheet.Rows rows = new InfoSheet.Rows();
        rows.add("ID", user.id);
        rows.add(tr("Имя", "Name"), UserObject.getUserName(user));
        usernames(rows, user.username, user.usernames);
        String type = user.bot ? tr("бот", "bot") : user.deleted ? tr("удалённый аккаунт", "deleted account") : tr("пользователь", "user");
        if (UserObject.isUserSelf(user)) {
            type += tr(" (это вы)", " (this is you)");
        }
        rows.add(tr("Тип", "Kind"), type);

        final ArrayList<String> flags = new ArrayList<>();
        if (user.verified) flags.add(tr("подтверждён", "verified"));
        if (user.premium) flags.add("Premium");
        if (user.scam) flags.add(tr("мошенник", "scam"));
        if (user.fake) flags.add(tr("подделка", "fake"));
        if (user.support) flags.add(tr("поддержка", "support"));
        if (user.contact) flags.add(user.mutual_contact ? tr("взаимный контакт", "mutual contact") : tr("в контактах", "in contacts"));
        if (user.restricted) flags.add(tr("ограничен", "restricted"));
        if (!flags.isEmpty()) {
            rows.add(tr("Отметки", "Flags"), TextUtils.join(", ", flags));
        }
        if (user.photo != null && user.photo.dc_id != 0) {
            rows.add(tr("Дата-центр", "Data Center"), "DC" + user.photo.dc_id + tr(" (по фото профиля)", " (from the profile photo)"));
        }
        if (!TextUtils.isEmpty(user.lang_code)) {
            rows.add(tr("Язык", "Language"), user.lang_code);
        }
        final TLRPC.UserFull full = controller.getUserFull(userId);
        if (full != null) {
            rows.add(tr("О себе", "Bio"), full.about);
            if (full.common_chats_count > 0) {
                rows.add(tr("Общих групп", "Groups in Common"), full.common_chats_count);
            }
            if (full.ttl_period > 0) {
                rows.add(tr("Автоудаление", "Auto-Delete"), LocaleController.formatTTLString(full.ttl_period));
            }
        }
        rows.section(tr("Ссылки", "Links"));
        rows.add(tr("Внутри Telegram", "Inside Telegram"), "tg://user?id=" + user.id);
        final String username = UserObject.getPublicUsername(user);
        if (!TextUtils.isEmpty(username)) {
            rows.add(tr("Публичная", "Public"), "https://t.me/" + username);
        }
        return rows;
    }

    private static InfoSheet.Rows chat(BaseFragment fragment, long chatId, long topicId) {
        final MessagesController controller = fragment.getMessagesController();
        final TLRPC.Chat chat = controller.getChat(chatId);
        if (chat == null) {
            return null;
        }
        final boolean channel = ChatObject.isChannel(chat);
        final InfoSheet.Rows rows = new InfoSheet.Rows();
        rows.add("ID", chat.id);
        rows.add(tr("ID для ботов", "ID for Bots"), channel ? "-100" + chat.id : "-" + chat.id);
        if (topicId != 0) {
            rows.add(tr("Номер темы", "Topic ID"), topicId);
        }
        rows.add(tr("Название", "Title"), chat.title);
        usernames(rows, chat.username, chat.usernames);
        final String type;
        if (!channel) {
            type = tr("группа", "group");
        } else if (chat.megagroup) {
            type = chat.forum ? tr("супергруппа с темами", "supergroup with topics") : chat.gigagroup ? tr("группа-трансляция", "broadcast group") : tr("супергруппа", "supergroup");
        } else {
            type = tr("канал", "channel");
        }
        rows.add(tr("Тип", "Kind"), type);

        final TLRPC.ChatFull full = controller.getChatFull(chatId);
        final int members = full != null && full.participants_count > 0 ? full.participants_count : chat.participants_count;
        if (members > 0) {
            rows.add(channel && !chat.megagroup ? tr("Подписчиков", "Subscribers") : tr("Участников", "Members"), members);
        }
        if (full != null) {
            if (full.online_count > 0) {
                rows.add(tr("Сейчас в сети", "Online Now"), full.online_count);
            }
            if (full.admins_count > 0) {
                rows.add(tr("Администраторов", "Administrators"), full.admins_count);
            }
            if (full.banned_count > 0) {
                rows.add(tr("С ограничениями", "Restricted"), full.banned_count);
            }
            if (full.kicked_count > 0) {
                rows.add(tr("Исключено", "Removed"), full.kicked_count);
            }
        }
        if (chat.date != 0) {
            rows.add(chat.creator ? tr("Создан", "Created") : tr("Вы здесь с", "You Joined"), date(chat.date));
        }

        final ArrayList<String> flags = new ArrayList<>();
        if (chat.creator) flags.add(tr("вы владелец", "you are the owner"));
        else if (ChatObject.hasAdminRights(chat)) flags.add(tr("вы администратор", "you are an administrator"));
        if (chat.verified) flags.add(tr("подтверждён", "verified"));
        if (chat.scam) flags.add(tr("мошенники", "scam"));
        if (chat.fake) flags.add(tr("подделка", "fake"));
        if (chat.noforwards) flags.add(tr("копирование запрещено", "content protected"));
        if (chat.signatures) flags.add(tr("подписи авторов", "author signatures"));
        if (chat.join_request) flags.add(tr("вступление по заявке", "join by request"));
        if (chat.slowmode_enabled) flags.add(tr("медленный режим", "slow mode"));
        if (chat.restricted) flags.add(tr("ограничен", "restricted"));
        if (chat.left) flags.add(tr("вы не участник", "you are not a member"));
        if (!flags.isEmpty()) {
            rows.add(tr("Отметки", "Flags"), TextUtils.join(", ", flags));
        }
        if (chat.photo != null && chat.photo.dc_id != 0) {
            rows.add(tr("Дата-центр", "Data Center"), "DC" + chat.photo.dc_id + tr(" (по фото)", " (from the photo)"));
        }
        if (full != null) {
            rows.add(tr("Описание", "Description"), full.about);
            if (full.linked_chat_id != 0) {
                rows.add(channel && !chat.megagroup ? tr("Группа обсуждений", "Discussion Group") : tr("Привязанный канал", "Linked Channel"), peer(controller, -full.linked_chat_id));
            }
            if (full.slowmode_seconds > 0) {
                rows.add(tr("Медленный режим", "Slow Mode"), LocaleController.formatTTLString(full.slowmode_seconds));
            }
            if (full.ttl_period > 0) {
                rows.add(tr("Автоудаление", "Auto-Delete"), LocaleController.formatTTLString(full.ttl_period));
            }
        }
        final String username = ChatObject.getPublicUsername(chat);
        if (!TextUtils.isEmpty(username)) {
            rows.section(tr("Ссылки", "Links"));
            rows.add(tr("Публичная", "Public"), "https://t.me/" + username);
        }
        return rows;
    }

    private static void usernames(InfoSheet.Rows rows, String main, ArrayList<TLRPC.TL_username> all) {
        final ArrayList<String> names = new ArrayList<>();
        if (!TextUtils.isEmpty(main)) {
            names.add("@" + main);
        }
        if (all != null) {
            for (TLRPC.TL_username u : all) {
                if (u != null && u.active && !TextUtils.isEmpty(u.username) && !names.contains("@" + u.username)) {
                    names.add("@" + u.username);
                }
            }
        }
        if (!names.isEmpty()) {
            rows.add(names.size() > 1 ? tr("Имена пользователя", "Usernames") : tr("Имя пользователя", "Username"), TextUtils.join(", ", names));
        }
    }

    // ---- shared -------------------------------------------------------------------------

    /** "Name (id)" for a user (positive id) or a chat (negative id). */
    static String peer(MessagesController controller, long id) {
        if (id > 0) {
            final TLRPC.User user = controller.getUser(id);
            return (user == null ? tr("Пользователь", "User") : UserObject.getUserName(user)) + " (" + id + ")";
        } else if (id < 0) {
            final TLRPC.Chat chat = controller.getChat(-id);
            return (chat == null ? tr("Чат", "Chat") : chat.title) + " (" + (-id) + ")";
        }
        return "";
    }

    static String date(long seconds) {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        if (locale == null) {
            locale = Locale.getDefault();
        }
        return new SimpleDateFormat(LocaleController.is24HourFormat ? "d MMMM yyyy, HH:mm:ss" : "d MMMM yyyy, h:mm:ss a", locale).format(new Date(seconds * 1000L));
    }
}
