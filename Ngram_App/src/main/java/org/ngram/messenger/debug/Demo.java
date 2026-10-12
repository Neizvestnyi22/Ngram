package org.ngram.messenger.debug;

import org.telegram.messenger.LiteMode;
import android.os.Process;
import android.content.Intent;

import org.ngram.core.NgramConfig;
import org.ngram.core.NgramHooks;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;

/**
 * A made-up account for looking at the app's screens without signing in. Debug builds only:
 *
 *   adb shell am start -n org.ngram.messenger/org.telegram.messenger.DefaultIcon --ez demo true
 *
 * The first start only switches the demo on and closes the app; start it once more.
 *
 * It signs nobody in. The account exists only on the device, and while it is active nothing
 * that needs an account is sent to Telegram: the few requests that fill the main screens are
 * answered here with invented chats, the rest are dropped. Clear the app's data to leave it.
 */
public final class Demo {

    private static final long ME = 5550001L;
    private static final long ALICE = 5551001L, MARK = 5551002L, EVA = 5551003L, SASHA = 5551004L;
    private static final long TEAM = 5552001L;      // a basic group
    private static final long NEWS = 5553001L;      // a channel

    private static int sentId = 900000;
    private static int pts = 1;

    private Demo() {
    }

    public static void onLaunch(Intent intent) {
        final int account = UserConfig.selectedAccount;
        final UserConfig config = UserConfig.getInstance(account);
        if (!NgramConfig.demo) {
            if (intent == null || !intent.getBooleanExtra("demo", false) || config.isClientActivated()) {
                return;
            }
            // By now the app has already sent its first requests as "nobody". Their answers
            // would arrive after the made-up account appears and log it out, so the demo is
            // only switched on here and begins with the next start, from the first request.
            NgramConfig.prefs().edit().putBoolean("demo", true).commit();
            Process.killProcess(Process.myPid());
            return;
        }
        // The emulator counts as a slow device, and on those Telegram draws no glass at all.
        // "--ez glass true" switches every effect on so that glass can be looked at there,
        // "--ez glass false" goes back to the plain look of a slow device.
        if (intent != null && intent.hasExtra("glass")) {
            LiteMode.setAllFlags(intent.getBooleanExtra("glass", false) ? LiteMode.PRESET_HIGH : LiteMode.PRESET_LOW);
        }
        if (config.isClientActivated()) {
            return;
        }

        final TLRPC.User me = user(ME, "Максим", "Демо", "ngram_demo");
        me.self = true;
        me.contact = true;
        me.phone = "15550100";
        me.flags |= 16;

        config.setCurrentUser(me);
        config.saveConfig(true);
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        users.add(me);
        MessagesStorage.getInstance(account).putUsersAndChats(users, null, true, true);
        MessagesController.getInstance(account).putUser(me, false);
        ConnectionsManager.getInstance(account).setUserId(ME);
    }

    /** Requests that need no account and cannot log anyone out: language packs and app config. */
    public static boolean harmless(TLObject request, int flags) {
        if ((flags & ConnectionsManager.RequestFlagWithoutLogin) == 0) {
            return false;
        }
        final String name = request.getClass().getSimpleName();
        return name.startsWith("TL_langpack_") || name.equals("TL_help_getConfig") || name.equals("TL_help_getNearestDc");
    }

    /** Answers a request with invented data when it is one of those that fill the screens. */
    public static void answer(TLObject request, NgramHooks.LocalReply reply) {
        if (request instanceof TLRPC.TL_messages_getDialogs) {
            final TLRPC.TL_messages_getDialogs req = (TLRPC.TL_messages_getDialogs) request;
            final boolean first = req.offset_id == 0 && req.offset_date == 0 && (req.flags & 2) == 0;
            reply.answer(first ? dialogs() : new TLRPC.TL_messages_dialogs());
        } else if (request instanceof TLRPC.TL_messages_getHistory) {
            reply.answer(history((TLRPC.TL_messages_getHistory) request));
        } else if (request instanceof TLRPC.TL_updates_getState) {
            final TLRPC.TL_updates_state state = new TLRPC.TL_updates_state();
            state.pts = pts;
            state.qts = 0;
            state.seq = 1;
            state.date = now();
            reply.answer(state);
        } else if (request instanceof TLRPC.TL_updates_getDifference) {
            final TLRPC.TL_updates_differenceEmpty empty = new TLRPC.TL_updates_differenceEmpty();
            empty.date = now();
            empty.seq = 1;
            reply.answer(empty);
        } else if (request instanceof TLRPC.TL_messages_sendMessage) {
            final TLRPC.TL_updateShortSentMessage sent = new TLRPC.TL_updateShortSentMessage();
            sent.out = true;
            sent.id = ++sentId;
            sent.pts = ++pts;
            sent.pts_count = 1;
            sent.date = now();
            reply.answer(sent);
        }
    }

    // ---- invented data ------------------------------------------------------------------

    private static int now() {
        return (int) (System.currentTimeMillis() / 1000);
    }

    private static TLRPC.User user(long id, String first, String last, String username) {
        final TLRPC.TL_user u = new TLRPC.TL_user();
        u.id = id;
        u.access_hash = id * 31;
        u.first_name = first;
        u.last_name = last;
        u.flags = 1 | 2 | 4;
        if (username != null) {
            u.username = username;
            u.flags |= 8;
        }
        return u;
    }

    private static ArrayList<TLRPC.User> people() {
        final ArrayList<TLRPC.User> list = new ArrayList<>();
        final TLRPC.User me = user(ME, "Максим", "Демо", "ngram_demo");
        me.self = true;
        me.contact = true;
        me.phone = "15550100";
        me.flags |= 16;
        list.add(me);

        final TLRPC.User alice = user(ALICE, "Алиса", "Воронцова", "alice_v");
        alice.contact = true;
        final TLRPC.TL_userStatusOnline online = new TLRPC.TL_userStatusOnline();
        online.expires = now() + 600;
        alice.status = online;
        alice.flags |= 64;
        list.add(alice);

        final TLRPC.User mark = user(MARK, "Марк", "Соколов", null);
        mark.contact = true;
        list.add(mark);
        list.add(user(EVA, "Ева", "Лин", "eva_lin"));
        final TLRPC.User sasha = user(SASHA, "Саша", "Дизайн", "sasha_design");
        sasha.premium = true;
        list.add(sasha);
        return list;
    }

    private static ArrayList<TLRPC.Chat> groups() {
        final ArrayList<TLRPC.Chat> list = new ArrayList<>();
        final TLRPC.TL_chat team = new TLRPC.TL_chat();
        team.id = TEAM;
        team.title = "Команда Ngram";
        team.photo = new TLRPC.TL_chatPhotoEmpty();
        team.participants_count = 4;
        team.date = now() - 86400 * 30;
        team.version = 1;
        team.default_banned_rights = new TLRPC.TL_chatBannedRights();
        team.flags |= 262144;
        list.add(team);

        final TLRPC.TL_channel news = new TLRPC.TL_channel();
        news.id = NEWS;
        news.access_hash = NEWS * 17;
        news.flags = 8192 | 64;
        news.broadcast = true;
        news.title = "Новости Ngram";
        news.username = "ngram_news";
        news.photo = new TLRPC.TL_chatPhotoEmpty();
        news.date = now() - 86400 * 60;
        list.add(news);
        return list;
    }

    /** One line of an invented conversation: who says it (0 = the demo user) and what. */
    private static final class Line {
        final long from;
        final String text;

        Line(long from, String text) {
            this.from = from;
            this.text = text;
        }
    }

    private static Line l(long from, String text) {
        return new Line(from, text);
    }

    private static Line[] script(long dialog) {
        if (dialog == ALICE) {
            return new Line[]{
                l(ALICE, "Привет! Ты уже видел новый дизайн?"),
                l(0, "Да, жидкое стекло выглядит отлично 👌"),
                l(ALICE, "Особенно панель вкладок — под ней видно, как прокручивается чат"),
                l(0, "И кнопки теперь пружинят, как желе"),
                l(ALICE, "Это можно настроить?"),
                l(0, "Да: Настройки → Ngram → Оформление. Там плотность стекла, размытие и преломление."),
                l(ALICE, "Попробую вечером. Пришлёшь скриншот своих настроек?"),
                l(0, "Конечно, чуть позже"),
                l(ALICE, "Спасибо!"),
                l(ALICE, "И ещё вопрос: плагины из exteraGram подойдут?"),
            };
        } else if (dialog == MARK) {
            return new Line[]{
                l(MARK, "Созвон в 15:00 в силе?"),
                l(0, "Да, буду"),
                l(MARK, "Ок, ссылку пришлю за пять минут"),
                l(0, "Договорились 👍"),
            };
        } else if (dialog == EVA) {
            return new Line[]{
                l(0, "Ева, отправил документы на почту"),
                l(EVA, "Получила, спасибо! Посмотрю до завтра."),
                l(EVA, "Там нужна ещё одна подпись на третьей странице"),
            };
        } else if (dialog == SASHA) {
            return new Line[]{
                l(SASHA, "Макеты готовы, смотри в Figma"),
                l(0, "Супер, гляну сегодня"),
                l(0, "Кнопку «Отправить» я бы сделал чуть крупнее"),
            };
        } else if (dialog == ME) {
            return new Line[]{
                l(0, "Список покупок: молоко, хлеб, кофе"),
                l(0, "Идея: добавить в Ngram ассистента прямо в чат"),
            };
        } else if (dialog == -TEAM) {
            return new Line[]{
                l(ALICE, "Коллеги, релиз 3.0 собран"),
                l(MARK, "Отлично! Что внутри?"),
                l(ALICE, "Теперь в основе код официального Telegram — все его функции на месте"),
                l(0, "Плюс наше оформление и настройки стекла"),
                l(SASHA, "Иконку оставили прежнюю?"),
                l(0, "Да, синяя с буквой N"),
                l(MARK, "Проверю звонки сегодня вечером"),
                l(MARK, "И напишу, если что-то найду"),
            };
        } else if (dialog == -NEWS) {
            return new Line[]{
                l(0, "Ngram 3.0: приложение переехало на код официального Telegram для Android. Все функции Telegram доступны сразу."),
                l(0, "В настройках появился раздел Ngram: плотность и размытие стекла, преломление, отклик кнопок."),
                l(0, "Дальше — ассистент и плагины. Следите за обновлениями."),
            };
        }
        return new Line[0];
    }

    private static final long[] ORDER = {ALICE, -TEAM, -NEWS, MARK, EVA, SASHA, ME};

    /** Minutes since the last message of each dialog, in the order above. */
    private static final int[] AGE = {2, 9, 47, 130, 300, 1500, 4300};

    /** Incoming messages not read yet, in the order above. */
    private static final int[] UNREAD = {2, 3, 1, 0, 1, 0, 0};

    private static int baseId(long dialog) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == dialog) {
                return dialog == -NEWS ? 0 : (i + 1) * 1000;
            }
        }
        return 0;
    }

    private static int index(long dialog) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == dialog) {
                return i;
            }
        }
        return -1;
    }

    private static TLRPC.Peer peer(long dialog) {
        if (dialog == -TEAM) {
            final TLRPC.TL_peerChat p = new TLRPC.TL_peerChat();
            p.chat_id = TEAM;
            return p;
        } else if (dialog == -NEWS) {
            final TLRPC.TL_peerChannel p = new TLRPC.TL_peerChannel();
            p.channel_id = NEWS;
            return p;
        }
        final TLRPC.TL_peerUser p = new TLRPC.TL_peerUser();
        p.user_id = dialog;
        return p;
    }

    /** The whole invented conversation of a dialog, oldest first. */
    private static ArrayList<TLRPC.Message> messages(long dialog) {
        final ArrayList<TLRPC.Message> list = new ArrayList<>();
        final int idx = index(dialog);
        if (idx < 0) {
            return list;
        }
        final Line[] lines = script(dialog);
        final int base = baseId(dialog);
        final int last = now() - AGE[idx] * 60;
        final int readInbox = base + lines.length - UNREAD[idx];
        for (int i = 0; i < lines.length; i++) {
            final Line line = lines[i];
            final TLRPC.TL_message m = new TLRPC.TL_message();
            m.id = base + i + 1;
            m.peer_id = peer(dialog);
            m.date = last - (lines.length - 1 - i) * 95;
            m.message = line.text;
            m.dialog_id = dialog;
            if (dialog == -NEWS) {
                m.post = true;
                m.views = 1200 + i * 310;
                m.forwards = 4 + i;
                m.flags |= 1024;
                m.unread = m.id > readInbox;
            } else {
                final boolean mine = line.from == 0;
                final TLRPC.TL_peerUser from = new TLRPC.TL_peerUser();
                from.user_id = mine ? ME : line.from;
                m.from_id = from;
                m.flags |= 256;
                m.out = mine;
                // My own last message to Sasha has not been read on the other side yet.
                m.unread = mine ? (dialog == SASHA && i == lines.length - 1) : m.id > readInbox;
            }
            list.add(m);
        }
        return list;
    }

    private static TLRPC.TL_messages_dialogs dialogs() {
        final TLRPC.TL_messages_dialogs res = new TLRPC.TL_messages_dialogs();
        res.users.addAll(people());
        res.chats.addAll(groups());
        for (int i = 0; i < ORDER.length; i++) {
            final long id = ORDER[i];
            final ArrayList<TLRPC.Message> all = messages(id);
            if (all.isEmpty()) {
                continue;
            }
            final TLRPC.Message top = all.get(all.size() - 1);
            final TLRPC.TL_dialog d = new TLRPC.TL_dialog();
            d.id = id;
            d.peer = peer(id);
            d.top_message = top.id;
            d.unread_count = UNREAD[i];
            d.read_inbox_max_id = top.id - UNREAD[i];
            d.read_outbox_max_id = id == SASHA ? top.id - 1 : top.id;
            d.notify_settings = new TLRPC.TL_peerNotifySettings();
            d.last_message_date = top.date;
            if (id == -NEWS) {
                d.pts = 1;
                d.flags |= 1;
            }
            res.dialogs.add(d);
            res.messages.add(top);
        }
        res.count = res.dialogs.size();
        return res;
    }

    private static long dialogOf(TLRPC.InputPeer peer) {
        if (peer instanceof TLRPC.TL_inputPeerSelf) {
            return ME;
        } else if (peer == null) {
            return 0;
        } else if (peer.channel_id != 0) {
            return -peer.channel_id;
        } else if (peer.chat_id != 0) {
            return -peer.chat_id;
        }
        return peer.user_id;
    }

    private static TLRPC.messages_Messages history(TLRPC.TL_messages_getHistory req) {
        final TLRPC.TL_messages_messages res = new TLRPC.TL_messages_messages();
        final ArrayList<TLRPC.Message> all = messages(dialogOf(req.peer));
        Collections.reverse(all);   // newest first, as the server returns it
        int start = 0;
        if (req.offset_id > 0) {
            while (start < all.size() && all.get(start).id >= req.offset_id) {
                start++;
            }
        }
        start = Math.max(0, start + req.add_offset);
        final int limit = req.limit > 0 ? req.limit : 20;
        for (int i = start; i < all.size() && res.messages.size() < limit; i++) {
            res.messages.add(all.get(i));
        }
        res.users.addAll(people());
        res.chats.addAll(groups());
        res.count = all.size();
        return res;
    }
}
