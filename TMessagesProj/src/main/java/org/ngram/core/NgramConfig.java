package org.ngram.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Ngram's own settings that Telegram's code reads while it draws or reacts to a touch. They are
 * plain static fields so that reading one costs nothing; {@link #set} writes a value and
 * refreshes the field.
 *
 * Everything here changes how the app looks, moves or asks before acting. Nothing here changes
 * what other people see of you: read marks, typing and online status are left exactly as
 * Telegram sends them.
 */
public final class NgramConfig {

    private static final String FILE = "ngram3";

    public static final int MOTION_JELLY = 0;
    public static final int MOTION_GLASS = 1;
    public static final int MOTION_PLAIN = 2;

    public static final int TAP_REACTION = 0;
    public static final int TAP_REPLY = 1;
    public static final int TAP_COPY = 2;
    public static final int TAP_EDIT = 3;
    public static final int TAP_SAVE = 4;
    public static final int TAP_NONE = 5;
    public static final int TAP_DETAILS = 6;

    // ---- liquid glass -------------------------------------------------------------------

    // Ngram's own starting point: glass a little clearer than Telegram's, with a stronger
    // lens and a brighter edge. 1.0 everywhere is exactly Telegram's look.
    public static final float NGRAM_TINT = 0.8f, NGRAM_REFRACTION = 1.3f, NGRAM_RIM = 1.25f;

    /** How much of its colour a glass panel keeps: below 1 it is clearer, above 1 milkier. */
    public static float glassTint = NGRAM_TINT;
    /** Blur under clear glass (tab bar, buttons), as a multiple of Telegram's 6dp. */
    public static float glassBlur = 1f;
    /** Blur under frosted glass (headers, panels), as a multiple of Telegram's 40dp. */
    public static float frostBlur = 1f;
    /** How strongly the edge of a glass panel bends what is under it. */
    public static float refraction = NGRAM_REFRACTION;
    /** Brightness of the light line along the edge of a glass panel. */
    public static float rim = NGRAM_RIM;
    /** A colour mixed into every glass panel; 0 leaves the glass neutral. */
    public static int glassColor = 0;
    /** How much of {@link #glassColor} goes into the glass, 0..0.6. */
    public static float glassColorStrength = 0.25f;

    // ---- motion -------------------------------------------------------------------------

    /** What a button does under the finger: {@link #MOTION_JELLY}, _GLASS or _PLAIN. */
    public static int pressMotion = MOTION_JELLY;

    // ---- look ---------------------------------------------------------------------------

    /** Switches drawn as the wide green capsule of iOS instead of Telegram's thin one. */
    public static boolean iosSwitch = true;
    /** Names under the icons of the bottom tab bar. */
    public static boolean tabTitles = true;
    /** The round camera button for posting a story, above the "new message" button. */
    public static boolean storyButton = true;
    /** The row of stories above the chat list. */
    public static boolean stories = true;
    /** Text shown at the top of the chat list instead of the Ngram wordmark; empty for the wordmark. */
    public static String mainTitle = "";

    // ---- chats --------------------------------------------------------------------------

    /** Seconds in the time of a message. */
    public static boolean seconds = false;
    /** Whole numbers for views, members and reactions instead of "1.2K". */
    public static boolean exactNumbers = false;
    /** Size of stickers in a chat, as a part of Telegram's own size. */
    public static float stickerScale = 1f;
    /** What a double tap on a message does: one of the TAP_ constants. */
    public static int doubleTap = TAP_REACTION;
    /** Pulling up at the end of a channel opens the next unread one. */
    public static boolean pullNext = true;
    /** The keyboard goes away when the chat is scrolled by hand. */
    public static boolean hideKeyboardOnScroll = false;

    // ---- asking first -------------------------------------------------------------------

    public static boolean confirmSticker = false;
    public static boolean confirmGif = false;
    public static boolean confirmCall = false;
    /** Letting go of the microphone stops into the preview instead of sending at once. */
    public static boolean voicePreview = false;

    // ---- privacy on this device ---------------------------------------------------------

    /** The user's own phone number is drawn as dots in settings and in their profile. */
    public static boolean hidePhone = false;
    /** No screenshots of the app and no preview of it in the list of recent apps. */
    public static boolean secureScreen = false;

    // ---- debug --------------------------------------------------------------------------

    /** Made-up account for looking at screens without signing in; debug builds only. */
    public static boolean demo = false;

    private static SharedPreferences prefs;

    private NgramConfig() {
    }

    public static SharedPreferences prefs() {
        if (prefs == null) {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return null;
            }
            prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
            load();
        }
        return prefs;
    }

    /** Called once the application has a context; safe to call again. */
    public static void init() {
        prefs();
    }

    /** Reads every field again, after the settings file was changed in bulk. */
    public static void reload() {
        if (prefs() != null) {
            load();
        }
    }

    private static void load() {
        glassTint = clamp(prefs.getFloat("glassTint", NGRAM_TINT), 0.3f, 1.4f);
        glassBlur = clamp(prefs.getFloat("glassBlur", 1f), 0f, 3f);
        frostBlur = clamp(prefs.getFloat("frostBlur", 1f), 0.25f, 1.6f);
        refraction = clamp(prefs.getFloat("refraction", NGRAM_REFRACTION), 0f, 2.2f);
        rim = clamp(prefs.getFloat("rim", NGRAM_RIM), 0f, 2.5f);
        glassColor = prefs.getInt("glassColor", 0);
        glassColorStrength = clamp(prefs.getFloat("glassColorStrength", 0.25f), 0f, 0.6f);
        pressMotion = prefs.getInt("pressMotion", MOTION_JELLY);

        iosSwitch = prefs.getBoolean("iosSwitch", true);
        tabTitles = prefs.getBoolean("tabTitles", true);
        storyButton = prefs.getBoolean("storyButton", true);
        stories = prefs.getBoolean("stories", true);
        mainTitle = prefs.getString("mainTitle", "");

        seconds = prefs.getBoolean("seconds", false);
        exactNumbers = prefs.getBoolean("exactNumbers", false);
        stickerScale = clamp(prefs.getFloat("stickerScale", 1f), 0.4f, 1f);
        doubleTap = prefs.getInt("doubleTap", TAP_REACTION);
        pullNext = prefs.getBoolean("pullNext", true);
        hideKeyboardOnScroll = prefs.getBoolean("hideKeyboardOnScroll", false);

        confirmSticker = prefs.getBoolean("confirmSticker", false);
        confirmGif = prefs.getBoolean("confirmGif", false);
        confirmCall = prefs.getBoolean("confirmCall", false);
        voicePreview = prefs.getBoolean("voicePreview", false);

        hidePhone = prefs.getBoolean("hidePhone", false);
        secureScreen = prefs.getBoolean("secureScreen", false);

        demo = prefs.getBoolean("demo", false);
        timeFormat = null;
    }

    public static void set(String key, float value) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putFloat(key, value).apply();
            load();
        }
    }

    public static void set(String key, int value) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putInt(key, value).apply();
            load();
        }
    }

    public static void set(String key, boolean value) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean(key, value).apply();
            load();
        }
    }

    public static void set(String key, String value) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putString(key, value == null ? "" : value).apply();
            load();
        }
    }

    /** Puts the glass sliders back: to Ngram's own values, or to exactly Telegram's. */
    public static void resetGlass(boolean telegram) {
        SharedPreferences p = prefs();
        if (p != null) {
            SharedPreferences.Editor e = p.edit().remove("glassBlur").remove("frostBlur").remove("glassColor").remove("glassColorStrength");
            if (telegram) {
                e.putFloat("glassTint", 1f).putFloat("refraction", 1f).putFloat("rim", 1f);
            } else {
                e.remove("glassTint").remove("refraction").remove("rim");
            }
            e.apply();
            load();
        }
    }

    // ---- helpers used from the drawing code ---------------------------------------------

    /** The fill of a glass panel after the user's settings; an opaque colour stays as it is. */
    public static int tint(int color) {
        final int alpha = Color.alpha(color);
        if (alpha == 0 || alpha == 255) {
            return color;
        }
        int rgb = color & 0x00FFFFFF;
        if (glassColor != 0 && glassColorStrength > 0f) {
            rgb = mix(rgb, glassColor & 0x00FFFFFF, glassColorStrength);
        }
        // Never fully opaque: an opaque fill switches the glass off altogether.
        final int scaled = glassTint == 1f ? alpha : Math.max(8, Math.min(250, Math.round(alpha * glassTint)));
        return rgb | (scaled << 24);
    }

    private static int mix(int from, int to, float part) {
        final int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * part);
        final int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * part);
        final int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * part);
        return (r << 16) | (g << 8) | b;
    }

    /** The edge highlight of a glass panel after the user's setting. */
    public static int rim(int color) {
        if (rim == 1f) {
            return color;
        }
        final int alpha = Color.alpha(color);
        if (alpha == 0) {
            return color;
        }
        final int scaled = Math.max(0, Math.min(255, Math.round(alpha * rim)));
        return (color & 0x00FFFFFF) | (scaled << 24);
    }

    // ---- helpers used from the chat code ------------------------------------------------

    private static SimpleDateFormat timeFormat;
    private static boolean timeFormat24;

    /** The time of a message: Telegram's own format, or the same with seconds. */
    public static String time(long millis) {
        if (!seconds) {
            return LocaleController.getInstance().getFormatterDay().format(millis);
        }
        final boolean is24 = LocaleController.is24HourFormat;
        if (timeFormat == null || timeFormat24 != is24) {
            Locale locale = LocaleController.getInstance().getCurrentLocale();
            timeFormat = new SimpleDateFormat(is24 ? "HH:mm:ss" : "h:mm:ss a", locale == null ? Locale.getDefault() : locale);
            timeFormat24 = is24;
        }
        return timeFormat.format(new Date(millis));
    }

    /** A number written in full, digits in groups of three: 1 234 567. */
    public static String whole(int number) {
        final String digits = Integer.toString(Math.abs(number));
        final StringBuilder sb = new StringBuilder(digits.length() + 4);
        if (number < 0) {
            sb.append('-');
        }
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                sb.append(' ');
            }
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    /** The user's own number as it is shown: whole, or the country code, dots and two digits. */
    public static String ownPhone(String formatted) {
        if (!hidePhone || formatted == null) {
            return formatted;
        }
        final int keepUntil = Math.max(formatted.indexOf(' '), Math.min(2, formatted.length() - 1));
        int digitsLeft = 0;
        for (int i = keepUntil + 1; i < formatted.length(); i++) {
            if (Character.isDigit(formatted.charAt(i))) {
                digitsLeft++;
            }
        }
        final StringBuilder sb = new StringBuilder(formatted.length());
        for (int i = 0; i < formatted.length(); i++) {
            final char c = formatted.charAt(i);
            if (i > keepUntil && Character.isDigit(c)) {
                sb.append(digitsLeft > 2 ? '•' : c);
                digitsLeft--;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }
}
