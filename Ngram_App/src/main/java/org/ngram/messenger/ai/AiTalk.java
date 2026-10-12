package org.ngram.messenger.ai;

import static org.ngram.messenger.N.tr;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The conversation with the assistant. One conversation at a time, kept in a small file in the
 * app's private storage so that it is still there after the app was closed.
 *
 * Everything here is used from the main thread.
 */
public final class AiTalk {

    public static final class Message {
        public final boolean assistant;
        public String text;
        public boolean error;
        public boolean streaming;
        /** Model that produced an answer; shown above it. */
        public String model = "";

        Message(boolean assistant, String text) {
            this.assistant = assistant;
            this.text = text;
        }
    }

    public interface Listener {
        /** Messages were added or removed, or the conversation started or stopped generating. */
        void onChanged();

        /** The text of the answer being written has grown. */
        void onText(Message message);
    }

    private static final ArrayList<Message> messages = new ArrayList<>();
    private static boolean loaded;
    private static AiTasks.Job job;
    private static Listener listener;

    private AiTalk() {
    }

    public static void setListener(Listener value) {
        listener = value;
    }

    public static List<Message> messages() {
        load();
        return messages;
    }

    public static boolean generating() {
        return job != null && job.running();
    }

    private static File file() {
        final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "ai");
        dir.mkdirs();
        return new File(dir, "talk3.json");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            final File f = file();
            if (!f.exists()) {
                return;
            }
            final byte[] data = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                int read = 0;
                while (read < data.length) {
                    final int n = in.read(data, read, data.length - read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
            }
            final JSONArray arr = new JSONObject(new String(data, StandardCharsets.UTF_8)).optJSONArray("messages");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    final JSONObject o = arr.getJSONObject(i);
                    final Message m = new Message(o.optBoolean("assistant"), o.optString("text"));
                    m.error = o.optBoolean("error");
                    m.model = o.optString("model");
                    messages.add(m);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private static void save() {
        final String text;
        try {
            final JSONArray arr = new JSONArray();
            for (Message m : messages) {
                arr.put(new JSONObject().put("assistant", m.assistant).put("text", m.text).put("error", m.error).put("model", m.model));
            }
            text = new JSONObject().put("messages", arr).toString();
        } catch (Throwable e) {
            return;
        }
        final File f = file();
        Utilities.globalQueue.postRunnable(() -> {
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignore) {
            }
        });
    }

    /** Adds the user's message and streams the answer into a new assistant message. */
    public static void ask(String text) {
        load();
        final String question = text == null ? "" : text.trim();
        if (question.isEmpty() || generating()) {
            return;
        }
        messages.add(new Message(false, question));
        generate();
    }

    /** Drops the last answer and asks again. */
    public static void regenerate() {
        load();
        if (generating()) {
            return;
        }
        while (!messages.isEmpty() && messages.get(messages.size() - 1).assistant) {
            messages.remove(messages.size() - 1);
        }
        if (!messages.isEmpty()) {
            generate();
        }
    }

    public static void stop() {
        if (job != null) {
            job.cancel();
        }
    }

    public static void clear() {
        stop();
        job = null;
        messages.clear();
        save();
        changed();
    }

    private static void changed() {
        if (listener != null) {
            listener.onChanged();
        }
    }

    private static void generate() {
        final Message answer = new Message(true, "");
        messages.add(answer);
        final Ai.Provider provider = Ai.active();
        if (provider == null || !provider.ready()) {
            answer.error = true;
            answer.text = tr("Сначала подключите провайдера и выберите модель в настройках ассистента.", "Connect a provider and pick a model in the assistant's settings first.");
            changed();
            return;
        }
        answer.model = provider.model;
        answer.streaming = true;

        // The last 40 turns are enough context and keep a long conversation affordable.
        final ArrayList<AiClient.Turn> turns = new ArrayList<>();
        for (Message m : messages) {
            if (m != answer && !m.error && !m.text.trim().isEmpty()) {
                turns.add(new AiClient.Turn(m.assistant, m.text, null));
            }
        }
        while (turns.size() > 40) {
            turns.remove(0);
        }

        job = AiTasks.stream(provider, AiTasks.chatSystemPrompt(), turns, new AiTasks.Listener() {
            @Override
            public void onText(String text) {
                answer.text = text;
                if (listener != null) {
                    listener.onText(answer);
                }
            }

            @Override
            public void onDone(String text, String error) {
                answer.streaming = false;
                answer.text = text;
                if (error != null) {
                    if (text.isEmpty()) {
                        if (error.isEmpty()) {
                            // Stopped by the user before anything arrived.
                            messages.remove(answer);
                        } else {
                            answer.error = true;
                            answer.text = error;
                        }
                    } else if (!error.isEmpty()) {
                        // Keep whatever arrived before the failure; the error goes in its own message.
                        final Message failure = new Message(true, error);
                        failure.error = true;
                        messages.add(failure);
                    }
                }
                job = null;
                save();
                changed();
            }
        });
        changed();
    }
}
