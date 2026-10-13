package org.ngram.messenger.features;


import org.json.JSONArray;
import org.json.JSONObject;
import org.ngram.core.NgramConfig;
import org.ngram.messenger.Opt;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The feed: the newest posts of the channels the user follows, in one list ordered by time.
 *
 * Telegram has no request for "posts of all my channels", so the feed asks each channel for a
 * page of its history, a few channels at a time, and merges the pages. It never shows a post
 * out of order: a post is held back while another channel may still have a newer one that has
 * not been loaded.
 *
 * Reading the feed is honest towards the channels: posts shown here are counted as viewed,
 * exactly as they are when a channel is opened (this can be switched off).
 */
public final class Feed {

    /** Channels asked on one refresh, newest activity first. */
    private static final int MAX_SOURCES = 60;
    /** Posts asked from a channel at a time. */
    private static final int PAGE = 12;
    /** Requests in the air at once. */
    private static final int PARALLEL = 5;
    /** A feed older than this is refreshed when it is opened again. */
    public static final long STALE_MS = 3 * 60 * 1000;
    private static final int MAX_SAVED = 200;

    public static final String KEY_MUTED = "feedMuted";         // channels without sound are included
    public static final String KEY_ARCHIVED = "feedArchived";   // archived channels are included
    public static final String KEY_FORWARDS = "feedForwards";   // reposts from other channels are shown
    public static final String KEY_MEDIA = "feedMedia";         // pictures and video previews
    public static final String KEY_COMPACT = "feedCompact";     // a few lines of text per post
    public static final String KEY_VIEWS = "feedViews";         // posts shown count as viewed
    public static final String KEY_FLAT = "feedFlat";           // one list instead of cards
    private static final String KEY_HIDDEN = "feedLeftOut";     // channels left out: ids separated by commas
    private static final String KEY_WORDS = "feedWords";        // posts with these words are left out
    private static final String KEY_SAVED = "feedSaved";        // "read later"

    /** One row of the feed: a post, or the first post of an album standing for all of it. */
    public static final class Post {
        public MessageObject message;
        public TLRPC.Chat channel;
        public long dialogId;
        /** More pictures in the album besides the one shown. */
        public int more;
        public boolean unread;
    }

    /** A post put aside to read later; kept as text so that it is there without the network. */
    public static final class Saved {
        public long channelId;
        public int messageId;
        public String title = "";
        public String text = "";
        public int date;
    }

    private static final class Source {
        TLRPC.Chat chat;
        long dialogId;
        int readMaxId;
        final ArrayList<MessageObject> posts = new ArrayList<>();
        boolean more = true;
        boolean loading;
        boolean failed;
    }

    public interface Listener {
        void onFeedChanged();
    }

    private static final HashMap<Integer, Feed> feeds = new HashMap<>();

    public static Feed of(int account) {
        Feed feed = feeds.get(account);
        if (feed == null) {
            feed = new Feed(account);
            feeds.put(account, feed);
        }
        return feed;
    }

    private final int account;
    private final ArrayList<Source> sources = new ArrayList<>();
    private final ArrayList<Source> waiting = new ArrayList<>();
    private final ArrayList<Post> items = new ArrayList<>();
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private final HashMap<Long, ArrayList<Integer>> viewed = new HashMap<>();
    private final HashSet<Long> viewedOnce = new HashSet<>();
    private int inFlight;
    private int generation;
    private long loadedAt;
    private boolean heldBack;
    private boolean viewsScheduled;

    private Feed(int account) {
        this.account = account;
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void changed() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onFeedChanged();
        }
    }

    public List<Post> items() {
        return items;
    }

    public boolean loading() {
        return inFlight > 0 || !waiting.isEmpty();
    }

    /** There are older posts that a call to {@link #loadMore} would bring. */
    public boolean hasMore() {
        return heldBack;
    }

    public boolean stale() {
        return loadedAt == 0 || System.currentTimeMillis() - loadedAt > STALE_MS;
    }

    public int sourceCount() {
        return sources.size();
    }

    // ---- which channels -----------------------------------------------------------------

    /** Every broadcast channel the user follows, most recently active first. */
    public static ArrayList<TLRPC.Chat> followed(int account) {
        final MessagesController controller = MessagesController.getInstance(account);
        final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>(controller.getAllDialogs());
        Collections.sort(dialogs, (a, b) -> Integer.compare(b.last_message_date, a.last_message_date));
        final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
        for (TLRPC.Dialog dialog : dialogs) {
            if (!DialogObject.isChatDialog(dialog.id)) {
                continue;
            }
            final TLRPC.Chat chat = controller.getChat(-dialog.id);
            if (ChatObject.isChannelAndNotMegaGroup(chat) && !ChatObject.isNotInChat(chat)) {
                channels.add(chat);
            }
        }
        return channels;
    }

    public static Set<String> hidden() {
        final HashSet<String> set = new HashSet<>();
        for (String id : Opt.text(KEY_HIDDEN).split(",")) {
            if (!id.trim().isEmpty()) {
                set.add(id.trim());
            }
        }
        return set;
    }

    public static boolean isHidden(long channelId) {
        return hidden().contains(Long.toString(channelId));
    }

    public static void setHidden(long channelId, boolean hide) {
        final Set<String> set = hidden();
        if (hide) {
            set.add(Long.toString(channelId));
        } else {
            set.remove(Long.toString(channelId));
        }
        Opt.text(KEY_HIDDEN, android.text.TextUtils.join(",", set));
    }

    public static String words() {
        return Opt.text(KEY_WORDS);
    }

    public static void setWords(String words) {
        Opt.text(KEY_WORDS, words == null ? "" : words.trim());
    }

    private static ArrayList<String> wordList() {
        final ArrayList<String> list = new ArrayList<>();
        for (String word : words().toLowerCase(Locale.ROOT).split("[,\\n]")) {
            word = word.trim();
            if (word.length() >= 2) {
                list.add(word);
            }
        }
        return list;
    }

    // ---- loading ------------------------------------------------------------------------

    /** Starts over: the first page of every channel in the feed. */
    public void refresh() {
        generation++;
        sources.clear();
        waiting.clear();
        items.clear();
        inFlight = 0;
        heldBack = false;
        loadedAt = System.currentTimeMillis();

        final MessagesController controller = MessagesController.getInstance(account);
        final boolean muted = Opt.on(KEY_MUTED, true), archived = Opt.on(KEY_ARCHIVED, false);
        final Set<String> hidden = hidden();
        for (TLRPC.Chat chat : followed(account)) {
            if (sources.size() >= MAX_SOURCES) {
                break;
            }
            final long dialogId = -chat.id;
            final TLRPC.Dialog dialog = controller.dialogs_dict.get(dialogId);
            if (hidden.contains(Long.toString(chat.id))
                || !archived && dialog != null && dialog.folder_id == 1
                || !muted && controller.isDialogMuted(dialogId, 0)) {
                continue;
            }
            final Source source = new Source();
            source.chat = chat;
            source.dialogId = dialogId;
            source.readMaxId = dialog == null ? Integer.MAX_VALUE : dialog.read_inbox_max_id;
            sources.add(source);
        }
        waiting.addAll(sources);
        pump();
        changed();
    }

    /** Brings older posts: the next page of the channels that hold the feed back. */
    public void loadMore() {
        if (loading() || !heldBack) {
            return;
        }
        final int cutoff = cutoff();
        for (Source source : sources) {
            if (source.more && !source.failed && !source.posts.isEmpty() && oldestDate(source) >= cutoff) {
                waiting.add(source);
            }
        }
        pump();
        changed();
    }

    private void pump() {
        while (inFlight < PARALLEL && !waiting.isEmpty()) {
            load(waiting.remove(0));
        }
    }

    private void load(Source source) {
        if (source.loading) {
            return;
        }
        source.loading = true;
        inFlight++;
        final int myGeneration = generation;
        final TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = MessagesController.getInstance(account).getInputPeer(source.dialogId);
        req.limit = PAGE;
        req.offset_id = source.posts.isEmpty() ? 0 : source.posts.get(source.posts.size() - 1).getId();
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            final ArrayList<MessageObject> loaded = new ArrayList<>();
            TLRPC.messages_Messages res = null;
            if (response instanceof TLRPC.messages_Messages) {
                res = (TLRPC.messages_Messages) response;
                for (int i = 0; i < res.messages.size(); i++) {
                    final TLRPC.Message message = res.messages.get(i);
                    if (message instanceof TLRPC.TL_messageEmpty || message instanceof TLRPC.TL_messageService) {
                        continue;
                    }
                    if (message.dialog_id == 0) {
                        message.dialog_id = source.dialogId;
                    }
                    try {
                        loaded.add(new MessageObject(account, message, false, false));
                    } catch (Throwable ignore) {
                        // One post the app cannot make sense of must not cost the whole page.
                    }
                }
            }
            final TLRPC.messages_Messages result = res;
            AndroidUtilities.runOnUIThread(() -> {
                if (myGeneration != generation) {
                    return;
                }
                source.loading = false;
                inFlight--;
                if (result == null) {
                    source.failed = true;
                    source.more = false;
                } else {
                    MessagesController.getInstance(account).putUsers(result.users, false);
                    MessagesController.getInstance(account).putChats(result.chats, false);
                    MessagesStorage.getInstance(account).putUsersAndChats(result.users, result.chats, true, true);
                    source.posts.addAll(loaded);
                    source.more = result.messages.size() >= PAGE;
                }
                pump();
                // Rows appear in batches rather than one channel at a time: less jumping about.
                if (inFlight == 0 || inFlight % 2 == 0) {
                    rebuild();
                }
            });
        });
    }

    private static int oldestDate(Source source) {
        return source.posts.get(source.posts.size() - 1).messageOwner.date;
    }

    /** Posts older than this cannot be shown yet: some channel may still have newer ones. */
    private int cutoff() {
        int cutoff = 0;
        for (Source source : sources) {
            if (source.more && !source.failed && !source.posts.isEmpty()) {
                cutoff = Math.max(cutoff, oldestDate(source));
            }
        }
        return cutoff;
    }

    private void rebuild() {
        final int cutoff = loading() ? 0 : cutoff();
        final boolean forwards = Opt.on(KEY_FORWARDS, true);
        final ArrayList<String> words = wordList();
        final ArrayList<Post> list = new ArrayList<>();
        boolean held = false;
        for (Source source : sources) {
            long lastGroup = 0;
            Post album = null;
            for (int i = 0; i < source.posts.size(); i++) {
                final MessageObject message = source.posts.get(i);
                if (message.messageOwner.date < cutoff) {
                    held = true;
                    break;
                }
                final long group = message.getGroupId();
                if (group != 0 && group == lastGroup && album != null) {
                    // Albums come newest picture first; the caption sits on the oldest one.
                    album.more++;
                    if (!hasText(album.message) && hasText(message)) {
                        album.message = message;
                    } else if (album.message.messageOwner.id > message.messageOwner.id && !hasText(album.message)) {
                        album.message = message;
                    }
                    continue;
                }
                if (!forwards && message.messageOwner.fwd_from != null) {
                    continue;
                }
                if (!words.isEmpty() && muted(message, words)) {
                    continue;
                }
                final Post post = new Post();
                post.message = message;
                post.channel = source.chat;
                post.dialogId = source.dialogId;
                post.unread = message.getId() > source.readMaxId;
                list.add(post);
                lastGroup = group;
                album = group != 0 ? post : null;
            }
        }
        Collections.sort(list, (a, b) -> Integer.compare(b.message.messageOwner.date, a.message.messageOwner.date));
        items.clear();
        items.addAll(list);
        heldBack = held;
        if (!loading()) {
            loadedAt = System.currentTimeMillis();
        }
        changed();
    }

    private static boolean hasText(MessageObject message) {
        return message.messageOwner.message != null && !message.messageOwner.message.isEmpty();
    }

    private static boolean muted(MessageObject message, ArrayList<String> words) {
        final String text = message.messageOwner.message;
        if (text == null || text.isEmpty()) {
            return false;
        }
        final String lower = text.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    // ---- views --------------------------------------------------------------------------

    /** A post has come on screen: it is counted as viewed, a few at a time. */
    public void seen(Post post) {
        if (!Opt.on(KEY_VIEWS, true) || NgramConfig.demo || post == null || post.message == null) {
            return;
        }
        final long key = post.dialogId * 100000000L + post.message.getId();
        if (!viewedOnce.add(key)) {
            return;
        }
        ArrayList<Integer> ids = viewed.get(post.dialogId);
        if (ids == null) {
            ids = new ArrayList<>();
            viewed.put(post.dialogId, ids);
        }
        ids.add(post.message.getId());
        if (!viewsScheduled) {
            viewsScheduled = true;
            AndroidUtilities.runOnUIThread(this::flushViews, 2500);
        }
    }

    private void flushViews() {
        viewsScheduled = false;
        for (Long dialogId : new ArrayList<>(viewed.keySet())) {
            final ArrayList<Integer> ids = viewed.remove(dialogId);
            if (ids == null || ids.isEmpty()) {
                continue;
            }
            final TLRPC.TL_messages_getMessagesViews req = new TLRPC.TL_messages_getMessagesViews();
            req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
            req.id = ids;
            req.increment = true;
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            });
        }
    }

    // ---- read later ---------------------------------------------------------------------

    public static ArrayList<Saved> saved() {
        final ArrayList<Saved> list = new ArrayList<>();
        try {
            final String stored = Opt.text(KEY_SAVED);
            final JSONArray array = new JSONArray(stored.isEmpty() ? "[]" : stored);
            for (int i = 0; i < array.length(); i++) {
                final JSONObject o = array.getJSONObject(i);
                final Saved s = new Saved();
                s.channelId = o.optLong("c");
                s.messageId = o.optInt("m");
                s.title = o.optString("t");
                s.text = o.optString("x");
                s.date = o.optInt("d");
                list.add(s);
            }
        } catch (Throwable ignore) {
        }
        return list;
    }

    private static void store(ArrayList<Saved> list) {
        final JSONArray array = new JSONArray();
        try {
            for (Saved s : list) {
                array.put(new JSONObject().put("c", s.channelId).put("m", s.messageId).put("t", s.title).put("x", s.text).put("d", s.date));
            }
        } catch (Throwable ignore) {
        }
        Opt.text(KEY_SAVED, array.toString());
    }

    public static boolean isSaved(long channelId, int messageId) {
        for (Saved s : saved()) {
            if (s.channelId == channelId && s.messageId == messageId) {
                return true;
            }
        }
        return false;
    }

    /** Puts a post aside, or takes it back; returns whether it is put aside now. */
    public static boolean toggleSaved(Post post) {
        final ArrayList<Saved> list = saved();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).channelId == post.channel.id && list.get(i).messageId == post.message.getId()) {
                list.remove(i);
                store(list);
                return false;
            }
        }
        final Saved s = new Saved();
        s.channelId = post.channel.id;
        s.messageId = post.message.getId();
        s.title = post.channel.title == null ? "" : post.channel.title;
        final String text = post.message.messageOwner.message == null ? "" : post.message.messageOwner.message;
        s.text = text.length() > 400 ? text.substring(0, 400) + "…" : text;
        s.date = post.message.messageOwner.date;
        list.add(0, s);
        while (list.size() > MAX_SAVED) {
            list.remove(list.size() - 1);
        }
        store(list);
        return true;
    }

    public static void removeSaved(Saved saved) {
        final ArrayList<Saved> list = saved();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).channelId == saved.channelId && list.get(i).messageId == saved.messageId) {
                list.remove(i);
                break;
            }
        }
        store(list);
    }

    /** The feed of the account on screen, for code that has no account at hand. */
    public static Feed current() {
        return of(UserConfig.selectedAccount);
    }

    /** Text of the newest posts, for the assistant's digest: "Channel: text" paragraphs. */
    public String digestSource(int maxPosts, int maxChars) {
        final StringBuilder sb = new StringBuilder();
        int taken = 0;
        for (Post post : items) {
            final String text = post.message.messageOwner.message;
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            final String cut = text.length() > 600 ? text.substring(0, 600) + "…" : text;
            if (sb.length() + cut.length() > maxChars) {
                break;
            }
            sb.append('[').append(post.channel.title).append("]\n").append(cut.trim()).append("\n\n");
            if (++taken >= maxPosts) {
                break;
            }
        }
        return sb.toString().trim();
    }
}
