package org.ngram.messenger.ui.ai;

import static org.ngram.messenger.N.tr;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;

import org.ngram.core.NgramHooks;
import org.ngram.messenger.R;
import org.ngram.messenger.ai.Ai;
import org.ngram.messenger.ai.AiTasks;
import org.ngram.messenger.ui.Prompt;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;

/**
 * The assistant inside a chat: one entry in the menu of a message and one in the menu of the
 * chat. Text, a picture or a recording leaves the device only after the user picks an action
 * here, and the very first time only after they agree to that.
 *
 * Secret chats and chats whose owner has switched copying off are left alone: what may not be
 * copied out of Telegram is not sent to a third service either.
 */
public final class AiInChat {

    private static final int OPTION = NgramHooks.MESSAGE_OPTION_BASE + 1;
    /** The longest side of a picture sent to a model: enough to read text on it. */
    private static final int IMAGE_SIDE = 1568;

    private AiInChat() {
    }

    private static String textOf(MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return "";
        }
        if (!TextUtils.isEmpty(message.messageOwner.message)) {
            return message.messageOwner.message;
        }
        if (!TextUtils.isEmpty(message.caption)) {
            return message.caption.toString();
        }
        return "";
    }

    /** False for chats and messages that must not leave Telegram. */
    private static boolean allowed(ChatActivity chat, MessageObject message) {
        if (chat.isSecretChat() || chat.isPeerNoForwards()) {
            return false;
        }
        return message == null || message.messageOwner != null && !message.messageOwner.noforwards && !message.isSecretMedia() && !message.isSponsored();
    }

    private static boolean isPhoto(MessageObject message) {
        return message.type == MessageObject.TYPE_PHOTO;
    }

    private static boolean isSpeech(MessageObject message) {
        return message.isVoice() || message.isRoundVideo();
    }

    public static void fillMessageMenu(ChatActivity chat, MessageObject message, ArrayList<Integer> icons, ArrayList<CharSequence> items, ArrayList<Integer> options) {
        if (!Ai.inChat || !allowed(chat, message)) {
            return;
        }
        if (textOf(message).trim().isEmpty() && !isPhoto(message) && !isSpeech(message)) {
            return;
        }
        items.add(tr("Ассистент", "Assistant"));
        options.add(OPTION);
        icons.add(R.drawable.ngram_set_ai);
    }

    /** Runs the action once a provider exists and the user has agreed to send content to it. */
    private static void guarded(ChatActivity chat, Runnable action) {
        if (chat.getContext() == null) {
            return;
        }
        final Ai.Provider provider = Ai.active();
        if (provider == null || !provider.ready()) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(chat.getContext(), chat.getResourceProvider());
            builder.setTitle(tr("Ассистент не настроен", "The Assistant Is Not Set Up"));
            builder.setMessage(tr("Подключите провайдера и выберите модель — понадобится ваш ключ API.", "Connect a provider and pick a model — you will need your own API key."));
            builder.setPositiveButton(tr("Настроить", "Set Up"), (d, w) -> chat.presentFragment(new AiSettingsFragment()));
            builder.setNegativeButton(LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
            chat.showDialog(builder.create());
            return;
        }
        if (Ai.consent) {
            action.run();
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(chat.getContext(), chat.getResourceProvider());
        builder.setTitle(tr("Отправить провайдеру?", "Send to the Provider?"));
        builder.setMessage(tr(
            "Выбранные сообщения, картинки и записи будут отправляться сервису «" + provider.title() + "» с вашим ключом. Их увидит этот сервис, а не только вы и собеседник. Ngram спросит об этом один раз.",
            "The messages, pictures and recordings you choose will be sent to " + provider.title() + " with your key. That service will see them, not only you and the other side. Ngram asks this once."));
        builder.setPositiveButton(tr("Отправлять", "Send"), (d, w) -> {
            Ai.consent = true;
            Ai.save();
            action.run();
        });
        builder.setNegativeButton(LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
        chat.showDialog(builder.create());
    }

    public static void onMessageMenu(ChatActivity chat, int option, MessageObject message) {
        if (option != OPTION || chat.getContext() == null || !allowed(chat, message)) {
            return;
        }
        final String text = textOf(message).trim();
        final ArrayList<AiPanel.Action> actions = new ArrayList<>();

        if (!text.isEmpty()) {
            if (text.length() > 280) {
                add(chat, actions, org.telegram.messenger.R.drawable.msg_list, tr("Суть", "The Gist"),
                    () -> AiSheet.run(chat, tr("Суть", "The Gist"), AiTasks.summarizeText(text), null));
            }
            add(chat, actions, org.telegram.messenger.R.drawable.msg_info, tr("Объяснить", "Explain"),
                () -> AiSheet.run(chat, tr("Объяснение", "Explanation"), AiTasks.explain(text), null));
            add(chat, actions, org.telegram.messenger.R.drawable.msg_translate, tr("Перевод ассистентом", "Assistant Translation"),
                () -> AiSheet.run(chat, tr("Перевод ассистентом", "Assistant Translation"), AiTasks.translate(text), null));
            if (!message.isOut() && chat.getChatActivityEnterView() != null) {
                add(chat, actions, org.telegram.messenger.R.drawable.msg_discussion, tr("Варианты ответа", "Reply Options"),
                    () -> AiSheet.run(chat, tr("Варианты ответа", "Reply Options"),
                        AiTasks.replies(transcript(chat, 20), tr("Я", "Me")), reply -> putIntoField(chat, reply), true));
            }
        }
        if (isPhoto(message)) {
            add(chat, actions, org.telegram.messenger.R.drawable.msg_photos, tr("Что на картинке", "What Is in the Picture"),
                () -> withImage(chat, message, image -> AiSheet.runWithImage(chat, tr("Картинка", "Picture"), AiTasks.describeImage(), image)));
            add(chat, actions, org.telegram.messenger.R.drawable.msg_text_check, tr("Текст с картинки", "Text from the Picture"),
                () -> withImage(chat, message, image -> AiSheet.runWithImage(chat, tr("Текст с картинки", "Text from the Picture"), AiTasks.readImage(), image)));
        }
        if (isSpeech(message)) {
            add(chat, actions, org.telegram.messenger.R.drawable.msg_voicechat, tr("Текст из записи", "Text from the Recording"), () -> transcribe(chat, message));
        }

        // One question line for whatever the message has: its picture first, else its text.
        final String askHint;
        final Runnable onAsk;
        if (isPhoto(message)) {
            askHint = tr("Спросить о картинке…", "Ask about the picture…");
            onAsk = () -> Prompt.show(chat, tr("Вопрос о картинке", "Question About the Picture"), null, tr("Что вы хотите узнать?", "What do you want to know?"), "", Prompt.MULTILINE, question -> {
                if (!question.isEmpty()) {
                    guarded(chat, () -> withImage(chat, message, image -> AiSheet.runWithImage(chat, tr("Ответ", "Answer"), AiTasks.askImage(question), image)));
                }
            });
        } else if (!text.isEmpty()) {
            askHint = tr("Спросить о сообщении…", "Ask about the message…");
            onAsk = () -> Prompt.show(chat, tr("Вопрос о сообщении", "Question About the Message"), null, tr("Что вы хотите узнать?", "What do you want to know?"), "", Prompt.MULTILINE, question -> {
                if (!question.isEmpty()) {
                    guarded(chat, () -> AiSheet.run(chat, tr("Ответ", "Answer"), AiTasks.askAbout(text, question), null));
                }
            });
        } else {
            askHint = null;
            onAsk = null;
        }
        if (actions.isEmpty() && onAsk == null) {
            return;
        }
        AiPanel.show(chat, text, actions, askHint, onAsk);
    }

    /** A tile whose action waits for a provider and for the user's consent before anything leaves. */
    private static void add(ChatActivity chat, ArrayList<AiPanel.Action> actions, int icon, String title, Runnable run) {
        actions.add(new AiPanel.Action(icon, title, () -> guarded(chat, run)));
    }

    public static void onChatMenu(ChatActivity chat) {
        if (chat.getContext() == null) {
            return;
        }
        if (!allowed(chat, null)) {
            BulletinFactory.of(chat).createSimpleBulletin(org.telegram.messenger.R.raw.error, tr(
                "В этом чате копирование запрещено, поэтому ассистент здесь не работает.",
                "Copying is switched off in this chat, so the assistant does not work here.")).show();
            return;
        }
        final ArrayList<AiPanel.Action> actions = new ArrayList<>();
        add(chat, actions, org.telegram.messenger.R.drawable.msg_list, tr("Пересказ чата", "Chat Summary"), () -> overChat(chat, tr("Пересказ чата", "Chat Summary"), AiTasks::summarizeChat));
        add(chat, actions, org.telegram.messenger.R.drawable.msg_select, tr("О чём договорились", "What Was Agreed"), () -> overChat(chat, tr("О чём договорились", "What Was Agreed"), AiTasks::decisions));
        add(chat, actions, org.telegram.messenger.R.drawable.msg_markunread, tr("Что ждут от меня", "What Is Asked of Me"), () -> overChat(chat, tr("Что ждут от меня", "What Is Asked of Me"), AiTasks::askedOfMe));
        add(chat, actions, org.telegram.messenger.R.drawable.msg_help, tr("Вопросы без ответа", "Unanswered Questions"), () -> overChat(chat, tr("Вопросы без ответа", "Unanswered Questions"), AiTasks::openQuestions));
        if (chat.getChatActivityEnterView() != null) {
            final CharSequence draft = chat.getChatActivityEnterView().getFieldText();
            if (draft != null && draft.toString().trim().length() > 0) {
                final String mine = draft.toString().trim();
                actions.add(AiPanel.Action.heading(tr("Мой текст в поле ввода", "My Text in the Message Field")));
                for (AiTasks.Rewrite r : AiTasks.REWRITES) {
                    add(chat, actions, org.telegram.messenger.R.drawable.msg_edit, r.title(), () -> AiSheet.run(chat, r.title(), AiTasks.rewrite(mine, r), result -> putIntoField(chat, result)));
                }
            }
        }
        AiPanel.show(chat, null, actions, tr("Спросить о чате…", "Ask about the chat…"),
            () -> Prompt.show(chat, tr("Вопрос о чате", "Question About the Chat"), null, tr("Например: когда созвон?", "For example: when is the call?"), "", Prompt.MULTILINE, question -> {
                final String transcript = transcript(chat, Ai.contextSize);
                if (!question.isEmpty() && !transcript.isEmpty()) {
                    guarded(chat, () -> AiSheet.run(chat, tr("Ответ", "Answer"), AiTasks.askChat(transcript, question), null));
                }
            }));
    }

    private interface OverChat {
        String prompt(String transcript);
    }

    private static void overChat(ChatActivity chat, String title, OverChat task) {
        final String transcript = transcript(chat, Ai.contextSize);
        if (!transcript.isEmpty()) {
            AiSheet.run(chat, title, task.prompt(transcript), null);
        }
    }

    private static void putIntoField(ChatActivity chat, String text) {
        if (chat.getChatActivityEnterView() != null && !TextUtils.isEmpty(text)) {
            chat.getChatActivityEnterView().setFieldText(text);
        }
    }

    // ---- pictures and recordings --------------------------------------------------------

    private static File fileOf(ChatActivity chat, MessageObject message) {
        try {
            final File file = FileLoader.getInstance(chat.getCurrentAccount()).getPathToMessage(message.messageOwner);
            return file != null && file.exists() ? file : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Shrinks the photo of a message off the main thread and hands it over as Base64 JPEG. */
    private static void withImage(ChatActivity chat, MessageObject message, Utilities.Callback<String> then) {
        final File file = fileOf(chat, message);
        if (file == null) {
            notLoaded(chat, tr("Фото ещё не загружено. Откройте его и повторите.", "The photo is not downloaded yet. Open it and try again."));
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            String encoded = null;
            try {
                final BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= IMAGE_SIDE) {
                    options.inSampleSize *= 2;
                }
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (bitmap != null) {
                    final int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
                    if (longest > IMAGE_SIDE) {
                        final float scale = IMAGE_SIDE / (float) longest;
                        final Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
                        if (scaled != bitmap) {
                            bitmap.recycle();
                            bitmap = scaled;
                        }
                    }
                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out);
                    bitmap.recycle();
                    encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final String image = encoded;
            AndroidUtilities.runOnUIThread(() -> {
                if (chat.getContext() == null) {
                    return;
                }
                if (image == null) {
                    notLoaded(chat, tr("Не удалось прочитать фото.", "Could not read the photo."));
                } else {
                    then.run(image);
                }
            });
        });
    }

    private static void transcribe(ChatActivity chat, MessageObject message) {
        final File file = fileOf(chat, message);
        if (file == null) {
            notLoaded(chat, tr("Запись ещё не загружена. Включите её и повторите.", "The recording is not downloaded yet. Play it and try again."));
            return;
        }
        // The extension tells the service what it is given: voice is Ogg Opus, a video message is MP4.
        final String name = message.isRoundVideo() ? "video.mp4" : "voice.ogg";
        AiSheet.runJob(chat, tr("Расшифровка", "Transcript"), listener -> AiTasks.transcribe(file, name, listener), null, false);
    }

    private static void notLoaded(ChatActivity chat, String text) {
        if (chat.getContext() != null) {
            BulletinFactory.of(chat).createSimpleBulletin(org.telegram.messenger.R.raw.error, text).show();
        }
    }

    /** The latest loaded messages of the chat as "Name: text" lines, oldest first. */
    private static String transcript(ChatActivity chat, int limit) {
        final ArrayList<MessageObject> messages = chat.messages;
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        final MessagesController controller = chat.getMessagesController();
        final ArrayList<String> lines = new ArrayList<>();
        // The list is newest first.
        for (int i = 0; i < messages.size() && lines.size() < limit; i++) {
            final MessageObject m = messages.get(i);
            final String text = textOf(m).trim();
            if (text.isEmpty() || m.messageOwner == null || m.messageOwner.action != null || m.messageOwner.noforwards) {
                continue;
            }
            String who;
            if (m.isOut()) {
                who = tr("Я", "Me");
            } else {
                final long sender = m.getSenderId();
                if (sender > 0) {
                    final TLRPC.User user = controller.getUser(sender);
                    who = user == null ? tr("Собеседник", "Them") : UserObject.getFirstName(user);
                } else {
                    final TLRPC.Chat c = controller.getChat(-sender);
                    who = c == null ? tr("Канал", "Channel") : c.title;
                }
            }
            lines.add(who + ": " + (text.length() > 1500 ? text.substring(0, 1500) + "…" : text));
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = lines.size() - 1; i >= 0; i--) {
            sb.append(lines.get(i)).append('\n');
        }
        return sb.toString().trim();
    }
}
