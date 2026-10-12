package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;

import android.view.View;

import org.ngram.messenger.features.Templates;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/** The list of ready-made phrases: add, change, reorder, remove. */
public class TemplatesFragment extends NgramFragment {

    private static final int ADD = 1;
    private static final int FIRST = 100;

    @Override
    protected CharSequence getTitle() {
        return tr("Шаблоны", "Templates");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final ArrayList<String> list = Templates.all();
        if (!list.isEmpty()) {
            items.add(UItem.asHeader(tr("Ваши фразы", "Your Phrases")));
            for (int i = 0; i < list.size(); i++) {
                items.add(UItem.asButton(FIRST + i, Templates.preview(list.get(i))));
            }
            items.add(UItem.asShadow(tr("Нажмите на фразу, чтобы изменить, передвинуть или удалить её.", "Tap a phrase to change, move or remove it.")));
        }
        if (list.size() < Templates.LIMIT) {
            items.add(UItem.asButton(ADD, org.telegram.messenger.R.drawable.msg_add, tr("Добавить фразу", "Add a Phrase")).accent());
        }
        items.add(UItem.asShadow(tr(
            "В чате: меню (три точки) → Ngram → Шаблоны. Фраза только попадает в поле ввода — отправляете её вы.\n\nВ тексте можно писать {имя}, {дата} и {время}: при вставке они заменятся на имя собеседника, сегодняшнюю дату и текущее время.",
            "In a chat: the menu (three dots) → Ngram → Templates. A phrase only goes into the message field — you are the one who sends it.\n\nThe text may hold {name}, {date} and {time}: when the phrase is used they become the other person's name, today's date and the current time.")));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ADD) {
            Prompt.show(this, tr("Новая фраза", "New Phrase"), null, tr("Текст фразы", "The phrase"), "", Prompt.MULTILINE, text -> {
                if (!text.isEmpty()) {
                    final ArrayList<String> list = Templates.all();
                    list.add(text);
                    Templates.save(list);
                    refresh();
                }
            });
        } else if (item.id >= FIRST) {
            actions(item.id - FIRST);
        }
    }

    private void actions(int index) {
        final ArrayList<String> list = Templates.all();
        if (index < 0 || index >= list.size() || getContext() == null) {
            return;
        }
        final ArrayList<CharSequence> names = new ArrayList<>();
        final ArrayList<Runnable> runs = new ArrayList<>();
        names.add(tr("Изменить", "Change"));
        runs.add(() -> Prompt.show(this, tr("Фраза", "Phrase"), null, tr("Текст фразы", "The phrase"), list.get(index), Prompt.MULTILINE, text -> {
            if (text.isEmpty()) {
                list.remove(index);
            } else {
                list.set(index, text);
            }
            Templates.save(list);
            refresh();
        }));
        if (index > 0) {
            names.add(tr("Выше", "Move Up"));
            runs.add(() -> move(list, index, index - 1));
        }
        if (index < list.size() - 1) {
            names.add(tr("Ниже", "Move Down"));
            runs.add(() -> move(list, index, index + 1));
        }
        names.add(tr("Удалить", "Remove"));
        runs.add(() -> {
            list.remove(index);
            Templates.save(list);
            refresh();
        });
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(Templates.preview(list.get(index)));
        builder.setItems(names.toArray(new CharSequence[0]), (dialog, which) -> {
            if (which >= 0 && which < runs.size()) {
                runs.get(which).run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void move(ArrayList<String> list, int from, int to) {
        final String item = list.remove(from);
        list.add(to, item);
        Templates.save(list);
        refresh();
    }
}
