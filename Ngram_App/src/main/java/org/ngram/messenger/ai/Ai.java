package org.ngram.messenger.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The user's AI providers and the settings of the assistant. Everything stays on the device:
 * a request goes straight from the phone to the provider the user chose, with the user's own
 * key, and only when the user asks for it.
 *
 * The storage is the one Ngram 2.x used, so providers and keys survive the move to 3.0.
 */
public final class Ai {

    /** Wire format a provider speaks. Most services are OpenAI-compatible. */
    public static final int API_OPENAI = 0;
    public static final int API_ANTHROPIC = 1;

    /** One configured endpoint: where to send requests, with which key and which model. */
    public static final class Provider {
        public final String id;
        public String name = "";
        public int api = API_OPENAI;
        public String baseUrl = "";
        public String apiKey = "";
        public String model = "";
        public String sttModel = "";
        public List<String> models = new ArrayList<>();

        Provider(String id) {
            this.id = id;
        }

        public boolean ready() {
            return !TextUtils.isEmpty(baseUrl.trim()) && !TextUtils.isEmpty(model.trim());
        }

        public String title() {
            return TextUtils.isEmpty(name) ? baseUrl : name;
        }
    }

    public static final class Preset {
        public final String name;
        public final int api;
        public final String baseUrl;
        /** Where the user gets a key, shown as a hint. */
        public final String keyUrl;
        public final boolean needsKey;

        Preset(String name, int api, String baseUrl, String keyUrl, boolean needsKey) {
            this.name = name;
            this.api = api;
            this.baseUrl = baseUrl;
            this.keyUrl = keyUrl;
            this.needsKey = needsKey;
        }
    }

    public static final Preset[] PRESETS = {
        new Preset("OpenAI", API_OPENAI, "https://api.openai.com/v1", "platform.openai.com/api-keys", true),
        new Preset("Anthropic Claude", API_ANTHROPIC, "https://api.anthropic.com/v1", "console.anthropic.com", true),
        new Preset("Google Gemini", API_OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai", "aistudio.google.com/apikey", true),
        new Preset("OpenRouter", API_OPENAI, "https://openrouter.ai/api/v1", "openrouter.ai/keys", true),
        new Preset("Groq", API_OPENAI, "https://api.groq.com/openai/v1", "console.groq.com/keys", true),
        new Preset("DeepSeek", API_OPENAI, "https://api.deepseek.com/v1", "platform.deepseek.com", true),
        new Preset("Mistral", API_OPENAI, "https://api.mistral.ai/v1", "console.mistral.ai", true),
        new Preset("xAI Grok", API_OPENAI, "https://api.x.ai/v1", "console.x.ai", true),
        new Preset("Together", API_OPENAI, "https://api.together.xyz/v1", "api.together.ai", true),
        new Preset("Ollama", API_OPENAI, "http://192.168.1.2:11434/v1", "ollama.com", false),
        new Preset("LM Studio", API_OPENAI, "http://192.168.1.2:1234/v1", "lmstudio.ai", false),
    };

    private static final ArrayList<Provider> providers = new ArrayList<>();
    private static SharedPreferences prefs;

    // Settings of the assistant.
    public static String activeId = "";
    public static String systemPrompt = "";
    /** Below zero: leave the provider's default. */
    public static float temperature = -1f;
    public static int maxTokens = 4096;
    /** How many of the latest messages of a chat go into a summary. */
    public static int contextSize = 100;
    /** The user has agreed once that chat text is sent to the provider they chose. */
    public static boolean consent = false;
    /** AI actions in the menu of a message and of a chat. */
    public static boolean inChat = true;

    private Ai() {
    }

    private static synchronized SharedPreferences prefs() {
        if (prefs == null) {
            final Context context = ApplicationLoader.applicationContext;
            prefs = context.getSharedPreferences("ngram_ai", Context.MODE_PRIVATE);
            load(context);
        }
        return prefs;
    }

    private static void load(Context context) {
        providers.clear();
        try {
            final JSONArray arr = new JSONArray(prefs.getString("providers", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                final JSONObject o = arr.getJSONObject(i);
                final Provider p = new Provider(o.getString("id"));
                p.name = o.optString("name");
                p.api = "anthropic".equals(o.optString("api")) ? API_ANTHROPIC : API_OPENAI;
                p.baseUrl = o.optString("baseUrl");
                p.apiKey = KeyVault.open(o.optString("key"));
                p.model = o.optString("model");
                p.sttModel = o.optString("stt");
                final JSONArray m = o.optJSONArray("models");
                if (m != null) {
                    for (int j = 0; j < m.length(); j++) {
                        p.models.add(m.getString(j));
                    }
                }
                providers.add(p);
            }
        } catch (Throwable ignore) {
        }
        if (!prefs.contains("settingsMoved")) {
            moveOldSettings(context);
        }
        activeId = prefs.getString("active", "");
        systemPrompt = prefs.getString("systemPrompt", "");
        temperature = prefs.getFloat("temperature", -1f);
        maxTokens = prefs.getInt("maxTokens", 4096);
        contextSize = prefs.getInt("contextSize", 100);
        consent = prefs.getBoolean("consent", false);
        inChat = prefs.getBoolean("inChat", true);
    }

    /** Ngram 2.x kept the assistant's settings in its general preferences file. */
    private static void moveOldSettings(Context context) {
        final SharedPreferences.Editor e = prefs.edit().putBoolean("settingsMoved", true);
        try {
            final SharedPreferences old = context.getSharedPreferences("ngram", Context.MODE_PRIVATE);
            if (old.contains("aiProvider")) {
                e.putString("active", old.getString("aiProvider", ""));
            }
            if (old.contains("aiSystemPrompt")) {
                e.putString("systemPrompt", old.getString("aiSystemPrompt", ""));
            }
            if (old.contains("aiTemperature")) {
                e.putFloat("temperature", old.getFloat("aiTemperature", -1f));
            }
            if (old.contains("aiMaxTokens")) {
                e.putInt("maxTokens", old.getInt("aiMaxTokens", 4096));
            }
            if (old.contains("aiContextSize")) {
                e.putInt("contextSize", old.getInt("aiContextSize", 100));
            }
            if (old.contains("aiConsent")) {
                e.putBoolean("consent", old.getBoolean("aiConsent", false));
            }
        } catch (Throwable ignore) {
            // A value of another type in the old file: start from the defaults.
        }
        e.apply();
    }

    public static synchronized List<Provider> providers() {
        prefs();
        return new ArrayList<>(providers);
    }

    public static synchronized Provider active() {
        prefs();
        for (Provider p : providers) {
            if (p.id.equals(activeId)) {
                return p;
            }
        }
        return providers.isEmpty() ? null : providers.get(0);
    }

    /** True when there is somewhere to send a request. */
    public static boolean ready() {
        final Provider p = active();
        return p != null && p.ready();
    }

    public static synchronized Provider find(String id) {
        prefs();
        for (Provider p : providers) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    public static synchronized Provider add(Preset preset) {
        prefs();
        final Provider p = new Provider(UUID.randomUUID().toString());
        if (preset != null) {
            p.name = preset.name;
            p.api = preset.api;
            p.baseUrl = preset.baseUrl;
        }
        providers.add(p);
        if (providers.size() == 1) {
            activeId = p.id;
        }
        save();
        return p;
    }

    public static synchronized void remove(Provider p) {
        prefs();
        providers.remove(p);
        if (p.id.equals(activeId)) {
            activeId = providers.isEmpty() ? "" : providers.get(0).id;
        }
        save();
    }

    public static Preset presetFor(Provider p) {
        String base = p.baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        for (Preset preset : PRESETS) {
            if (preset.baseUrl.equals(base)) {
                return preset;
            }
        }
        return null;
    }

    /** Writes the providers and the settings. Call after changing any of them. */
    public static synchronized void save() {
        final SharedPreferences sp = prefs();
        final JSONArray arr = new JSONArray();
        try {
            for (Provider p : providers) {
                arr.put(new JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("api", p.api == API_ANTHROPIC ? "anthropic" : "openai")
                    .put("baseUrl", p.baseUrl.trim())
                    .put("key", KeyVault.seal(p.apiKey.trim()))
                    .put("model", p.model.trim())
                    .put("stt", p.sttModel.trim())
                    .put("models", new JSONArray(p.models)));
            }
        } catch (Throwable ignore) {
        }
        sp.edit()
            .putString("providers", arr.toString())
            .putString("active", activeId)
            .putString("systemPrompt", systemPrompt)
            .putFloat("temperature", temperature)
            .putInt("maxTokens", maxTokens)
            .putInt("contextSize", contextSize)
            .putBoolean("consent", consent)
            .putBoolean("inChat", inChat)
            .apply();
    }

    /** The assistant's settings and providers for a backup file. Keys are left out on purpose. */
    public static synchronized JSONObject exportSettings() throws Exception {
        prefs();
        final JSONObject root = new JSONObject();
        root.put("systemPrompt", systemPrompt);
        root.put("temperature", (double) temperature);
        root.put("maxTokens", maxTokens);
        root.put("contextSize", contextSize);
        root.put("inChat", inChat);
        final JSONArray list = new JSONArray();
        for (Provider p : providers) {
            list.put(new JSONObject()
                .put("name", p.name)
                .put("api", p.api == API_ANTHROPIC ? "anthropic" : "openai")
                .put("baseUrl", p.baseUrl.trim())
                .put("model", p.model.trim())
                .put("stt", p.sttModel.trim())
                .put("active", p.id.equals(activeId)));
        }
        root.put("providers", list);
        return root;
    }

    /**
     * Takes the assistant's settings from a backup. Providers that are not here yet are added
     * without a key; the ones already here keep their key and get the model from the file.
     * Returns how many settings and providers were taken.
     */
    public static synchronized int importSettings(JSONObject root) {
        prefs();
        int applied = 0;
        if (root.has("systemPrompt")) {
            systemPrompt = root.optString("systemPrompt", "");
            applied++;
        }
        if (root.has("temperature")) {
            temperature = (float) root.optDouble("temperature", -1);
            applied++;
        }
        if (root.has("maxTokens")) {
            maxTokens = Math.max(64, Math.min(200000, root.optInt("maxTokens", 4096)));
            applied++;
        }
        if (root.has("contextSize")) {
            contextSize = Math.max(10, Math.min(1000, root.optInt("contextSize", 100)));
            applied++;
        }
        if (root.has("inChat")) {
            inChat = root.optBoolean("inChat", true);
            applied++;
        }
        final JSONArray list = root.optJSONArray("providers");
        if (list != null) {
            for (int i = 0; i < list.length() && i < 40; i++) {
                final JSONObject o = list.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                final String baseUrl = o.optString("baseUrl", "").trim();
                if (baseUrl.isEmpty()) {
                    continue;
                }
                Provider target = null;
                for (Provider p : providers) {
                    if (p.baseUrl.trim().equalsIgnoreCase(baseUrl)) {
                        target = p;
                        break;
                    }
                }
                if (target == null) {
                    target = new Provider(UUID.randomUUID().toString());
                    target.baseUrl = baseUrl;
                    target.name = o.optString("name", "");
                    target.api = "anthropic".equals(o.optString("api")) ? API_ANTHROPIC : API_OPENAI;
                    providers.add(target);
                }
                target.model = o.optString("model", target.model);
                target.sttModel = o.optString("stt", target.sttModel);
                if (o.optBoolean("active") || providers.size() == 1) {
                    activeId = target.id;
                }
                applied++;
            }
        }
        save();
        return applied;
    }

    /**
     * Keeps API keys encrypted with a key that never leaves the Android Keystore, so a copy of
     * the preferences file alone is not enough to read them.
     */
    private static final class KeyVault {
        private static final String ALIAS = "ngram_ai_keys";

        private static SecretKey key() throws Exception {
            final KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            final KeyStore.Entry entry = ks.getEntry(ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
            final KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
            return gen.generateKey();
        }

        static String seal(String plain) {
            if (plain.isEmpty()) {
                return "";
            }
            try {
                final Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.ENCRYPT_MODE, key());
                final byte[] data = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
                return "v1:" + Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP);
            } catch (Throwable e) {
                // A broken keystore must not lock the user out of the feature.
                return "p:" + Base64.encodeToString(plain.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            }
        }

        static String open(String sealed) {
            if (sealed == null || sealed.isEmpty()) {
                return "";
            }
            try {
                if (sealed.startsWith("v1:")) {
                    final String[] parts = sealed.split(":");
                    final Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                    c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[1], Base64.NO_WRAP)));
                    return new String(c.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)), StandardCharsets.UTF_8);
                } else if (sealed.startsWith("p:")) {
                    return new String(Base64.decode(sealed.substring(2), Base64.NO_WRAP), StandardCharsets.UTF_8);
                }
            } catch (Throwable ignore) {
            }
            return "";
        }
    }
}
