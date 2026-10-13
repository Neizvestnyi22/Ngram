package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;

import android.os.Bundle;
import android.view.View;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Everything typed and not sent, in one place. Telegram marks a chat that has a draft in the
 * chat list, but with many chats those marks are easy to lose; here the drafts stand together,
 * newest first.
 */
public class DraftsFragment extends NgramFragment {

    private static final class Draft {
        long dialogId, topicId;
        String title, text;
        int date;
    }

    private final ArrayList<Draft> drafts = new ArrayList<>();

    /** How many chats of the account have an unsent text. */
    public static int count(int account) {
        return collect(account).size();
    }

    private static ArrayList<Draft> collect(int account) {
        final ArrayList<Draft> list = new ArrayList<>();
        final LongSparseArray<LongSparseArray<TLRPC.DraftMessage>> all = MediaDataController.getInstance(account).getDrafts();
        final org.telegram.messenger.MessagesController controller = org.telegram.messenger.MessagesController.getInstance(account);
        for (int i = 0; i < all.size(); i++) {
            final long dialogId = all.keyAt(i);
            final LongSparseArray<TLRPC.DraftMessage> threads = all.valueAt(i);
            for (int j = 0; threads != null && j < threads.size(); j++) {
                final TLRPC.DraftMessage draft = threads.valueAt(j);
                if (draft == null || draft.message == null || draft.message.trim().isEmpty() || DialogObject.isEncryptedDialog(dialogId)) {
                    continue;
                }
                final Draft d = new Draft();
                d.dialogId = dialogId;
                d.topicId = threads.keyAt(j);
                d.text = draft.message.trim();
                d.date = draft.date;
                final String name = controller.getPeerName(dialogId);
                d.title = name == null || name.isEmpty() ? String.valueOf(dialogId) : name;
                list.add(d);
            }
        }
        Collections.sort(list, (a, b) -> Integer.compare(b.date, a.date));
        return list;
    }

    @Override
    protected CharSequence getTitle() {
        return tr("Неотправленное", "Unsent Texts");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        drafts.clear();
        drafts.addAll(collect(currentAccount));
        if (drafts.isEmpty()) {
            items.add(UItem.asShadow(tr("Неотправленных текстов нет.", "There are no unsent texts.")));
            return;
        }
        for (int i = 0; i < drafts.size(); i++) {
            final Draft d = drafts.get(i);
            final String flat = d.text.replace('\n', ' ');
            items.add(UItem.asButton(1 + i, d.title, org.telegram.messenger.LocaleController.stringForMessageListDate(d.date)));
            items.add(UItem.asShadow(flat.length() > 240 ? flat.substring(0, 240) + "…" : flat));
        }
        items.add(UItem.asShadow(tr(
            "Нажмите на чат, чтобы открыть его с набранным текстом. Удерживайте, чтобы стереть черновик.",
            "Tap a chat to open it with the text you typed. Press and hold to erase the draft.")));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id < 1 || item.id > drafts.size()) {
            return;
        }
        final Draft d = drafts.get(item.id - 1);
        final Bundle args = new Bundle();
        if (d.dialogId > 0) {
            args.putLong("user_id", d.dialogId);
        } else {
            args.putLong("chat_id", -d.dialogId);
        }
        if (getMessagesController().checkCanOpenChat(args, this)) {
            presentFragment(new ChatActivity(args));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < 1 || item.id > drafts.size()) {
            return false;
        }
        final Draft d = drafts.get(item.id - 1);
        ItemOptions.makeOptions(this, view)
            .add(R.drawable.msg_delete, tr("Стереть черновик", "Erase the Draft"), () -> {
                MediaDataController.getInstance(currentAccount).cleanDraft(d.dialogId, d.topicId, false);
                refresh();
            })
            .show();
        return true;
    }
}
