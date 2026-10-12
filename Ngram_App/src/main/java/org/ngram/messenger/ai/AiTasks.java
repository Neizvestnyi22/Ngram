package org.ngram.messenger.ai;

import static org.ngram.messenger.N.tr;

import org.ngram.messenger.N;
import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ready-made jobs for the assistant: the prompts behind the buttons, and how a job is run. */
public final class AiTasks {

    public interface Listener {
        /** The answer so far; called on the main thread, at most about 20 times a second. */
        void onText(String text);

        /** The job is over; error is null when it succeeded. Main thread. */
        void onDone(String text, String error);
    }

    /** A running job. */
    public static final class Job {
        private final AiClient.Call call = new AiClient.Call();
        private volatile boolean finished;

        public void cancel() {
            call.cancel();
        }

        public boolean running() {
            return !finished;
        }
    }

    private AiTasks() {
    }

    private static String langName() {
        return N.russian() ? "Russian" : "English";
    }

    public static String chatSystemPrompt() {
        final String base = "You are the assistant built into Ngram, a Telegram client. Answer in the language the user writes in. "
            + "Be direct and useful; use Markdown for structure and fenced code blocks for code.";
        final String custom = Ai.systemPrompt.trim();
        return custom.isEmpty() ? base : base + "\n\nUser's standing instructions:\n" + custom;
    }

    private static String toolSystemPrompt() {
        final String base = "You are a writing and reading assistant inside a messenger. Do exactly what is asked and output only the result: "
            + "no preamble, no closing remarks, no quotes around it.";
        final String custom = Ai.systemPrompt.trim();
        return custom.isEmpty() ? base : base + "\n\nUser's standing instructions:\n" + custom;
    }

    /**
     * Streams an answer on a background thread and reports the text gathered so far on the
     * main thread, throttled so that a fast model does not flood the screen with updates.
     */
    public static Job stream(Ai.Provider provider, String system, List<AiClient.Turn> turns, Listener listener) {
        final Job job = new Job();
        final StringBuffer text = new StringBuffer();
        final boolean[] scheduled = {false};
        final Runnable push = () -> {
            synchronized (scheduled) {
                scheduled[0] = false;
            }
            if (!job.finished) {
                listener.onText(text.toString());
            }
        };
        new Thread(() -> {
            String error = null;
            try {
                AiClient.chat(provider, system, turns, piece -> {
                    text.append(piece);
                    synchronized (scheduled) {
                        if (scheduled[0]) {
                            return;
                        }
                        scheduled[0] = true;
                    }
                    AndroidUtilities.runOnUIThread(push, 48);
                }, job.call);
            } catch (AiClient.AiException e) {
                error = e.getMessage() == null ? "" : e.getMessage();
            } catch (Throwable e) {
                error = String.valueOf(e.getMessage());
            }
            final String finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                job.finished = true;
                AndroidUtilities.cancelRunOnUIThread(push);
                // Whatever has arrived is shown even if the stream was cut short.
                listener.onDone(text.toString(), finalError);
            });
        }, "ngram-ai").start();
        return job;
    }

    /** Runs a one-off job (explain, translate, summarize) with the active provider. */
    public static Job run(String prompt, String imageBase64, Listener listener) {
        final Ai.Provider provider = Ai.active();
        if (provider == null || !provider.ready()) {
            final Job job = new Job();
            job.finished = true;
            AndroidUtilities.runOnUIThread(() -> listener.onDone("", tr("Подключите провайдера в настройках ИИ", "Connect a provider in AI settings")));
            return job;
        }
        return stream(provider, toolSystemPrompt(), Collections.singletonList(new AiClient.Turn(false, prompt, imageBase64)), listener);
    }

    public static String explain(String text) {
        return "Explain the following message in " + langName() + ": what it means, including slang, abbreviations, "
            + "references or technical terms. Keep it short.\n\nMessage:\n" + text;
    }

    public static String translate(String text) {
        return "Translate the following text into " + langName() + ". Preserve meaning, tone and formatting. Output only the translation.\n\nText:\n" + text;
    }

    public static String summarizeText(String text) {
        return "Summarize the following text in " + langName() + " in a few short bullet points.\n\nText:\n" + text;
    }

    public static String summarizeChat(String transcript) {
        return "Below is a fragment of a chat. Write a summary in " + langName() + ": the main topics, "
            + "decisions, open questions and anything addressed to the reader. Use short bullet points grouped by topic; "
            + "mention who said what where it matters.\n\nChat:\n" + transcript;
    }

    public static String askChat(String transcript, String question) {
        return "Below is a fragment of a chat, then a question about it. Answer in " + langName()
            + ", based only on the chat.\n\nChat:\n" + transcript + "\n\nQuestion: " + question;
    }

    public static String askAbout(String text, String question) {
        return "Message:\n" + text + "\n\nQuestion about it: " + question + "\n\nAnswer in " + langName() + ".";
    }

    public static String replies(String transcript, String me) {
        return "Below is the end of a chat. Suggest three different short replies that the participant labelled \"" + me
            + "\" could send next, in the language of the chat and matching its tone. Output exactly three lines, "
            + "one reply per line, no numbering, no quotes.\n\nChat:\n" + transcript;
    }

    /** A way to rework the text in the message field. */
    public static final class Rewrite {
        public final String ru, en, instruction;

        Rewrite(String ru, String en, String instruction) {
            this.ru = ru;
            this.en = en;
            this.instruction = instruction;
        }

        public String title() {
            return tr(ru, en);
        }
    }

    public static final List<Rewrite> REWRITES;

    static {
        final ArrayList<Rewrite> list = new ArrayList<>();
        list.add(new Rewrite("Улучшить текст", "Improve Writing", "Improve the wording so it reads clearly and naturally."));
        list.add(new Rewrite("Исправить ошибки", "Fix Mistakes", "Fix spelling, grammar and punctuation. Change nothing else."));
        list.add(new Rewrite("Короче", "Make Shorter", "Make it noticeably shorter while keeping the meaning."));
        list.add(new Rewrite("Подробнее", "Make Longer", "Expand it with a little more detail, staying natural."));
        list.add(new Rewrite("Дружелюбнее", "Friendlier", "Rewrite it in a warm, friendly tone."));
        list.add(new Rewrite("Официальнее", "More Formal", "Rewrite it in a polite, formal tone."));
        list.add(new Rewrite("Перевести на английский", "Translate to English", "Translate it into English."));
        list.add(new Rewrite("Перевести на русский", "Translate to Russian", "Translate it into Russian."));
        REWRITES = Collections.unmodifiableList(list);
    }

    public static String rewrite(String text, Rewrite r) {
        return r.instruction + " Keep the original language unless told to translate. Output only the resulting text.\n\nText:\n" + text;
    }
}
