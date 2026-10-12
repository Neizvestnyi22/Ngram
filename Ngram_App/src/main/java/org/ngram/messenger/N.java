package org.ngram.messenger;

import org.telegram.messenger.LocaleController;

import java.util.Locale;

/** Small things every Ngram screen needs. */
public final class N {

    private N() {
    }

    /**
     * Ngram's own texts in the two languages it ships with. Telegram's texts come from its
     * language packs; these are not in any of them, so they are picked here by the language
     * the app is currently shown in.
     */
    public static String tr(String ru, String en) {
        return russian() ? ru : en;
    }

    public static boolean russian() {
        String lang = null;
        try {
            Locale locale = LocaleController.getInstance().getCurrentLocale();
            if (locale != null) {
                lang = locale.getLanguage();
            }
        } catch (Throwable ignore) {
        }
        if (lang == null) {
            lang = Locale.getDefault().getLanguage();
        }
        return "ru".equals(lang) || "uk".equals(lang) || "be".equals(lang) || "kk".equals(lang);
    }

    /** "85%" for 0.85. */
    public static String percent(float value) {
        return Math.round(value * 100) + "%";
    }
}
