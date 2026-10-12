package org.ngram.messenger.features;

import android.content.SharedPreferences;

import org.json.JSONObject;
import org.ngram.core.NgramConfig;
import org.ngram.messenger.BuildConfig;
import org.ngram.messenger.ai.Ai;

import java.util.Iterator;
import java.util.Map;

/**
 * Ngram's own settings as one JSON file: the look, the chat options, templates, saved glass
 * sets and the assistant's providers. API keys are never written into it, and nothing of
 * Telegram's (account, chats, its own settings) is touched.
 */
public final class Backup {

    private static final String FORMAT = "ngram-settings";
    private static final int VERSION = 1;

    // Settings that describe this device or this session and make no sense on another one.
    private static boolean skipped(String key) {
        return "demo".equals(key) || "legacyCleaned".equals(key) || key.startsWith("plugins");
    }

    private Backup() {
    }

    public static String export() throws Exception {
        final JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("app", "Ngram " + BuildConfig.VERSION_NAME);
        root.put("saved_at", System.currentTimeMillis() / 1000);

        final JSONObject settings = new JSONObject();
        final SharedPreferences prefs = NgramConfig.prefs();
        if (prefs != null) {
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                final Object value = entry.getValue();
                if (skipped(entry.getKey()) || value == null) {
                    continue;
                }
                // The kind of a value is kept next to it: JSON alone cannot tell 1 from 1.0.
                final JSONObject item = new JSONObject();
                if (value instanceof Boolean) {
                    item.put("t", "b").put("v", (Boolean) value);
                } else if (value instanceof Float) {
                    item.put("t", "f").put("v", (double) (Float) value);
                } else if (value instanceof Integer) {
                    item.put("t", "i").put("v", (Integer) value);
                } else if (value instanceof Long) {
                    item.put("t", "l").put("v", (Long) value);
                } else if (value instanceof String) {
                    item.put("t", "s").put("v", (String) value);
                } else {
                    continue;
                }
                settings.put(entry.getKey(), item);
            }
        }
        root.put("settings", settings);
        root.put("assistant", Ai.exportSettings());
        return root.toString(2);
    }

    /** Applies a backup; returns how many settings were taken from it. */
    public static int restore(String json) throws Exception {
        final JSONObject root = new JSONObject(json);
        if (!FORMAT.equals(root.optString("format"))) {
            throw new IllegalArgumentException("not a Ngram settings file");
        }
        int applied = 0;
        final JSONObject settings = root.optJSONObject("settings");
        final SharedPreferences prefs = NgramConfig.prefs();
        if (settings != null && prefs != null) {
            final SharedPreferences.Editor editor = prefs.edit();
            final Iterator<String> keys = settings.keys();
            while (keys.hasNext()) {
                final String key = keys.next();
                final JSONObject item = settings.optJSONObject(key);
                if (item == null || skipped(key) || key.length() > 64) {
                    continue;
                }
                switch (item.optString("t")) {
                    case "b":
                        editor.putBoolean(key, item.optBoolean("v"));
                        break;
                    case "f":
                        editor.putFloat(key, (float) item.optDouble("v"));
                        break;
                    case "i":
                        editor.putInt(key, item.optInt("v"));
                        break;
                    case "l":
                        editor.putLong(key, item.optLong("v"));
                        break;
                    case "s":
                        editor.putString(key, item.optString("v"));
                        break;
                    default:
                        continue;
                }
                applied++;
            }
            editor.apply();
            NgramConfig.reload();
        }
        final JSONObject assistant = root.optJSONObject("assistant");
        if (assistant != null) {
            applied += Ai.importSettings(assistant);
        }
        return applied;
    }
}
