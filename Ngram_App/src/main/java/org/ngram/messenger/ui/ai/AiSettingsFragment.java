package org.ngram.messenger.ui.ai;

import static org.ngram.messenger.N.tr;

import android.view.View;

import org.ngram.messenger.ai.Ai;
import org.ngram.messenger.ui.NgramFragment;
import org.ngram.messenger.ui.Prompt;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.List;

/** The assistant's settings: which providers it may use and how it behaves. */
public class AiSettingsFragment extends NgramFragment {

    private static final int OPEN_CHAT = 1;
    private static final int ADD = 2;
    private static final int SYSTEM_PROMPT = 3;
    private static final int IN_CHAT = 4;
    private static final int PROVIDER_BASE = 100;

    private static final float[] TEMPERATURES = {-1f, 0f, 0.3f, 0.7f, 1f};
    private static final int[] CONTEXT_SIZES = {30, 50, 100, 200, 400};

    private List<Ai.Provider> providers = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return tr("Ассистент", "Assistant");
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        providers = Ai.providers();
        final Ai.Provider active = Ai.active();

        items.add(UItem.asButton(OPEN_CHAT, R.drawable.msg_discussion, tr("Открыть ассистента", "Open the Assistant")).accent());
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(tr("Провайдеры", "Providers")));
        for (int i = 0; i < providers.size(); i++) {
            final Ai.Provider p = providers.get(i);
            final String state = !p.ready()
                ? tr("не настроен", "not set up")
                : (p == active ? "✓ " : "") + p.model;
            items.add(UItem.asButton(PROVIDER_BASE + i, p.title(), state));
        }
        items.add(UItem.asButton(ADD, R.drawable.msg_add, tr("Добавить провайдера", "Add a Provider")).accent());
        items.add(UItem.asShadow(tr(
            "Ассистент работает с вашим собственным ключом API. Запрос уходит с телефона прямо выбранному провайдеру, минуя Telegram и какие-либо серверы Ngram. Ключи хранятся на устройстве в зашифрованном виде.",
            "The assistant works with your own API key. A request goes from the phone straight to the provider you chose, bypassing Telegram and any Ngram servers. Keys are kept encrypted on the device.")));

        items.add(UItem.asHeader(tr("Поведение", "Behaviour")));
        items.add(UItem.asButton(SYSTEM_PROMPT, tr("Постоянные инструкции", "Standing Instructions"),
            Ai.systemPrompt.trim().isEmpty() ? tr("нет", "none") : tr("заданы", "set")));
        items.add(UItem.asShadow(tr(
            "Добавляются к каждому запросу: например, «отвечай коротко» или «обращайся на ты».",
            "Added to every request: for example \"keep answers short\".")));

        items.add(UItem.asHeader(tr("Температура", "Temperature")));
        final String[] labels = new String[TEMPERATURES.length];
        int chosen = 0;
        for (int i = 0; i < TEMPERATURES.length; i++) {
            labels[i] = TEMPERATURES[i] < 0 ? tr("Авто", "Auto") : String.valueOf(TEMPERATURES[i]);
            if (Math.abs(TEMPERATURES[i] - Ai.temperature) < 0.01f) {
                chosen = i;
            }
        }
        items.add(UItem.asSlideView(labels, chosen, index -> {
            Ai.temperature = TEMPERATURES[index];
            Ai.save();
        }));
        items.add(UItem.asShadow(tr(
            "Чем выше, тем свободнее ответы. «Авто» оставляет значение провайдера — некоторые модели другого не принимают.",
            "Higher means freer answers. Auto leaves the provider's own value: some models accept no other.")));

        items.add(UItem.asHeader(tr("В чатах", "In Chats")));
        items.add(UItem.asCheck(IN_CHAT, tr("Действия ассистента в меню", "Assistant Actions in Menus")).setChecked(Ai.inChat));
        items.add(UItem.asShadow(tr(
            "Пункт «Ассистент» в меню сообщения и чата: объяснить, перевести, пересказать, предложить ответ. Текст отправляется провайдеру только когда вы выбираете действие.",
            "An Assistant entry in the menu of a message and of a chat: explain, translate, summarize, suggest a reply. Text is sent to the provider only when you choose an action.")));

        items.add(UItem.asHeader(tr("Сколько сообщений пересказывать", "Messages in a Summary")));
        final String[] sizes = new String[CONTEXT_SIZES.length];
        int size = 2;
        for (int i = 0; i < CONTEXT_SIZES.length; i++) {
            sizes[i] = String.valueOf(CONTEXT_SIZES[i]);
            if (CONTEXT_SIZES[i] == Ai.contextSize) {
                size = i;
            }
        }
        items.add(UItem.asSlideView(sizes, size, index -> {
            Ai.contextSize = CONTEXT_SIZES[index];
            Ai.save();
        }));
        items.add(UItem.asShadow(tr(
            "Берутся последние загруженные сообщения чата. Больше сообщений — дороже запрос.",
            "The latest loaded messages of the chat are taken. More messages cost more.")));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == OPEN_CHAT) {
            presentFragment(new AiChatFragment(true));
        } else if (item.id == ADD) {
            addProvider();
        } else if (item.id == SYSTEM_PROMPT) {
            Prompt.show(this, tr("Постоянные инструкции", "Standing Instructions"), null,
                tr("Например: отвечай коротко", "For example: keep answers short"), Ai.systemPrompt, Prompt.MULTILINE, text -> {
                    Ai.systemPrompt = text;
                    Ai.save();
                    refresh();
                });
        } else if (item.id == IN_CHAT) {
            Ai.inChat = !Ai.inChat;
            Ai.save();
            refresh();
        } else if (item.id >= PROVIDER_BASE && item.id - PROVIDER_BASE < providers.size()) {
            presentFragment(new AiProviderFragment(providers.get(item.id - PROVIDER_BASE).id));
        }
    }

    private void addProvider() {
        final CharSequence[] names = new CharSequence[Ai.PRESETS.length + 1];
        for (int i = 0; i < Ai.PRESETS.length; i++) {
            names[i] = Ai.PRESETS[i].name;
        }
        names[Ai.PRESETS.length] = tr("Другой (совместимый с OpenAI)", "Other (OpenAI-compatible)");
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(tr("Провайдер", "Provider"));
        builder.setItems(names, (dialog, which) -> {
            final Ai.Provider p = Ai.add(which < Ai.PRESETS.length ? Ai.PRESETS[which] : null);
            presentFragment(new AiProviderFragment(p.id));
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }
}
