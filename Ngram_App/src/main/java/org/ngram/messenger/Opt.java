package org.ngram.messenger;

import android.content.SharedPreferences;

import org.ngram.core.NgramConfig;

/**
 * Options that only Ngram's own code reads. They live in the same settings file as
 * {@link NgramConfig}, so a backup carries both.
 */
public final class Opt {

    // Which of Ngram's entries appear in the menu of a message.
    public static final String MENU_DETAILS = "menuDetails";
    public static final String MENU_REPEAT = "menuRepeat";
    public static final String MENU_SAVE = "menuSave";
    public static final String MENU_COPY_PHOTO = "menuCopyPhoto";
    public static final String MENU_STICKER = "menuSticker";

    private Opt() {
    }

    public static boolean on(String key, boolean byDefault) {
        final SharedPreferences p = NgramConfig.prefs();
        return p == null ? byDefault : p.getBoolean(key, byDefault);
    }

    public static void set(String key, boolean value) {
        final SharedPreferences p = NgramConfig.prefs();
        if (p != null) {
            p.edit().putBoolean(key, value).apply();
        }
    }

    public static String text(String key) {
        final SharedPreferences p = NgramConfig.prefs();
        return p == null ? "" : p.getString(key, "");
    }

    public static void text(String key, String value) {
        final SharedPreferences p = NgramConfig.prefs();
        if (p != null) {
            p.edit().putString(key, value == null ? "" : value).apply();
        }
    }

    /**
     * What each entry of the message menu is by default. "Repeat" starts switched off, and so
     * does "To Saved Messages": Telegram's own Forward already leads there in two taps.
     */
    public static boolean menu(String key) {
        return on(key, !MENU_REPEAT.equals(key) && !MENU_SAVE.equals(key));
    }
}
