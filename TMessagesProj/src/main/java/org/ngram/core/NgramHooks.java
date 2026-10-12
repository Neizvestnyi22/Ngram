package org.ngram.core;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.ChatActivity;
import android.app.Activity;
import android.content.Intent;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.UItem;

import java.util.ArrayList;

/**
 * The few places where Telegram's code hands over to Ngram.
 *
 * Telegram's sources stay in this module and Ngram's own features (assistant, plugins, its
 * settings) live in the app module, which this module cannot see. The app module registers a
 * {@link Host} at start and Telegram's code calls it through the static methods below. With no
 * host every method does nothing, so the Telegram code runs unchanged.
 */
public final class NgramHooks {

    public interface Host {
        /** Adds Ngram's rows to the main settings list. */
        void fillSettings(ArrayList<UItem> items);

        /** A row added by {@link #fillSettings} was tapped; null when the id is not Ngram's. */
        BaseFragment openSettings(int id);

        /** Start of the main activity, before it decides which screen to show. */
        void onLaunch(Activity activity, Intent intent);

        /** A new intent for the main activity; true when Ngram has dealt with it. */
        boolean onIntent(Activity activity, Intent intent);

        /**
         * A request is about to leave for Telegram. Returns the request to send (the same or a
         * replacement), or null to keep it off the network: it is then either answered through
         * {@code reply} or, when {@code reply} is left alone, silently dropped.
         *
         * @param flags the ConnectionsManager.RequestFlag* bits the request is sent with
         */
        TLObject onRequest(int account, TLObject request, int flags, LocalReply reply);

        /** The answer to a request has arrived (one of response and error is null). */
        void onResponse(int account, TLObject request, TLObject response, TLRPC.TL_error error);

        /** Updates have arrived from Telegram, before the app applies them. */
        void onUpdates(int account, TLRPC.Updates updates);

        /**
         * A text of the app is being looked up. Returns Ngram's own wording for it, or null to
         * leave Telegram's. Called for every text on screen: keep it to a few comparisons.
         */
        String string(int resId, String key);

        /** Adds Ngram's entries to the menu of a message (ids from {@link #MESSAGE_OPTION_BASE}). */
        void fillMessageMenu(ChatActivity chat, MessageObject message, ArrayList<Integer> icons, ArrayList<CharSequence> items, ArrayList<Integer> options);

        /** An entry added by {@link #fillMessageMenu} was chosen. */
        void onMessageMenu(ChatActivity chat, int option, MessageObject message);

        /** The entry Ngram adds to the menu of a chat's header, or null for none. */
        CharSequence chatMenuTitle(ChatActivity chat);

        /** Ngram's entry in the header menu was chosen. */
        void onChatMenu(ChatActivity chat);
    }

    /** Ids of Ngram's entries in the menu of a message start here. */
    public static final int MESSAGE_OPTION_BASE = 9000;
    /** Id of Ngram's entry in the menu of a chat's header. */
    public static final int CHAT_MENU_ID = 9100;

    /** An answer given to a request on the device instead of by Telegram. */
    public static final class LocalReply {
        public TLObject response;
        public TLRPC.TL_error error;

        public void answer(TLObject value) {
            response = value;
        }

        public void fail(int code, String text) {
            error = new TLRPC.TL_error();
            error.code = code;
            error.text = text;
        }

        public boolean given() {
            return response != null || error != null;
        }
    }

    /** Ids of Ngram's rows in the settings list start here, clear of Telegram's own. */
    public static final int SETTINGS_ID_BASE = 9000;

    private static volatile Host host;

    private NgramHooks() {
    }

    public static void install(Host value) {
        host = value;
    }

    public static boolean installed() {
        return host != null;
    }

    public static void fillSettings(ArrayList<UItem> items) {
        final Host h = host;
        if (h != null) {
            try {
                h.fillSettings(items);
            } catch (Throwable e) {
                report(e);
            }
        }
    }

    public static BaseFragment openSettings(int id) {
        final Host h = host;
        if (h == null || id < SETTINGS_ID_BASE) {
            return null;
        }
        try {
            return h.openSettings(id);
        } catch (Throwable e) {
            report(e);
            return null;
        }
    }

    public static void onLaunch(Activity activity, Intent intent) {
        final Host h = host;
        if (h != null) {
            try {
                h.onLaunch(activity, intent);
            } catch (Throwable e) {
                report(e);
            }
        }
    }

    public static boolean onIntent(Activity activity, Intent intent) {
        final Host h = host;
        if (h != null) {
            try {
                return h.onIntent(activity, intent);
            } catch (Throwable e) {
                report(e);
            }
        }
        return false;
    }

    public static TLObject onRequest(int account, TLObject request, int flags, LocalReply reply) {
        final Host h = host;
        if (h == null) {
            return request;
        }
        try {
            return h.onRequest(account, request, flags, reply);
        } catch (Throwable e) {
            report(e);
            return request;
        }
    }

    public static void onResponse(int account, TLObject request, TLObject response, TLRPC.TL_error error) {
        final Host h = host;
        if (h != null) {
            try {
                h.onResponse(account, request, response, error);
            } catch (Throwable e) {
                report(e);
            }
        }
    }

    public static void onUpdates(int account, TLRPC.Updates updates) {
        final Host h = host;
        if (h != null) {
            try {
                h.onUpdates(account, updates);
            } catch (Throwable e) {
                report(e);
            }
        }
    }

    public static String string(int resId, String key) {
        final Host h = host;
        if (h == null) {
            return null;
        }
        try {
            return h.string(resId, key);
        } catch (Throwable e) {
            return null;
        }
    }

    public static void fillMessageMenu(ChatActivity chat, MessageObject message, ArrayList<Integer> icons, ArrayList<CharSequence> items, ArrayList<Integer> options) {
        final Host h = host;
        if (h != null && message != null) {
            try {
                h.fillMessageMenu(chat, message, icons, items, options);
            } catch (Throwable e) {
                report(e);
            }
        }
    }

    public static boolean onMessageMenu(ChatActivity chat, int option, MessageObject message) {
        final Host h = host;
        if (h == null || option < MESSAGE_OPTION_BASE || message == null) {
            return false;
        }
        try {
            h.onMessageMenu(chat, option, message);
        } catch (Throwable e) {
            report(e);
        }
        return true;
    }

    public static CharSequence chatMenuTitle(ChatActivity chat) {
        final Host h = host;
        if (h == null) {
            return null;
        }
        try {
            return h.chatMenuTitle(chat);
        } catch (Throwable e) {
            report(e);
            return null;
        }
    }

    public static void onChatMenu(ChatActivity chat) {
        final Host h = host;
        if (h != null) {
            try {
                h.onChatMenu(chat);
            } catch (Throwable e) {
                report(e);
            }
        }
    }

    /** A failure in Ngram's code must never take Telegram's code down with it. */
    private static void report(Throwable e) {
        try {
            org.telegram.messenger.FileLog.e(e);
        } catch (Throwable ignore) {
        }
    }
}
