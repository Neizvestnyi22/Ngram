package org.ngram.messenger.features;

import static org.ngram.messenger.N.tr;

import android.text.TextUtils;

import org.ngram.messenger.ui.InfoSheet;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Counts over the messages of a chat that are loaded on screen: who wrote how much, what kinds
 * of messages, the busiest hour, the most used words. Counted on the device from what is
 * already there.
 */
public final class ChatStats {

    private ChatStats() {
    }

    public static void show(ChatActivity chat) {
        final ArrayList<MessageObject> all = chat.messages;
        final MessagesController controller = chat.getMessagesController();
        final long selfId = chat.getUserConfig().getClientUserId();

        int total = 0, photos = 0, videos = 0, voices = 0, stickers = 0, files = 0, links = 0, forwarded = 0, replies = 0;
        long characters = 0, words = 0;
        int first = Integer.MAX_VALUE, last = 0;
        final HashMap<Long, int[]> bySender = new HashMap<>();
        final int[] byHour = new int[24];
        final int[] byWeekday = new int[8];
        final HashMap<String, int[]> byWord = new HashMap<>();
        final Calendar calendar = Calendar.getInstance();

        if (all != null) {
            for (MessageObject m : all) {
                if (m == null || m.messageOwner == null || m.isDateObject || m.isSponsored() || m.getId() == 0 || m.messageOwner.action != null) {
                    continue;
                }
                final TLRPC.Message o = m.messageOwner;
                total++;
                first = Math.min(first, o.date);
                last = Math.max(last, o.date);
                final long sender = m.isOut() && m.getSenderId() > 0 ? selfId : m.getSenderId();
                int[] count = bySender.get(sender);
                if (count == null) {
                    bySender.put(sender, count = new int[1]);
                }
                count[0]++;
                calendar.setTimeInMillis(o.date * 1000L);
                byHour[calendar.get(Calendar.HOUR_OF_DAY)]++;
                byWeekday[calendar.get(Calendar.DAY_OF_WEEK)]++;

                switch (m.type) {
                    case MessageObject.TYPE_PHOTO:
                        photos++;
                        break;
                    case MessageObject.TYPE_VIDEO:
                    case MessageObject.TYPE_GIF:
                        videos++;
                        break;
                    case MessageObject.TYPE_VOICE:
                    case MessageObject.TYPE_ROUND_VIDEO:
                        voices++;
                        break;
                    case MessageObject.TYPE_STICKER:
                    case MessageObject.TYPE_ANIMATED_STICKER:
                        stickers++;
                        break;
                    case MessageObject.TYPE_FILE:
                    case MessageObject.TYPE_MUSIC:
                        files++;
                        break;
                }
                if (o.fwd_from != null) {
                    forwarded++;
                }
                if (o.reply_to != null && o.reply_to.reply_to_msg_id != 0) {
                    replies++;
                }
                final String text = !TextUtils.isEmpty(o.message) ? o.message : m.caption == null ? "" : m.caption.toString();
                if (text.isEmpty()) {
                    continue;
                }
                characters += text.codePointCount(0, text.length());
                for (String raw : text.split("\\s+")) {
                    if (raw.isEmpty()) {
                        continue;
                    }
                    words++;
                    if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("t.me/")) {
                        links++;
                        continue;
                    }
                    final String word = raw.toLowerCase(Locale.ROOT).replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
                    // Short words are mostly "and", "the", "что", "это": they say nothing about a chat.
                    if (word.codePointCount(0, word.length()) < 5) {
                        continue;
                    }
                    int[] n = byWord.get(word);
                    if (n == null) {
                        byWord.put(word, n = new int[1]);
                    }
                    n[0]++;
                }
            }
        }

        if (total == 0) {
            BulletinFactory.of(chat).createSimpleBulletin(R.raw.error, tr("Здесь пока нечего считать.", "Nothing to count here yet.")).show();
            return;
        }

        final InfoSheet.Rows rows = new InfoSheet.Rows();
        rows.add(tr("Сообщений загружено", "Messages Loaded"), total);
        rows.add(tr("Период", "Period"), Details.date(first) + " — " + Details.date(last));
        final long days = Math.max(1, (last - first) / 86400 + 1);
        rows.add(tr("В среднем за день", "Per Day on Average"), String.format(Locale.US, "%.1f", total / (double) days));

        rows.section(tr("Кто пишет", "Who Writes"));
        final ArrayList<Map.Entry<Long, int[]>> senders = new ArrayList<>(bySender.entrySet());
        Collections.sort(senders, (a, b) -> b.getValue()[0] - a.getValue()[0]);
        for (int i = 0; i < senders.size() && i < 8; i++) {
            final long id = senders.get(i).getKey();
            final int n = senders.get(i).getValue()[0];
            rows.add(name(controller, id, selfId), n + " · " + Math.round(n * 100f / total) + "%");
        }
        if (senders.size() > 8) {
            rows.add(tr("Остальные", "Others"), tr("ещё ", "") + (senders.size() - 8) + tr("", " more"));
        }

        rows.section(tr("Что пишут", "What Is Written"));
        rows.add(tr("Слов", "Words"), words);
        rows.add(tr("Знаков", "Characters"), characters);
        if (photos > 0) rows.add(tr("Фото", "Photos"), photos);
        if (videos > 0) rows.add(tr("Видео и GIF", "Videos and GIFs"), videos);
        if (voices > 0) rows.add(tr("Голосовых и видеосообщений", "Voice and Video Messages"), voices);
        if (stickers > 0) rows.add(tr("Стикеров", "Stickers"), stickers);
        if (files > 0) rows.add(tr("Файлов и музыки", "Files and Music"), files);
        if (links > 0) rows.add(tr("Ссылок", "Links"), links);
        if (forwarded > 0) rows.add(tr("Пересланных", "Forwarded"), forwarded);
        if (replies > 0) rows.add(tr("Ответов", "Replies"), replies);

        rows.section(tr("Когда пишут", "When"));
        int bestHour = 0;
        for (int h = 1; h < 24; h++) {
            if (byHour[h] > byHour[bestHour]) {
                bestHour = h;
            }
        }
        rows.add(tr("Самый активный час", "Busiest Hour"), String.format(Locale.US, "%02d:00–%02d:00 · %d", bestHour, (bestHour + 1) % 24, byHour[bestHour]));
        int bestDay = Calendar.SUNDAY;
        for (int d = Calendar.SUNDAY; d <= Calendar.SATURDAY; d++) {
            if (byWeekday[d] > byWeekday[bestDay]) {
                bestDay = d;
            }
        }
        calendar.set(Calendar.DAY_OF_WEEK, bestDay);
        final String dayName = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());
        rows.add(tr("Самый активный день недели", "Busiest Day of the Week"), (dayName == null ? String.valueOf(bestDay) : dayName) + " · " + byWeekday[bestDay]);

        // Protected chats keep their words to themselves: the counts above say nothing of the content.
        if (!chat.isPeerNoForwards() && !chat.isSecretChat()) {
            final ArrayList<Map.Entry<String, int[]>> top = new ArrayList<>(byWord.entrySet());
            Collections.sort(top, (a, b) -> b.getValue()[0] - a.getValue()[0]);
            final ArrayList<String> frequent = new ArrayList<>();
            for (int i = 0; i < top.size() && frequent.size() < 10; i++) {
                if (top.get(i).getValue()[0] < 2) {
                    break;
                }
                frequent.add(top.get(i).getKey() + " (" + top.get(i).getValue()[0] + ")");
            }
            if (!frequent.isEmpty()) {
                rows.section(tr("Частые слова", "Frequent Words"));
                rows.add(tr("От пяти букв", "Five Letters and Longer"), TextUtils.join(", ", frequent));
            }
        }
        InfoSheet.show(chat, tr("Разбор чата", "Chat Breakdown"), rows);
    }

    private static String name(MessagesController controller, long id, long selfId) {
        if (id == selfId) {
            return tr("Я", "Me");
        }
        if (id > 0) {
            final TLRPC.User user = controller.getUser(id);
            return user == null ? tr("Пользователь ", "User ") + id : UserObject.getUserName(user);
        }
        final TLRPC.Chat chat = controller.getChat(-id);
        return chat == null ? tr("Чат ", "Chat ") + (-id) : chat.title;
    }
}
