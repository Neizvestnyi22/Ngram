package org.ngram.messenger.ui.ai;

import static org.ngram.messenger.N.tr;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.view.View;
import android.widget.TextView;

import org.ngram.messenger.ai.Ai;
import org.ngram.messenger.ai.AiTalk;
import org.ngram.messenger.ui.NgramFragment;
import org.ngram.messenger.ui.Prompt;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * The conversation with the assistant, laid out with Telegram's list kit: every message is a
 * section, and the question is typed in a dialog.
 */
public class AiChatFragment extends NgramFragment implements AiTalk.Listener {

    private static final int ASK = 1;
    private static final int STOP = 2;
    private static final int RETRY = 3;
    private static final int NEW = 4;
    private static final int SETTINGS = 5;
    private static final int COPY_LAST = 6;

    private final IdentityHashMap<AiTalk.Message, TextView> views = new IdentityHashMap<>();
    /** Opened from the settings screen: its row there would only lead back and forth. */
    private boolean fromSettings;

    public AiChatFragment() {
    }

    public AiChatFragment(boolean fromSettings) {
        this.fromSettings = fromSettings;
    }

    @Override
    protected CharSequence getTitle() {
        return tr("Ассистент", "Assistant");
    }

    @Override
    public boolean onFragmentCreate() {
        AiTalk.setListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        AiTalk.setListener(null);
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private TextView viewFor(AiTalk.Message m) {
        TextView view = views.get(m);
        if (view == null) {
            view = AiText.view(getContext(), getResourceProvider());
            view.setPadding(dp(21), dp(4), dp(21), dp(14));
            views.put(m, view);
        }
        view.setTextColor(Theme.getColor(m.error ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()));
        final String text = m.streaming && m.text.isEmpty() ? "…" : m.text;
        AiText.set(view, text, m.assistant && !m.error);
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final List<AiTalk.Message> messages = AiTalk.messages();
        final boolean generating = AiTalk.generating();
        final Ai.Provider provider = Ai.active();

        if (messages.isEmpty()) {
            items.add(UItem.asShadow(Ai.ready()
                ? tr("Спросите о чём угодно: объяснить, написать, перевести, придумать. Разговор хранится только на этом устройстве.",
                     "Ask anything: explain, write, translate, brainstorm. The conversation is kept on this device only.")
                : tr("Ассистент работает с вашим ключом API. Подключите провайдера — OpenAI, Claude, Gemini, OpenRouter или свой сервер — и выберите модель.",
                     "The assistant works with your own API key. Connect a provider — OpenAI, Claude, Gemini, OpenRouter or your own server — and pick a model.")));
        }

        int id = 1000;
        for (AiTalk.Message m : messages) {
            final String who = m.assistant
                ? tr("Ассистент", "Assistant") + (m.model.isEmpty() ? "" : " · " + m.model)
                : tr("Вы", "You");
            items.add(UItem.asHeader(id++, who));
            items.add(UItem.asCustom(viewFor(m), LayoutHelper.WRAP_CONTENT));
            items.add(UItem.asShadow(id++, null));
        }

        if (generating) {
            items.add(UItem.asButton(STOP, R.drawable.msg_cancel, tr("Остановить", "Stop")).red());
        } else if (Ai.ready()) {
            items.add(UItem.asButton(ASK, R.drawable.msg_edit, messages.isEmpty() ? tr("Задать вопрос", "Ask a Question") : tr("Написать ещё", "Write More")).accent());
            if (!messages.isEmpty()) {
                items.add(UItem.asButton(COPY_LAST, R.drawable.msg_copy, tr("Скопировать последний ответ", "Copy the Last Answer")));
                items.add(UItem.asButton(RETRY, R.drawable.msg_retry, tr("Ответить заново", "Answer Again")));
                items.add(UItem.asButton(NEW, R.drawable.msg_clear, tr("Новый разговор", "New Conversation")));
            }
        }
        if (!fromSettings) {
            items.add(UItem.asButton(SETTINGS, R.drawable.msg_settings, tr("Настройки ассистента", "Assistant Settings"),
                provider == null ? tr("нет провайдера", "no provider") : (provider.model.isEmpty() ? provider.title() : provider.model)));
        }
        items.add(UItem.asShadow(tr(
            "Ответы пишет выбранная вами модель; они могут быть неточными.",
            "Answers are written by the model you chose and may be inaccurate.")));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ASK:
                Prompt.show(this, tr("Вопрос ассистенту", "Ask the Assistant"), null, tr("Сообщение", "Message"), "", Prompt.MULTILINE, text -> {
                    if (!text.isEmpty()) {
                        AiTalk.ask(text);
                    }
                });
                break;
            case STOP:
                AiTalk.stop();
                break;
            case RETRY:
                AiTalk.regenerate();
                break;
            case NEW:
                views.clear();
                AiTalk.clear();
                break;
            case SETTINGS:
                presentFragment(new AiSettingsFragment());
                break;
            case COPY_LAST: {
                final List<AiTalk.Message> messages = AiTalk.messages();
                for (int i = messages.size() - 1; i >= 0; i--) {
                    final AiTalk.Message m = messages.get(i);
                    if (m.assistant && !m.error) {
                        if (AndroidUtilities.addToClipboard(m.text)) {
                            BulletinFactory.of(this).createCopyBulletin(tr("Ответ скопирован", "Answer copied")).show();
                        }
                        break;
                    }
                }
                break;
            }
        }
    }

    @Override
    public void onChanged() {
        if (listView == null) {
            return;
        }
        views.keySet().retainAll(AiTalk.messages());
        refresh();
        listView.post(() -> {
            if (listView != null && listView.adapter != null && listView.adapter.getItemCount() > 0) {
                listView.smoothScrollToPosition(listView.adapter.getItemCount() - 1);
            }
        });
    }

    @Override
    public void onText(AiTalk.Message message) {
        final TextView view = views.get(message);
        if (view != null) {
            AiText.set(view, message.text, true);
        }
    }
}
