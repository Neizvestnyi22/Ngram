package org.ngram.messenger.ui.ai;

import static org.ngram.messenger.N.tr;

import android.view.View;

import org.ngram.messenger.ai.Ai;
import org.ngram.messenger.ai.AiClient;
import org.ngram.messenger.ui.NgramFragment;
import org.ngram.messenger.ui.Prompt;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.List;

/** One provider: where requests go, with which key and which model. */
public class AiProviderFragment extends NgramFragment {

    private static final int NAME = 1;
    private static final int API_OPENAI = 2;
    private static final int API_ANTHROPIC = 3;
    private static final int URL = 4;
    private static final int KEY = 5;
    private static final int MODEL = 6;
    private static final int LOAD_MODELS = 7;
    private static final int USE = 8;
    private static final int REMOVE = 9;

    private final String providerId;
    private boolean loading;

    public AiProviderFragment(String providerId) {
        this.providerId = providerId;
    }

    private Ai.Provider provider() {
        return Ai.find(providerId);
    }

    @Override
    protected CharSequence getTitle() {
        final Ai.Provider p = provider();
        return p == null || p.name.isEmpty() ? tr("Провайдер", "Provider") : p.name;
    }

    /** A key is shown by its tail only: enough to tell two keys apart, useless to a bystander. */
    private static String masked(String key) {
        if (key.isEmpty()) {
            return tr("не задан", "not set");
        }
        return "••••" + (key.length() > 8 ? key.substring(key.length() - 4) : "");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final Ai.Provider p = provider();
        if (p == null) {
            return;
        }
        final Ai.Preset preset = Ai.presetFor(p);

        items.add(UItem.asHeader(tr("Подключение", "Connection")));
        items.add(UItem.asButton(NAME, tr("Название", "Name"), p.name.isEmpty() ? tr("без названия", "untitled") : p.name));
        items.add(UItem.asButton(URL, tr("Адрес API", "API Address"), p.baseUrl.isEmpty() ? tr("не задан", "not set") : p.baseUrl));
        items.add(UItem.asButton(KEY, tr("Ключ API", "API Key"), masked(p.apiKey)));
        if (preset != null && preset.needsKey) {
            items.add(UItem.asShadow(tr("Ключ выдаётся на ", "Get a key at ") + preset.keyUrl));
        } else if (preset != null) {
            items.add(UItem.asShadow(tr(
                "Локальному серверу ключ не нужен. Замените адрес на адрес компьютера в вашей сети.",
                "A local server needs no key. Replace the address with that of the computer on your network.")));
        } else {
            items.add(UItem.asShadow(tr(
                "Адрес — до пути /chat/completions, обычно оканчивается на /v1. Без https допускаются только адреса локальной сети.",
                "The address up to /chat/completions, usually ending in /v1. Plain http is accepted for local network addresses only.")));
        }

        items.add(UItem.asHeader(tr("Формат запросов", "Request Format")));
        items.add(UItem.asRadio(API_OPENAI, "OpenAI", tr("большинство сервисов", "most services")).setChecked(p.api == Ai.API_OPENAI));
        items.add(UItem.asRadio(API_ANTHROPIC, "Anthropic", "Claude").setChecked(p.api == Ai.API_ANTHROPIC));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(tr("Модель", "Model")));
        items.add(UItem.asButton(MODEL, tr("Модель", "Model"), p.model.isEmpty() ? tr("не выбрана", "not chosen") : p.model));
        items.add(UItem.asButton(LOAD_MODELS, R.drawable.msg_download,
            loading ? tr("Загрузка списка…", "Loading the list…") : tr("Загрузить список моделей", "Load the List of Models")).accent());
        items.add(UItem.asShadow(p.models.isEmpty()
            ? tr("Список запрашивается у провайдера с вашим ключом. Название модели можно ввести и вручную.",
                 "The list is requested from the provider with your key. A model name can also be typed in.")
            : tr("Моделей в списке: ", "Models in the list: ") + p.models.size()));

        final Ai.Provider active = Ai.active();
        if (active != p) {
            items.add(UItem.asButton(USE, R.drawable.msg_select, tr("Использовать этого провайдера", "Use This Provider")).accent());
        }
        items.add(UItem.asButton(REMOVE, R.drawable.msg_delete, tr("Удалить провайдера", "Remove the Provider")).red());
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final Ai.Provider p = provider();
        if (p == null) {
            return;
        }
        switch (item.id) {
            case NAME:
                Prompt.show(this, tr("Название", "Name"), null, "OpenAI", p.name, Prompt.PLAIN, text -> {
                    p.name = text;
                    changed();
                    actionBar.setTitle(getTitle());
                });
                break;
            case URL:
                Prompt.show(this, tr("Адрес API", "API Address"), null, "https://api.openai.com/v1", p.baseUrl, Prompt.EXACT, text -> {
                    p.baseUrl = text;
                    p.models = new ArrayList<>();
                    changed();
                });
                break;
            case KEY:
                Prompt.show(this, tr("Ключ API", "API Key"),
                    tr("Ключ сохраняется на устройстве в зашифрованном виде и отправляется только этому провайдеру.",
                       "The key is kept encrypted on the device and is sent to this provider only."),
                    "sk-…", p.apiKey, Prompt.EXACT, text -> {
                        p.apiKey = text;
                        changed();
                    });
                break;
            case API_OPENAI:
                p.api = Ai.API_OPENAI;
                changed();
                break;
            case API_ANTHROPIC:
                p.api = Ai.API_ANTHROPIC;
                changed();
                break;
            case MODEL:
                chooseModel(p);
                break;
            case LOAD_MODELS:
                loadModels(p, false);
                break;
            case USE:
                Ai.activeId = p.id;
                changed();
                break;
            case REMOVE: {
                final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
                builder.setTitle(tr("Удалить провайдера?", "Remove the provider?"));
                builder.setMessage(tr("Его адрес и ключ будут стёрты с устройства.", "Its address and key will be erased from the device."));
                builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                    Ai.remove(p);
                    finishFragment();
                });
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                final AlertDialog dialog = builder.create();
                showDialog(dialog);
                dialog.redPositive();
                break;
            }
        }
    }

    private void changed() {
        Ai.save();
        refresh();
    }

    private void typeModel(Ai.Provider p) {
        Prompt.show(this, tr("Модель", "Model"), null, "gpt-4o-mini", p.model, Prompt.EXACT, text -> {
            p.model = text;
            changed();
        });
    }

    private void chooseModel(Ai.Provider p) {
        if (p.models.isEmpty()) {
            if (p.baseUrl.trim().isEmpty()) {
                typeModel(p);
            } else {
                loadModels(p, true);
            }
            return;
        }
        final List<String> models = p.models;
        final CharSequence[] names = new CharSequence[models.size() + 1];
        names[0] = tr("Ввести название вручную…", "Type a name…");
        for (int i = 0; i < models.size(); i++) {
            names[i + 1] = models.get(i);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(tr("Модель", "Model"));
        builder.setItems(names, (dialog, which) -> {
            if (which == 0) {
                typeModel(p);
            } else {
                p.model = models.get(which - 1);
                changed();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void loadModels(Ai.Provider p, boolean thenChoose) {
        if (loading) {
            return;
        }
        loading = true;
        refresh();
        new Thread(() -> {
            List<String> result = null;
            String error = null;
            try {
                result = AiClient.listModels(p);
            } catch (AiClient.AiException e) {
                error = e.getMessage();
            }
            final List<String> models = result;
            final String failure = error;
            AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                if (models != null) {
                    p.models = models;
                    Ai.save();
                }
                if (getContext() == null) {
                    return;
                }
                refresh();
                if (failure != null) {
                    BulletinFactory.of(this).createErrorBulletin(failure).show();
                    if (thenChoose) {
                        typeModel(p);
                    }
                } else if (thenChoose) {
                    chooseModel(p);
                }
            });
        }, "ngram-ai-models").start();
    }
}
