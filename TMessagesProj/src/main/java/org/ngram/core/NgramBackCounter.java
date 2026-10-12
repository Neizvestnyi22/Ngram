package org.ngram.core;

import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.ActionBar;

/**
 * Keeps the number next to the back arrow of a chat equal to the number on the Chats tab: how
 * many unread messages wait in the other chats. Created with the chat screen, released with it.
 */
public final class NgramBackCounter implements NotificationCenter.NotificationCenterDelegate {

    private final int account;
    private ActionBar bar;

    private NgramBackCounter(int account, ActionBar bar) {
        this.account = account;
        this.bar = bar;
    }

    /** Returns null when the counter is switched off: there is then nothing to release. */
    public static NgramBackCounter attach(int account, ActionBar bar) {
        if (bar == null || !NgramConfig.backCounter) {
            return null;
        }
        final NgramBackCounter counter = new NgramBackCounter(account, bar);
        NotificationCenter.getInstance(account).addObserver(counter, NotificationCenter.updateInterfaces);
        NotificationCenter.getGlobalInstance().addObserver(counter, NotificationCenter.notificationsCountUpdated);
        counter.update();
        return counter;
    }

    public void detach() {
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.notificationsCountUpdated);
        bar = null;
    }

    private void update() {
        if (bar != null) {
            bar.setNgramBackCount(MessagesStorage.getInstance(account).getMainUnreadCount());
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        update();
    }
}
