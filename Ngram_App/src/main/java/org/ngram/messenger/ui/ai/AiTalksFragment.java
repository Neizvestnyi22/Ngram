package org.ngram.messenger.ui.ai;

import static org.ngram.messenger.N.tr;

import android.view.View;

import org.ngram.messenger.ai.AiTalk;
import org.ngram.messenger.ui.NgramFragment;
import org.ngram.messenger.ui.Prompt;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The list of conversations with the assistant: open one, rename it, remove it. */
public class AiTalksFragment extends NgramFragment {

    private static final int NEW = 1;
    private static final int FIRST = 100;

    private final ArrayList<AiTalk.Talk> shown = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return tr("Разговоры", "Conversations");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        shown.clear();
        shown.addAll(AiTalk.all());
        items.add(UItem.asButton(NEW, R.drawable.msg_add, tr("Новый разговор", "New Conversation")).accent());
        items.add(UItem.asShadow(null));
        if (!shown.isEmpty()) {
            items.add(UItem.asHeader(tr("Сохранённые", "Saved")));
            final AiTalk.Talk open = AiTalk.current();
            for (int i = 0; i < shown.size(); i++) {
                final AiTalk.Talk talk = shown.get(i);
                items.add(UItem.asButton(FIRST + i, talk.name(), talk == open ? tr("открыт", "open") : when(talk.updated)));
            }
        }
        items.add(UItem.asShadow(tr(
            "Разговоры хранятся только на этом устройстве, не больше " + AiTalk.LIMIT + ". Нажмите, чтобы открыть; удерживайте, чтобы переименовать или удалить.",
            "Conversations are kept on this device only, " + AiTalk.LIMIT + " at most. Tap to open; press and hold to rename or remove.")));
    }

    private static String when(long seconds) {
        if (seconds <= 0) {
            return "";
        }
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        if (locale == null) {
            locale = Locale.getDefault();
        }
        final boolean today = System.currentTimeMillis() / 1000 - seconds < 20 * 3600;
        return new SimpleDateFormat(today ? (LocaleController.is24HourFormat ? "HH:mm" : "h:mm a") : "d MMM", locale).format(new Date(seconds * 1000));
    }

    private AiTalk.Talk talkAt(int id) {
        final int index = id - FIRST;
        return index >= 0 && index < shown.size() ? shown.get(index) : null;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == NEW) {
            AiTalk.startNew();
            finishFragment();
            return;
        }
        final AiTalk.Talk talk = talkAt(item.id);
        if (talk != null) {
            AiTalk.open(talk);
            finishFragment();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        final AiTalk.Talk talk = talkAt(item.id);
        if (talk == null || getContext() == null) {
            return false;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(talk.name());
        builder.setItems(new CharSequence[]{tr("Переименовать", "Rename"), tr("Удалить", "Remove")}, (dialog, which) -> {
            if (which == 0) {
                Prompt.show(this, tr("Название разговора", "Conversation Name"), tr("Оставьте пустым, чтобы назвать по первому вопросу.", "Leave it empty to name it after its first question."),
                    tr("Название", "Name"), talk.title, Prompt.PLAIN, title -> {
                        AiTalk.rename(talk, title.length() > 60 ? title.substring(0, 60) : title);
                        refresh();
                    });
            } else if (which == 1) {
                AiTalk.delete(talk);
                refresh();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        final List<AiTalk.Talk> all = AiTalk.all();
        if (all.size() != shown.size()) {
            refresh();
        }
    }
}
