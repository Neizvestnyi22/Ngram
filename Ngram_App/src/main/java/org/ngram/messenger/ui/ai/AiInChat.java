package org.ngram.messenger.ui.ai;

import static org.ngram.messenger.N.tr;

import android.text.TextUtils;

import org.ngram.core.NgramHooks;
import org.ngram.messenger.R;
import org.ngram.messenger.ai.Ai;
import org.ngram.messenger.ai.AiTasks;
import org.ngram.messenger.ui.Prompt;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

/**
 * The assistant inside a chat: one entry in the menu of a message and one in the menu of the
 * chat. Text leaves the device only after the user picks an action here, and the very first
 * time only after they agree to that.
 */
public final class AiInChat {

    private static final int OPTION = NgramHooks.MESSAGE_OPTION_BASE + 1;

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

    public static void fillMessageMenu(MessageObject message, ArrayList<Integer> icons, ArrayList<CharSequence> items, ArrayList<Integer> options) {
        if (!Ai.inChat || textOf(message).trim().isEmpty()) {
            return;
        }
        items.add(tr("Ассистент", "Assistant"));
        options.add(OPTION);
        icons.add(R.drawable.ngram_set_ai);
    }

    public static CharSequence chatMenuTitle() {
        return Ai.inChat ? tr("Ассистент", "Assistant") : null;
    }

    /** Runs the action once a provider exists and the user has agreed to send text to it. */
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
        builder.setTitle(tr("Отправить текст провайдеру?", "Send Text to the Provider?"));
        builder.setMessage(tr(
            "Выбранные сообщения будут отправлены сервису «" + provider.title() + "» с вашим ключом. Их увидит этот сервис, а не только вы и собеседник. Ngram спросит об этом один раз.",
            "The chosen messages will be sent to " + provider.title() + " with your key. That service will see them, not only you and the other side. Ngram asks this once."));
        builder.setPositiveButton(tr("Отправлять", "Send"), (d, w) -> {
            Ai.consent = true;
            Ai.save();
            action.run();
        });
        builder.setNegativeButton(LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
        chat.showDialog(builder.create());
    }

    public static void onMessageMenu(ChatActivity chat, int option, MessageObject message) {
        if (option != OPTION || chat.getContext() == null) {
            return;
        }
        final String text = textOf(message).trim();
        if (text.isEmpty()) {
            return;
        }
        final ArrayList<CharSequence> names = new ArrayList<>();
        final ArrayList<Runnable> actions = new ArrayList<>();
        names.add(tr("Объяснить", "Explain"));
        actions.add(() -> AiSheet.run(chat, tr("Объяснение", "Explanation"), AiTasks.explain(text), null));
        names.add(tr("Перевести", "Translate"));
        actions.add(() -> AiSheet.run(chat, tr("Перевод", "Translation"), AiTasks.translate(text), null));
        if (text.length() > 280) {
            names.add(tr("Кратко", "Summarize"));
            actions.add(() -> AiSheet.run(chat, tr("Кратко", "Summary"), AiTasks.summarizeText(text), null));
        }
        names.add(tr("Спросить о сообщении…", "Ask About the Message…"));
        actions.add(() -> Prompt.show(chat, tr("Вопрос о сообщении", "Question About the Message"), null, tr("Что вы хотите узнать?", "What do you want to know?"), "", Prompt.MULTILINE, question -> {
            if (!question.isEmpty()) {
                AiSheet.run(chat, tr("Ответ", "Answer"), AiTasks.askAbout(text, question), null);
            }
        }));
        if (!message.isOut() && chat.getChatActivityEnterView() != null) {
            names.add(tr("Предложить ответы", "Suggest Replies"));
            actions.add(() -> AiSheet.run(chat, tr("Варианты ответа", "Reply Options"),
                AiTasks.replies(transcript(chat, 20), tr("Я", "Me")), reply -> putIntoField(chat, reply), true));
        }
        choose(chat, names, actions);
    }

    public static void onChatMenu(ChatActivity chat) {
        if (chat.getContext() == null) {
            return;
        }
        final ArrayList<CharSequence> names = new ArrayList<>();
        final ArrayList<Runnable> actions = new ArrayList<>();
        names.add(tr("Пересказать последние сообщения", "Summarize the Latest Messages"));
        actions.add(() -> {
            final String transcript = transcript(chat, Ai.contextSize);
            if (transcript.isEmpty()) {
                return;
            }
            AiSheet.run(chat, tr("Пересказ чата", "Chat Summary"), AiTasks.summarizeChat(transcript), null);
        });
        names.add(tr("Спросить о чате…", "Ask About the Chat…"));
        actions.add(() -> Prompt.show(chat, tr("Вопрос о чате", "Question About the Chat"), null, tr("Например: о чём договорились?", "For example: what was agreed?"), "", Prompt.MULTILINE, question -> {
            final String transcript = transcript(chat, Ai.contextSize);
            if (!question.isEmpty() && !transcript.isEmpty()) {
                AiSheet.run(chat, tr("Ответ", "Answer"), AiTasks.askChat(transcript, question), null);
            }
        }));
        if (chat.getChatActivityEnterView() != null) {
            final CharSequence draft = chat.getChatActivityEnterView().getFieldText();
            if (draft != null && draft.toString().trim().length() > 0) {
                final String mine = draft.toString().trim();
                for (AiTasks.Rewrite r : AiTasks.REWRITES) {
                    names.add(tr("Мой текст: ", "My text: ") + r.title().toLowerCase());
                    actions.add(() -> AiSheet.run(chat, r.title(), AiTasks.rewrite(mine, r), result -> putIntoField(chat, result)));
                }
            }
        }
        choose(chat, names, actions);
    }

    private static void choose(ChatActivity chat, ArrayList<CharSequence> names, ArrayList<Runnable> actions) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(chat.getContext(), chat.getResourceProvider());
        builder.setTitle(tr("Ассистент", "Assistant"));
        builder.setItems(names.toArray(new CharSequence[0]), (dialog, which) -> {
            if (which >= 0 && which < actions.size()) {
                // Asking a question opens its own dialog first; the guard runs before any text leaves.
                guarded(chat, actions.get(which));
            }
        });
        builder.setNegativeButton(LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
        chat.showDialog(builder.create());
    }


    private static void putIntoField(ChatActivity chat, String text) {
        if (chat.getChatActivityEnterView() != null && !TextUtils.isEmpty(text)) {
            chat.getChatActivityEnterView().setFieldText(text);
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
            if (text.isEmpty() || m.messageOwner == null || m.messageOwner.action != null) {
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
