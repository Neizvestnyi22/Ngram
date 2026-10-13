package org.ngram.messenger.features;

import static org.ngram.messenger.N.tr;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import org.ngram.core.NgramConfig;
import org.ngram.core.NgramHooks;
import org.ngram.messenger.Opt;
import org.ngram.messenger.Share;
import org.ngram.messenger.ai.Ai;
import org.ngram.messenger.plugins.PluginMenus;
import org.ngram.messenger.ui.ai.AiInChat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

/**
 * Ngram's additions to a chat: extra entries in the menu of a message, the user's own
 * double-tap action, the "Ngram" entry in the header menu, and the questions asked before a
 * sticker, a GIF or a call.
 *
 * Every action here respects what Telegram itself would refuse: a chat with copying switched
 * off gives nothing to copy, save or repeat, and a secret chat is never forwarded from.
 */
public final class ChatTools {

    private static final int DETAILS = NgramHooks.MESSAGE_OPTION_BASE + 10;
    private static final int REPEAT = NgramHooks.MESSAGE_OPTION_BASE + 11;
    private static final int SAVE = NgramHooks.MESSAGE_OPTION_BASE + 12;
    private static final int COPY_PHOTO = NgramHooks.MESSAGE_OPTION_BASE + 13;
    private static final int STICKER = NgramHooks.MESSAGE_OPTION_BASE + 14;
    private static final int PLUGIN = NgramHooks.MESSAGE_OPTION_BASE + 15;
    private static final int REMIND = NgramHooks.MESSAGE_OPTION_BASE + 16;

    private ChatTools() {
    }

    // ---- the menu of a message ----------------------------------------------------------

    public static void fillMessageMenu(ChatActivity chat, MessageObject message, ArrayList<Integer> icons, ArrayList<CharSequence> items, ArrayList<Integer> options) {
        if (message == null || message.messageOwner == null || message.isSponsored() || chat.getChatMode() == ChatActivity.MODE_QUICK_REPLIES) {
            return;
        }
        if (Opt.menu(Opt.MENU_SAVE) && canForward(chat, message) && chat.getDialogId() != chat.getUserConfig().getClientUserId()) {
            add(icons, items, options, SAVE, R.drawable.msg_saved, tr("В Избранное", "To Saved Messages"));
        }
        if (Opt.menu(Opt.MENU_REMIND) && canForward(chat, message)) {
            add(icons, items, options, REMIND, R.drawable.msg_calendar2, tr("Напомнить", "Remind Me"));
        }
        if (Opt.menu(Opt.MENU_REPEAT) && canForward(chat, message) && canWrite(chat)) {
            add(icons, items, options, REPEAT, R.drawable.msg_retry, tr("Повторить", "Repeat"));
        }
        if (Opt.menu(Opt.MENU_COPY_PHOTO) && isCopyablePhoto(chat, message)) {
            add(icons, items, options, COPY_PHOTO, R.drawable.msg_gallery, tr("Копировать фото", "Copy Photo"));
        }
        if (Opt.menu(Opt.MENU_STICKER) && isStillSticker(chat, message)) {
            add(icons, items, options, STICKER, R.drawable.msg_sticker, tr("Сохранить как картинку", "Save as Picture"));
        }
        if (isPluginFile(message)) {
            add(icons, items, options, PLUGIN, org.ngram.messenger.R.drawable.ngram_set_plugins, tr("Установить плагин", "Install Plugin"));
        }
        if (Opt.menu(Opt.MENU_DETAILS)) {
            add(icons, items, options, DETAILS, R.drawable.msg_info, tr("Подробности", "Details"));
        }
    }

    private static boolean isPluginFile(MessageObject m) {
        final String name = m.getDocumentName();
        return m.getDocument() != null && name != null && name.toLowerCase().endsWith(".plugin") && m.getDocument().size < 1024 * 1024;
    }

    private static void installPlugin(ChatActivity chat, MessageObject message) {
        final File file = FileLoader.getInstance(chat.getCurrentAccount()).getPathToMessage(message.messageOwner);
        if (file == null || !file.exists()) {
            note(chat, R.raw.error, tr("Сначала скачайте файл: нажмите на него.", "Download the file first: tap it."));
            return;
        }
        org.ngram.messenger.ui.PluginsFragment.offerInstall(chat, file);
    }

    private static void add(ArrayList<Integer> icons, ArrayList<CharSequence> items, ArrayList<Integer> options, int option, int icon, String title) {
        items.add(title);
        options.add(option);
        icons.add(icon);
    }

    /** True when the option was one of these. */
    public static boolean onMessageMenu(ChatActivity chat, int option, MessageObject message) {
        switch (option) {
            case DETAILS:
                Details.showMessage(chat, message);
                return true;
            case REPEAT:
                repeat(chat, message);
                return true;
            case SAVE:
                save(chat, message);
                return true;
            case COPY_PHOTO:
                copyPhoto(chat, message);
                return true;
            case STICKER:
                stickerToPicture(chat, message);
                return true;
            case PLUGIN:
                installPlugin(chat, message);
                return true;
            case REMIND:
                remind(chat, message);
                return true;
        }
        return false;
    }

    /**
     * Asks when, then has Telegram deliver the message to Saved Messages at that time: a
     * reminder of Telegram's own kind, set from the message instead of from Saved Messages.
     */
    static void remind(ChatActivity chat, MessageObject message) {
        if (chat.getContext() == null || !canForward(chat, message)) {
            return;
        }
        final java.util.Calendar now = java.util.Calendar.getInstance();
        final ArrayList<CharSequence> names = new ArrayList<>();
        final ArrayList<Long> times = new ArrayList<>();
        names.add(tr("Через час", "In an Hour"));
        times.add(now.getTimeInMillis() + 3600_000L);
        names.add(tr("Через три часа", "In Three Hours"));
        times.add(now.getTimeInMillis() + 3 * 3600_000L);
        if (now.get(java.util.Calendar.HOUR_OF_DAY) < 19) {
            names.add(tr("Сегодня в 20:00", "Today at 8 PM"));
            times.add(at(now, 0, 20));
        }
        names.add(tr("Завтра в 9:00", "Tomorrow at 9 AM"));
        times.add(at(now, 1, 9));
        names.add(tr("Послезавтра в 9:00", "The Day After Tomorrow at 9 AM"));
        times.add(at(now, 2, 9));
        names.add(tr("Через неделю в 9:00", "In a Week at 9 AM"));
        times.add(at(now, 7, 9));
        final AlertDialog.Builder builder = new AlertDialog.Builder(chat.getContext(), chat.getResourceProvider());
        builder.setTitle(tr("Когда напомнить?", "Remind Me When?"));
        builder.setItems(names.toArray(new CharSequence[0]), (dialog, which) -> {
            final long self = chat.getUserConfig().getClientUserId();
            final int date = (int) (times.get(which) / 1000L);
            final int failed = chat.getSendMessagesHelper().sendMessage(withAlbum(chat, message), self, false, false, true, date, 0);
            note(chat, failed == 0 ? R.raw.saved_messages : R.raw.error, failed == 0
                ? tr("Напомню: ", "Reminder set: ") + names.get(which).toString().toLowerCase() + tr(". Сообщение придёт в «Избранное».", ". The message will arrive in Saved Messages.")
                : tr("Не удалось поставить напоминание.", "Could not set the reminder."));
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        chat.showDialog(builder.create());
    }

    /** The given hour of the day that is {@code days} days from now, in milliseconds. */
    private static long at(java.util.Calendar now, int days, int hour) {
        final java.util.Calendar c = (java.util.Calendar) now.clone();
        c.add(java.util.Calendar.DAY_OF_YEAR, days);
        c.set(java.util.Calendar.HOUR_OF_DAY, hour);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        return c.getTimeInMillis();
    }

    // ---- what may be done with a message ------------------------------------------------

    /** The same test Telegram applies before it offers "Forward". */
    static boolean canForward(ChatActivity chat, MessageObject m) {
        return m != null && m.messageOwner != null && m.getId() > 0 && !m.isSponsored() && m.messageOwner.action == null
            && !chat.isSecretChat() && !chat.isInScheduleMode() && !chat.isPeerNoForwards() && !m.messageOwner.noforwards
            && m.type != MessageObject.TYPE_PAID_MEDIA && !m.isSecretMedia() && m.canForwardMessage();
    }

    static boolean canWrite(ChatActivity chat) {
        if (chat.getChatActivityEnterView() == null || chat.getChatActivityEnterView().getVisibility() != View.VISIBLE) {
            return false;
        }
        final TLRPC.Chat c = chat.getCurrentChat();
        return c == null || ChatObject.canSendMessages(c);
    }

    private static boolean isCopyablePhoto(ChatActivity chat, MessageObject m) {
        return m.type == MessageObject.TYPE_PHOTO && !chat.isSecretChat() && !chat.isPeerNoForwards() && !m.messageOwner.noforwards && !m.isSecretMedia();
    }

    private static boolean isStillSticker(ChatActivity chat, MessageObject m) {
        return m.isSticker() && !m.isAnimatedSticker() && !m.isVideoSticker() && !chat.isSecretChat() && !chat.isPeerNoForwards() && !m.messageOwner.noforwards;
    }

    /** The message, or its whole album when it is part of one. */
    private static ArrayList<MessageObject> withAlbum(ChatActivity chat, MessageObject message) {
        final ArrayList<MessageObject> list = new ArrayList<>();
        final MessageObject.GroupedMessages group = chat.getValidGroupedMessage(message);
        if (group != null && group.messages != null && !group.messages.isEmpty()) {
            list.addAll(group.messages);
        } else {
            list.add(message);
        }
        return list;
    }

    static String textOf(MessageObject m) {
        if (m == null || m.messageOwner == null) {
            return "";
        }
        if (!TextUtils.isEmpty(m.messageOwner.message)) {
            return m.messageOwner.message;
        }
        return m.caption == null ? "" : m.caption.toString();
    }

    // ---- actions ------------------------------------------------------------------------

    /** Sends the same message into this chat once more, as the user's own. */
    static void repeat(ChatActivity chat, MessageObject message) {
        if (!canForward(chat, message) || !canWrite(chat)) {
            note(chat, R.raw.error, tr("Это сообщение здесь нельзя повторить.", "This message cannot be repeated here."));
            return;
        }
        final ArrayList<MessageObject> list = withAlbum(chat, message);
        AlertsCreator.ensurePaidMessageConfirmation(chat.getCurrentAccount(), chat.getDialogId(), list.size(), payStars -> {
            final int failed = chat.getSendMessagesHelper().sendMessage(list, chat.getDialogId(), true, false, true, 0, chat.getThreadMessage(), -1, payStars);
            if (failed != 0) {
                note(chat, R.raw.error, tr("В этом чате такие сообщения отправлять нельзя.", "Messages of this kind cannot be sent in this chat."));
            }
        });
    }

    /** Forwards the message to Saved Messages in one tap. */
    static void save(ChatActivity chat, MessageObject message) {
        if (!canForward(chat, message)) {
            note(chat, R.raw.error, tr("Это сообщение нельзя сохранить: пересылка запрещена.", "This message cannot be saved: forwarding is restricted."));
            return;
        }
        final long self = chat.getUserConfig().getClientUserId();
        final int failed = chat.getSendMessagesHelper().sendMessage(withAlbum(chat, message), self, false, false, true, 0, 0);
        if (failed == 0) {
            note(chat, R.raw.saved_messages, tr("Сохранено в Избранное", "Saved to Saved Messages"));
        } else {
            note(chat, R.raw.error, tr("Не удалось сохранить.", "Could not save."));
        }
    }

    /** Puts the photo itself on the clipboard, to paste into another app. */
    static void copyPhoto(ChatActivity chat, MessageObject message) {
        final Context context = chat.getContext();
        if (context == null || !isCopyablePhoto(chat, message)) {
            return;
        }
        final File source = FileLoader.getInstance(chat.getCurrentAccount()).getPathToMessage(message.messageOwner);
        if (source == null || !source.exists()) {
            note(chat, R.raw.error, tr("Фото ещё не загружено. Откройте его и повторите.", "The photo is not downloaded yet. Open it and try again."));
            return;
        }
        final File copy = Share.file("photo_" + message.getId() + ".jpg");
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok;
            try {
                ok = AndroidUtilities.copyFile(source, copy);
            } catch (Throwable e) {
                FileLog.e(e);
                ok = false;
            }
            final boolean done = ok;
            AndroidUtilities.runOnUIThread(() -> {
                if (chat.getContext() == null) {
                    return;
                }
                if (!done) {
                    note(chat, R.raw.error, tr("Не удалось скопировать фото.", "Could not copy the photo."));
                    return;
                }
                try {
                    final ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newUri(context.getContentResolver(), "photo", Share.uri(copy)));
                    if (AndroidUtilities.shouldShowClipboardToast()) {
                        note(chat, R.raw.copy, tr("Фото скопировано", "Photo copied"));
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                    note(chat, R.raw.error, tr("Не удалось скопировать фото.", "Could not copy the photo."));
                }
            });
        });
    }

    /** Saves a still sticker to the gallery as a PNG picture with a clear background. */
    static void stickerToPicture(ChatActivity chat, MessageObject message) {
        final Context context = chat.getContext();
        if (context == null || !isStillSticker(chat, message) || !storageAllowed(chat)) {
            return;
        }
        final File source = FileLoader.getInstance(chat.getCurrentAccount()).getPathToMessage(message.messageOwner);
        if (source == null || !source.exists()) {
            note(chat, R.raw.error, tr("Стикер ещё не загружен.", "The sticker is not downloaded yet."));
            return;
        }
        final TLRPC.Document document = message.getDocument();
        final File picture = Share.file("sticker_" + (document == null ? message.getId() : document.id) + ".png");
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = false;
            try {
                final Bitmap bitmap = BitmapFactory.decodeFile(source.getAbsolutePath());
                if (bitmap != null) {
                    try (FileOutputStream out = new FileOutputStream(picture)) {
                        ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                    bitmap.recycle();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final boolean done = ok;
            AndroidUtilities.runOnUIThread(() -> {
                if (chat.getContext() == null) {
                    return;
                }
                if (!done) {
                    note(chat, R.raw.error, tr("Не удалось превратить стикер в картинку.", "Could not turn the sticker into a picture."));
                    return;
                }
                MediaController.saveFile(picture.getAbsolutePath(), context, 0, null, "image/png", uri -> {
                    if (chat.getContext() != null) {
                        BulletinFactory.of(chat).createSimpleBulletin(R.raw.ic_save_to_gallery,
                            tr("Картинка сохранена в галерею", "Picture saved to the gallery"),
                            tr("Поделиться", "Share"),
                            () -> Share.send(context, picture, "image/png", tr("Стикер", "Sticker"))).show();
                    }
                });
            });
        });
    }

    /** Before Android 10 saving to the gallery needs the storage permission. */
    private static boolean storageAllowed(ChatActivity chat) {
        final Activity activity = chat.getParentActivity();
        if (activity == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT <= 28 && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
            return false;
        }
        return true;
    }

    static void note(ChatActivity chat, int icon, String text) {
        if (chat.getContext() != null) {
            BulletinFactory.of(chat).createSimpleBulletin(icon, text).show();
        }
    }

    // ---- double tap ---------------------------------------------------------------------

    public static void onDoubleTap(ChatActivity chat, MessageObject message) {
        if (message == null || message.messageOwner == null || message.isSponsored()) {
            return;
        }
        switch (NgramConfig.doubleTap) {
            case NgramConfig.TAP_REPLY:
                reply(chat, message);
                break;
            case NgramConfig.TAP_COPY: {
                final String text = textOf(message);
                if (text.isEmpty() || chat.isPeerNoForwards() || message.messageOwner.noforwards) {
                    return;
                }
                if (AndroidUtilities.addToClipboard(text) && AndroidUtilities.shouldShowClipboardToast()) {
                    note(chat, R.raw.copy, tr("Текст скопирован", "Text copied"));
                }
                break;
            }
            case NgramConfig.TAP_EDIT:
                // Your own message opens for editing; anyone else's gets a reply.
                if (message.isOut() && message.getId() > 0 && !chat.isInScheduleMode() && message.canEditMessage(chat.getCurrentChat())) {
                    chat.ngramRunOption(message, ChatActivity.OPTION_EDIT);
                } else {
                    reply(chat, message);
                }
                break;
            case NgramConfig.TAP_SAVE:
                if (chat.getDialogId() != chat.getUserConfig().getClientUserId()) {
                    save(chat, message);
                }
                break;
            case NgramConfig.TAP_DETAILS:
                Details.showMessage(chat, message);
                break;
        }
    }

    private static void reply(ChatActivity chat, MessageObject message) {
        if (message.getId() > 0 && message.messageOwner.action == null && canWrite(chat) && !chat.isInScheduleMode()) {
            chat.showFieldPanelForReply(message);
        }
    }

    public static String doubleTapName(int action) {
        switch (action) {
            case NgramConfig.TAP_REPLY:
                return tr("Ответить", "Reply");
            case NgramConfig.TAP_COPY:
                return tr("Скопировать текст", "Copy Text");
            case NgramConfig.TAP_EDIT:
                return tr("Изменить своё, ответить на чужое", "Edit Mine, Reply to Others");
            case NgramConfig.TAP_SAVE:
                return tr("В Избранное", "To Saved Messages");
            case NgramConfig.TAP_DETAILS:
                return tr("Подробности", "Details");
            case NgramConfig.TAP_NONE:
                return tr("Ничего", "Nothing");
            default:
                return tr("Реакция", "Reaction");
        }
    }

    // ---- the header menu of a chat ------------------------------------------------------

    public static CharSequence chatMenuTitle(ChatActivity chat) {
        return chat.getChatMode() == 0 ? "Ngram" : null;
    }

    public static void onChatMenu(ChatActivity chat) {
        if (chat.getContext() == null) {
            return;
        }
        final ArrayList<CharSequence> names = new ArrayList<>();
        final ArrayList<Integer> icons = new ArrayList<>();
        final ArrayList<Runnable> actions = new ArrayList<>();
        if (Ai.inChat && !chat.isSecretChat() && !chat.isPeerNoForwards()) {
            names.add(tr("Ассистент", "Assistant"));
            icons.add(org.ngram.messenger.R.drawable.ngram_set_ai);
            actions.add(() -> AiInChat.onChatMenu(chat));
        }
        if (canWrite(chat)) {
            names.add(tr("Шаблоны", "Templates"));
            icons.add(R.drawable.msg_message);
            actions.add(() -> Templates.pick(chat));
        }
        if (!chat.isSecretChat()) {
            names.add(tr("К началу чата", "To the Start of the Chat"));
            icons.add(R.drawable.msg_go_up);
            // The earliest date there is: Telegram loads the first messages of the chat.
            actions.add(() -> chat.jumpToDate(86400 * 2));
        }
        names.add(tr("Разбор чата", "Chat Breakdown"));
        icons.add(R.drawable.msg_stats);
        actions.add(() -> ChatStats.show(chat));
        names.add(tr("Экспорт переписки", "Export Chat"));
        icons.add(R.drawable.msg_shareout);
        actions.add(() -> ChatExport.start(chat));
        PluginMenus.addChatItems(chat, names, icons, actions);

        final int[] iconArray = new int[icons.size()];
        for (int i = 0; i < iconArray.length; i++) {
            iconArray[i] = icons.get(i);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(chat.getContext(), chat.getResourceProvider());
        builder.setTitle("Ngram");
        builder.setItems(names.toArray(new CharSequence[0]), iconArray, (dialog, which) -> {
            if (which >= 0 && which < actions.size()) {
                actions.get(which).run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        chat.showDialog(builder.create());
    }

    // ---- asking first -------------------------------------------------------------------

    public static void confirm(Context context, int kind, CharSequence subject, Runnable action) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final String yes;
        switch (kind) {
            case NgramHooks.CONFIRM_CALL:
                builder.setTitle(tr("Позвонить?", "Call?"));
                builder.setMessage(tr("Начать звонок: ", "Start a call with ") + subject + tr("?", "?"));
                yes = tr("Позвонить", "Call");
                break;
            case NgramHooks.CONFIRM_VIDEO_CALL:
                builder.setTitle(tr("Видеозвонок?", "Video Call?"));
                builder.setMessage(tr("Начать видеозвонок: ", "Start a video call with ") + subject + tr("?", "?"));
                yes = tr("Позвонить", "Call");
                break;
            case NgramHooks.CONFIRM_STICKER:
                builder.setTitle(tr("Отправить стикер?", "Send the Sticker?"));
                builder.setMessage(tr("Вы просили спрашивать перед отправкой стикеров.", "You asked to be asked before a sticker is sent."));
                yes = tr("Отправить", "Send");
                break;
            case NgramHooks.CONFIRM_GIF:
                builder.setTitle(tr("Отправить GIF?", "Send the GIF?"));
                builder.setMessage(tr("Вы просили спрашивать перед отправкой GIF.", "You asked to be asked before a GIF is sent."));
                yes = tr("Отправить", "Send");
                break;
            default:
                action.run();
                return;
        }
        builder.setPositiveButton(yes, (dialog, which) -> action.run());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }
}
