package org.ngram.messenger.features;

import android.app.Activity;

import org.ngram.core.NgramConfig;
import org.telegram.messenger.FlagSecureReason;

/**
 * "No screenshots": while the option is on, the app's window is marked secure, so the system
 * refuses screenshots and screen recording of it and shows a blank card in the list of recent
 * apps. It is one more reason on top of Telegram's own (a passcode, a protected chat): each of
 * them is counted separately, so switching this one off does not open what another one closed.
 */
public final class Secure {

    private static FlagSecureReason reason;

    private Secure() {
    }

    /** Called when the main activity starts. */
    public static void attach(Activity activity) {
        try {
            if (reason != null) {
                reason.detach();
            }
            reason = new FlagSecureReason(activity.getWindow(), () -> NgramConfig.secureScreen);
            reason.attach();
        } catch (Throwable ignore) {
        }
    }

    /** Called after the option was changed. */
    public static void refresh() {
        if (reason != null) {
            reason.invalidate();
        }
    }
}
