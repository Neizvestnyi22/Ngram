package org.ngram.messenger;

import android.content.SharedPreferences;

import org.ngram.core.NgramConfig;
import org.ngram.core.NgramHooks;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.PushListenerController;

/**
 * The application class of Ngram. Everything Telegram's code needs to know about the build it
 * runs in is answered here, the way the official app modules do it.
 */
public class NgramApp extends ApplicationLoader {

    @Override
    public void onCreate() {
        // Before Telegram's code starts: it may ask Ngram something from its very first lines.
        NgramHooks.install(new NgramHost());
        super.onCreate();
        NgramConfig.init();
        keepConnectionInBackground();
        Legacy.cleanUpOnce(this);
    }

    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    /** Installed from an APK and not from a store: no store billing, no store update channel. */
    @Override
    protected boolean isStandalone() {
        return true;
    }

    @Override
    protected PushListenerController.IPushListenerServiceProvider onCreatePushProvider() {
        return NoPush.INSTANCE;
    }

    /**
     * Without push messages the app hears about new messages only while it is connected, so
     * the background connection and its service are on unless the user switched them off.
     */
    private void keepConnectionInBackground() {
        try {
            SharedPreferences prefs = MessagesController.getGlobalNotificationsSettings();
            SharedPreferences.Editor editor = null;
            if (!prefs.contains("pushService")) {
                editor = prefs.edit().putBoolean("pushService", true);
            }
            if (!prefs.contains("pushConnection")) {
                editor = (editor == null ? prefs.edit() : editor).putBoolean("pushConnection", true);
            }
            if (editor != null) {
                editor.apply();
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * Push delivery through Google needs a Firebase project registered for this exact package.
     * Ngram has none, so it says "no services" and Telegram's code falls back to its own
     * connection.
     */
    private static final class NoPush implements PushListenerController.IPushListenerServiceProvider {
        static final NoPush INSTANCE = new NoPush();

        @Override
        public boolean hasServices() {
            return false;
        }

        @Override
        public String getLogTitle() {
            return "Push services";
        }

        @Override
        public void onRequestPushToken() {
        }

        @Override
        public int getPushType() {
            return PushListenerController.PUSH_TYPE_FIREBASE;
        }
    }
}
