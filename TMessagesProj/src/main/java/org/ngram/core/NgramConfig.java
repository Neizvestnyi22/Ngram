package org.ngram.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.telegram.messenger.ApplicationLoader;

/**
 * Ngram's own settings that Telegram's code reads while it draws. They are plain static fields
 * so that reading one costs nothing; {@link #set} writes a value and refreshes the field.
 *
 * Everything here changes how the app looks or moves and nothing about what is sent to
 * Telegram.
 */
public final class NgramConfig {

    private static final String FILE = "ngram3";

    public static final int MOTION_JELLY = 0;
    public static final int MOTION_GLASS = 1;
    public static final int MOTION_PLAIN = 2;

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

    private static void load() {
        glassTint = clamp(prefs.getFloat("glassTint", NGRAM_TINT), 0.3f, 1.4f);
        glassBlur = clamp(prefs.getFloat("glassBlur", 1f), 0f, 3f);
        frostBlur = clamp(prefs.getFloat("frostBlur", 1f), 0.25f, 1.6f);
        refraction = clamp(prefs.getFloat("refraction", NGRAM_REFRACTION), 0f, 2.2f);
        rim = clamp(prefs.getFloat("rim", NGRAM_RIM), 0f, 2.5f);
        pressMotion = prefs.getInt("pressMotion", MOTION_JELLY);

        iosSwitch = prefs.getBoolean("iosSwitch", true);
        tabTitles = prefs.getBoolean("tabTitles", true);
        storyButton = prefs.getBoolean("storyButton", true);
        demo = prefs.getBoolean("demo", false);
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

    /** Puts the glass sliders back: to Ngram's own values, or to exactly Telegram's. */
    public static void resetGlass(boolean telegram) {
        SharedPreferences p = prefs();
        if (p != null) {
            SharedPreferences.Editor e = p.edit().remove("glassBlur").remove("frostBlur");
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

    /** The tint of a glass panel after the user's setting; an opaque colour stays opaque. */
    public static int tint(int color) {
        if (glassTint == 1f) {
            return color;
        }
        final int alpha = Color.alpha(color);
        if (alpha == 0 || alpha == 255) {
            return color;
        }
        // Never fully opaque: an opaque fill switches the glass off altogether.
        final int scaled = Math.max(8, Math.min(250, Math.round(alpha * glassTint)));
        return (color & 0x00FFFFFF) | (scaled << 24);
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

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }
}
