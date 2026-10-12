package org.ngram.messenger.ai;

import static org.ngram.messenger.N.tr;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Talks to OpenAI-compatible and Anthropic endpoints over plain HttpURLConnection. Every call
 * blocks: run it on a background thread (see {@link AiTasks}).
 */
public final class AiClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /** A failure with a message meant for the user. */
    public static final class AiException extends Exception {
        public AiException(String message) {
            super(message);
        }
    }

    /** One message of a conversation as sent to a provider. */
    public static final class Turn {
        public final boolean assistant;
        public final String text;
        /** A JPEG as base64, or null. */
        public final String imageBase64;

        public Turn(boolean assistant, String text, String imageBase64) {
            this.assistant = assistant;
            this.text = text == null ? "" : text;
            this.imageBase64 = imageBase64;
        }
    }

    public interface Delta {
        /** A fragment of the answer, on the calling (background) thread. */
        void onText(String piece);
    }

    /** Lets the owner of a request stop it: closing the socket unblocks the read. */
    public static final class Call {
        private volatile HttpURLConnection connection;
        private volatile boolean cancelled;

        public void cancel() {
            cancelled = true;
            final HttpURLConnection c = connection;
            if (c != null) {
                new Thread(() -> {
                    try {
                        c.disconnect();
                    } catch (Throwable ignore) {
                    }
                }).start();
            }
        }

        public boolean cancelled() {
            return cancelled;
        }
    }

    private AiClient() {
    }

    /**
     * Keys must not travel in the clear over the internet, so plain http is accepted only for
     * machines on the local network (a self-hosted Ollama or LM Studio).
     */
    private static URL endpoint(Ai.Provider p, String path) throws AiException {
        String base = p.baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty()) {
            throw new AiException(tr("Не указан адрес API", "The API address is empty"));
        }
        final URL url;
        try {
            url = new URL(base + path);
        } catch (Throwable e) {
            throw new AiException(tr("Некорректный адрес API", "The API address is invalid"));
        }
        final String protocol = url.getProtocol();
        if ("http".equals(protocol)) {
            if (!isLocalHost(url.getHost())) {
                throw new AiException(tr("Адрес без https разрешён только для локальной сети", "Plain http is only allowed for local network addresses"));
            }
        } else if (!"https".equals(protocol)) {
            throw new AiException(tr("Некорректный адрес API", "The API address is invalid"));
        }
        return url;
    }

    static boolean isLocalHost(String host) {
        final String h = host == null ? "" : host.toLowerCase(Locale.US);
        if (h.equals("localhost") || h.endsWith(".local") || h.endsWith(".lan") || h.equals("[::1]")) {
            return true;
        }
        final String[] parts = h.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        final int[] n = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                n[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return n[0] == 10 || n[0] == 127 || (n[0] == 192 && n[1] == 168) || (n[0] == 172 && n[1] >= 16 && n[1] <= 31) || (n[0] == 169 && n[1] == 254);
    }

    private static HttpURLConnection open(Ai.Provider p, String path, boolean post, Call call) throws AiException, IOException {
        final HttpURLConnection conn = (HttpURLConnection) endpoint(p, path).openConnection();
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(120_000);
        conn.setRequestMethod(post ? "POST" : "GET");
        conn.setRequestProperty("Accept", "application/json, text/event-stream");
        final String key = p.apiKey.trim();
        if (p.api == Ai.API_ANTHROPIC) {
            conn.setRequestProperty("x-api-key", key);
            conn.setRequestProperty("anthropic-version", ANTHROPIC_VERSION);
        } else if (!key.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + key);
        }
        if (call != null) {
            call.connection = conn;
            if (call.cancelled) {
                throw new IOException("cancelled");
            }
        }
        return conn;
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            final char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    private static String errorMessage(int code, String body) {
        String detail;
        try {
            final JSONObject o = new JSONObject(body);
            final Object e = o.opt("error");
            if (e instanceof JSONObject) {
                detail = ((JSONObject) e).optString("message");
                if (detail.isEmpty()) {
                    detail = e.toString();
                }
            } else if (e instanceof String) {
                detail = (String) e;
            } else {
                detail = o.optString("message");
            }
        } catch (Throwable t) {
            detail = body.length() > 200 ? body.substring(0, 200) : body;
        }
        detail = detail.trim();
        final String hint;
        if (code == 401 || code == 403) {
            hint = tr("Проверьте API-ключ", "Check the API key");
        } else if (code == 404) {
            hint = tr("Проверьте адрес API и название модели", "Check the API address and the model name");
        } else if (code == 429) {
            hint = tr("Превышен лимит запросов или закончился баланс", "Rate limit reached or out of credit");
        } else if (code >= 500 && code <= 599) {
            hint = tr("Сервис провайдера временно недоступен", "The provider is temporarily unavailable");
        } else {
            hint = "";
        }
        final StringBuilder sb = new StringBuilder("HTTP ").append(code);
        if (!hint.isEmpty()) {
            sb.append(" · ").append(hint);
        }
        if (!detail.isEmpty()) {
            sb.append(" · ").append(detail);
        }
        return sb.toString();
    }

    private static void failIfError(HttpURLConnection conn) throws IOException, AiException {
        final int code = conn.getResponseCode();
        if (code < 200 || code > 299) {
            String body = "";
            try {
                body = readAll(conn.getErrorStream());
            } catch (Throwable ignore) {
            }
            throw new AiException(errorMessage(code, body));
        }
    }

    private static void sendJson(HttpURLConnection conn, JSONObject body) throws IOException {
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Turns whatever went wrong into something the user can read. */
    private static AiException explain(Throwable e, Call call) {
        if (e instanceof AiException) {
            return (AiException) e;
        }
        if (call != null && call.cancelled) {
            return new AiException("");
        }
        final String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (e instanceof IOException) {
            return new AiException(tr("Нет связи с провайдером: ", "Couldn't reach the provider: ") + detail);
        }
        return new AiException(detail);
    }

    private static final String[] NON_CHAT = {"embed", "whisper", "tts", "dall-e", "moderation", "rerank", "transcribe", "speech", "imagen", "veo"};

    public static boolean looksLikeChatModel(String id) {
        final String lower = id.toLowerCase(Locale.US);
        for (String s : NON_CHAT) {
            if (lower.contains(s)) {
                return false;
            }
        }
        return true;
    }

    /** Asks the provider which models this key may use. */
    public static List<String> listModels(Ai.Provider p) throws AiException {
        HttpURLConnection conn = null;
        try {
            conn = open(p, p.api == Ai.API_ANTHROPIC ? "/models?limit=1000" : "/models", false, null);
            failIfError(conn);
            final String body = readAll(conn.getInputStream()).trim();
            JSONArray arr;
            if (body.startsWith("[")) {
                arr = new JSONArray(body);
            } else {
                final JSONObject o = new JSONObject(body);
                arr = o.optJSONArray("data");
                if (arr == null) {
                    arr = o.optJSONArray("models");
                }
                if (arr == null) {
                    arr = new JSONArray();
                }
            }
            final LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                final Object item = arr.opt(i);
                if (item instanceof JSONObject) {
                    String id = ((JSONObject) item).optString("id");
                    if (id.isEmpty()) {
                        id = ((JSONObject) item).optString("name");
                    }
                    if (id.startsWith("models/")) {
                        id = id.substring(7);
                    }
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                } else if (item instanceof String) {
                    ids.add((String) item);
                }
            }
            if (ids.isEmpty()) {
                throw new AiException(tr("Провайдер не вернул список моделей", "The provider returned no models"));
            }
            final ArrayList<String> list = new ArrayList<>(ids);
            // Anthropic already lists newest first; elsewhere alphabetical is the least surprising.
            if (p.api != Ai.API_ANTHROPIC) {
                Collections.sort(list, (a, b) -> {
                    final boolean ca = looksLikeChatModel(a), cb = looksLikeChatModel(b);
                    if (ca != cb) {
                        return ca ? -1 : 1;
                    }
                    return a.compareToIgnoreCase(b);
                });
            }
            return list;
        } catch (Throwable e) {
            throw explain(e, null);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static Object openAiContent(Turn t) throws Exception {
        if (t.imageBase64 == null) {
            return t.text;
        }
        return new JSONArray()
            .put(new JSONObject().put("type", "text").put("text", t.text.isEmpty() ? "?" : t.text))
            .put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + t.imageBase64)));
    }

    private static Object anthropicContent(Turn t) throws Exception {
        if (t.imageBase64 == null) {
            return t.text;
        }
        return new JSONArray()
            .put(new JSONObject().put("type", "image").put("source", new JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", t.imageBase64)))
            .put(new JSONObject().put("type", "text").put("text", t.text.isEmpty() ? "?" : t.text));
    }

    /**
     * Streams a completion and returns the whole answer.
     *
     * @param onDelta receives text fragments as they arrive, on the calling thread
     * @param call    lets another thread stop the request; an empty-message exception then follows
     */
    public static String chat(Ai.Provider p, String system, List<Turn> turns, Delta onDelta, Call call) throws AiException {
        final String model = p.model.trim();
        if (model.isEmpty()) {
            throw new AiException(tr("Не выбрана модель", "No model selected"));
        }
        final boolean anthropic = p.api == Ai.API_ANTHROPIC;
        HttpURLConnection conn = null;
        try {
            final JSONObject body = new JSONObject().put("model", model).put("stream", true);
            // Only sent when the user asked for it: several models reject a non-default
            // temperature. Anthropic's range tops out at 1.
            final float temperature = Ai.temperature;
            if (temperature >= 0f) {
                body.put("temperature", Math.round((anthropic ? Math.min(temperature, 1f) : temperature) * 100) / 100.0);
            }
            final JSONArray messages = new JSONArray();
            if (anthropic) {
                body.put("max_tokens", Math.max(256, Math.min(64000, Ai.maxTokens)));
                if (!TextUtils.isEmpty(system)) {
                    body.put("system", system);
                }
                for (Turn t : turns) {
                    messages.put(new JSONObject().put("role", t.assistant ? "assistant" : "user").put("content", anthropicContent(t)));
                }
            } else {
                if (!TextUtils.isEmpty(system)) {
                    messages.put(new JSONObject().put("role", "system").put("content", system));
                }
                for (Turn t : turns) {
                    messages.put(new JSONObject().put("role", t.assistant ? "assistant" : "user").put("content", openAiContent(t)));
                }
            }
            body.put("messages", messages);

            conn = open(p, anthropic ? "/messages" : "/chat/completions", true, call);
            sendJson(conn, body);
            failIfError(conn);

            final StringBuilder out = new StringBuilder();
            final StringBuilder raw = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith(":") || line.startsWith("event:")) {
                        continue;
                    }
                    if (!line.startsWith("data:")) {
                        // Not a stream after all: some gateways answer with one JSON document.
                        raw.append(line).append('\n');
                        continue;
                    }
                    final String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    final JSONObject o;
                    try {
                        o = new JSONObject(data);
                    } catch (Throwable ignore) {
                        continue;
                    }
                    final Object err = o.opt("error");
                    if (err != null && err != JSONObject.NULL) {
                        final String msg = err instanceof JSONObject ? ((JSONObject) err).optString("message") : "";
                        throw new AiException(msg.isEmpty() ? err.toString() : msg);
                    }
                    String piece = "";
                    if (anthropic) {
                        if ("content_block_delta".equals(o.optString("type"))) {
                            final JSONObject d = o.optJSONObject("delta");
                            if (d != null && "text_delta".equals(d.optString("type"))) {
                                piece = d.optString("text");
                            }
                        }
                    } else {
                        final JSONArray choices = o.optJSONArray("choices");
                        final JSONObject first = choices == null ? null : choices.optJSONObject(0);
                        final JSONObject delta = first == null ? null : first.optJSONObject("delta");
                        if (delta != null && !delta.isNull("content")) {
                            piece = delta.optString("content");
                        }
                    }
                    if (!piece.isEmpty()) {
                        out.append(piece);
                        if (onDelta != null) {
                            onDelta.onText(piece);
                        }
                    }
                }
            }
            if (out.length() == 0 && raw.toString().trim().length() > 0) {
                final JSONObject o;
                try {
                    o = new JSONObject(raw.toString());
                } catch (Throwable t) {
                    throw new AiException(tr("Непонятный ответ провайдера", "Unexpected response from the provider"));
                }
                final Object err = o.opt("error");
                if (err != null && err != JSONObject.NULL) {
                    final String msg = err instanceof JSONObject ? ((JSONObject) err).optString("message") : "";
                    throw new AiException(msg.isEmpty() ? err.toString() : msg);
                }
                final StringBuilder text = new StringBuilder();
                if (anthropic) {
                    final JSONArray content = o.optJSONArray("content");
                    if (content != null) {
                        for (int i = 0; i < content.length(); i++) {
                            final JSONObject part = content.optJSONObject(i);
                            if (part != null) {
                                text.append(part.optString("text"));
                            }
                        }
                    }
                } else {
                    final JSONArray choices = o.optJSONArray("choices");
                    final JSONObject first = choices == null ? null : choices.optJSONObject(0);
                    final JSONObject message = first == null ? null : first.optJSONObject("message");
                    if (message != null) {
                        text.append(message.optString("content"));
                    }
                }
                if (text.length() > 0) {
                    out.append(text);
                    if (onDelta != null) {
                        onDelta.onText(text.toString());
                    }
                }
            }
            if (out.length() == 0) {
                throw new AiException(tr("Модель вернула пустой ответ", "The model returned an empty answer"));
            }
            return out.toString();
        } catch (Throwable e) {
            throw explain(e, call);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
