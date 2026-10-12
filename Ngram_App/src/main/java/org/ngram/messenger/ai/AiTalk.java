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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The conversations with the assistant. Several are kept, one of them is open; all of them
 * live in one small file in the app's private storage, so they are still there after the app
 * was closed, and nowhere else.
 *
 * Everything here is used from the main thread.
 */
public final class AiTalk {

    /** How many conversations are kept; the one untouched for the longest goes first. */
    public static final int LIMIT = 40;

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

    /** One conversation. */
    public static final class Talk {
        public final String id;
        /** Set by the user; empty means "named after its first question". */
        public String title = "";
        /** Seconds; when something was last said in it. */
        public long updated;
        final ArrayList<Message> messages = new ArrayList<>();

        Talk(String id) {
            this.id = id;
        }

        public boolean isEmpty() {
            return messages.isEmpty();
        }

        public int size() {
            return messages.size();
        }

        /** The name shown in the list. */
        public String name() {
            if (!title.isEmpty()) {
                return title;
            }
            for (Message m : messages) {
                if (!m.assistant && !m.text.trim().isEmpty()) {
                    final String line = m.text.trim().replace('\n', ' ');
                    return line.length() > 48 ? line.substring(0, 48) + "…" : line;
                }
            }
            return tr("Новый разговор", "New Conversation");
        }
    }

    public interface Listener {
        /** Messages were added or removed, or the conversation started or stopped generating. */
        void onChanged();

        /** The text of the answer being written has grown. */
        void onText(Message message);
    }

    private static final ArrayList<Talk> talks = new ArrayList<>();
    private static Talk current;
    private static boolean loaded;
    private static AiTasks.Job job;
    private static Listener listener;

    private AiTalk() {
    }

    public static void setListener(Listener value) {
        listener = value;
    }

    /** The messages of the open conversation. */
    public static List<Message> messages() {
        load();
        return current.messages;
    }

    public static Talk current() {
        load();
        return current;
    }

    /** Every conversation that holds something, the latest first. */
    public static List<Talk> all() {
        load();
        final ArrayList<Talk> list = new ArrayList<>();
        for (Talk t : talks) {
            if (!t.isEmpty()) {
                list.add(t);
            }
        }
        Collections.sort(list, (a, b) -> Long.compare(b.updated, a.updated));
        return list;
    }

    public static boolean generating() {
        return job != null && job.running();
    }

    // ---- storage ------------------------------------------------------------------------

    private static File dir() {
        final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "ai");
        dir.mkdirs();
        return dir;
    }

    private static String read(File f) throws Exception {
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
        return new String(data, StandardCharsets.UTF_8);
    }

    private static void readMessages(JSONArray arr, Talk into) throws Exception {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            final JSONObject o = arr.getJSONObject(i);
            final Message m = new Message(o.optBoolean("assistant"), o.optString("text"));
            m.error = o.optBoolean("error");
            m.model = o.optString("model");
            into.messages.add(m);
        }
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        String openId = "";
        try {
            final File file = new File(dir(), "talks3.json");
            if (file.exists()) {
                final JSONObject root = new JSONObject(read(file));
                openId = root.optString("current");
                final JSONArray list = root.optJSONArray("talks");
                for (int i = 0; list != null && i < list.length(); i++) {
                    final JSONObject o = list.getJSONObject(i);
                    final Talk t = new Talk(o.optString("id", UUID.randomUUID().toString()));
                    t.title = o.optString("title");
                    t.updated = o.optLong("updated");
                    readMessages(o.optJSONArray("messages"), t);
                    talks.add(t);
                }
            } else {
                // Ngram 3.0 kept a single conversation in its own file.
                final File single = new File(dir(), "talk3.json");
                if (single.exists()) {
                    final Talk t = new Talk(UUID.randomUUID().toString());
                    readMessages(new JSONObject(read(single)).optJSONArray("messages"), t);
                    t.updated = single.lastModified() / 1000;
                    if (!t.isEmpty()) {
                        talks.add(t);
                        openId = t.id;
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        for (Talk t : talks) {
            if (t.id.equals(openId)) {
                current = t;
            }
        }
        if (current == null) {
            current = new Talk(UUID.randomUUID().toString());
            talks.add(current);
        }
    }

    private static void save() {
        final String text;
        try {
            final JSONArray list = new JSONArray();
            for (Talk t : talks) {
                if (t.isEmpty()) {
                    continue;
                }
                final JSONArray arr = new JSONArray();
                for (Message m : t.messages) {
                    arr.put(new JSONObject().put("assistant", m.assistant).put("text", m.text).put("error", m.error).put("model", m.model));
                }
                list.put(new JSONObject().put("id", t.id).put("title", t.title).put("updated", t.updated).put("messages", arr));
            }
            text = new JSONObject().put("current", current == null ? "" : current.id).put("talks", list).toString();
        } catch (Throwable e) {
            return;
        }
        final File f = new File(dir(), "talks3.json");
        Utilities.globalQueue.postRunnable(() -> {
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignore) {
            }
        });
    }

    // ---- conversations ------------------------------------------------------------------

    /** Opens an empty conversation; the one that was open stays in the list. */
    public static void startNew() {
        load();
        stop();
        job = null;
        if (!current.isEmpty()) {
            current = new Talk(UUID.randomUUID().toString());
            talks.add(current);
        }
        save();
        changed();
    }

    public static void open(Talk talk) {
        load();
        if (talk == null || talk == current || !talks.contains(talk)) {
            return;
        }
        stop();
        job = null;
        if (current.isEmpty()) {
            talks.remove(current);
        }
        current = talk;
        save();
        changed();
    }

    public static void rename(Talk talk, String title) {
        load();
        if (talk != null) {
            talk.title = title == null ? "" : title.trim();
            save();
            changed();
        }
    }

    public static void delete(Talk talk) {
        load();
        if (talk == null) {
            return;
        }
        if (talk == current) {
            stop();
            job = null;
            current = new Talk(UUID.randomUUID().toString());
            talks.add(current);
        }
        talks.remove(talk);
        save();
        changed();
    }

    /** Drops the conversations that were untouched for the longest once there are too many. */
    private static void trim() {
        while (talks.size() > LIMIT) {
            Talk oldest = null;
            for (Talk t : talks) {
                if (t != current && (oldest == null || t.updated < oldest.updated)) {
                    oldest = t;
                }
            }
            if (oldest == null) {
                return;
            }
            talks.remove(oldest);
        }
    }

    // ---- talking ------------------------------------------------------------------------

    /** Adds the user's message and streams the answer into a new assistant message. */
    public static void ask(String text) {
        load();
        final String question = text == null ? "" : text.trim();
        if (question.isEmpty() || generating()) {
            return;
        }
        current.messages.add(new Message(false, question));
        generate();
    }

    /** Drops the last answer and asks again. */
    public static void regenerate() {
        load();
        if (generating()) {
            return;
        }
        final ArrayList<Message> messages = current.messages;
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

    private static void changed() {
        if (listener != null) {
            listener.onChanged();
        }
    }

    private static void generate() {
        final Talk talk = current;
        final ArrayList<Message> messages = talk.messages;
        talk.updated = System.currentTimeMillis() / 1000;
        trim();

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
                if (listener != null && talk == current) {
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
                if (talk == current) {
                    job = null;
                }
                talk.updated = System.currentTimeMillis() / 1000;
                save();
                changed();
            }
        });
        changed();
    }
}
