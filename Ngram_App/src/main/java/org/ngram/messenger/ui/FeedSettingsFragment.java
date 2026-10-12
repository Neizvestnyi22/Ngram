package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;

import android.view.View;

import org.ngram.core.NgramConfig;
import org.ngram.messenger.Opt;
import org.ngram.messenger.features.Feed;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/** What goes into the feed, how a post looks there and what reading it does. */
public class FeedSettingsFragment extends NgramFragment {

    private static final int TAB = 1, MUTED = 2, ARCHIVED = 3, FORWARDS = 4, MEDIA = 5, COMPACT = 6, FLAT = 7, VIEWS = 8, WORDS = 9;
    private static final int CHANNEL_FIRST = 1000;

    private final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
    private boolean changed;

    @Override
    protected CharSequence getTitle() {
        return tr("Лента", "Feed");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(tr("Вкладка", "Tab")));
        items.add(UItem.asCheck(TAB, tr("Лента на панели вкладок", "Feed on the Tab Bar")).setChecked(NgramConfig.feedTab));
        items.add(UItem.asShadow(tr(
            "Лента собирает свежие посты каналов, на которые вы подписаны, в один список по времени. Без вкладки она открывается из главного меню.",
            "The feed gathers the newest posts of the channels you follow into one list, ordered by time. Without the tab it opens from the main menu.")));

        items.add(UItem.asHeader(tr("Что попадает в ленту", "What Goes In")));
        items.add(UItem.asCheck(MUTED, tr("Каналы без звука", "Muted Channels")).setChecked(Opt.on(Feed.KEY_MUTED, true)));
        items.add(UItem.asCheck(ARCHIVED, tr("Каналы из архива", "Archived Channels")).setChecked(Opt.on(Feed.KEY_ARCHIVED, false)));
        items.add(UItem.asCheck(FORWARDS, tr("Пересланные посты", "Forwarded Posts")).setChecked(Opt.on(Feed.KEY_FORWARDS, true)));
        final String words = Feed.words();
        items.add(UItem.asButton(WORDS, tr("Скрывать посты со словами", "Hide Posts With Words"), words.isEmpty() ? tr("нет", "none") : shorten(words)));
        items.add(UItem.asShadow(tr(
            "Слова пишутся через запятую. Пост, в тексте которого есть любое из них, в ленту не попадает — в самом канале он остаётся.",
            "Separate the words with commas. A post whose text has any of them stays out of the feed; it is still there in its channel.")));

        items.add(UItem.asHeader(tr("Вид поста", "How a Post Looks")));
        items.add(UItem.asCheck(MEDIA, tr("Картинки и кадры видео", "Pictures and Video Frames")).setChecked(Opt.on(Feed.KEY_MEDIA, true)));
        items.add(UItem.asCheck(COMPACT, tr("Короткие посты", "Short Posts")).setChecked(Opt.on(Feed.KEY_COMPACT, false)));
        items.add(UItem.asCheck(FLAT, tr("Сплошной список", "One Solid List")).setChecked(Opt.on(Feed.KEY_FLAT, false)));
        items.add(UItem.asShadow(tr(
            "Картинки загружаются по тем же правилам, что и в чатах (Данные и память → Автозагрузка медиа). Короткий пост — четыре строки текста и невысокая картинка. В сплошном списке посты идут подряд, без промежутков между карточками.",
            "Pictures load under the same rules as in chats (Data and Storage → Automatic media download). A short post is four lines of text and a low picture. In a solid list the posts follow each other with no gaps between cards.")));

        items.add(UItem.asHeader(tr("Просмотры", "Views")));
        items.add(UItem.asCheck(VIEWS, tr("Засчитывать просмотры", "Count Views")).setChecked(Opt.on(Feed.KEY_VIEWS, true)));
        items.add(UItem.asShadow(tr(
            "Пост, показанный в ленте, считается просмотренным — так же, как при открытии канала. Отметки о прочтении лента не меняет: канал остаётся непрочитанным, пока вы его не откроете.",
            "A post shown in the feed counts as viewed, just as it does when the channel is opened. The feed does not change read marks: a channel stays unread until you open it.")));

        channels.clear();
        channels.addAll(Feed.followed(currentAccount));
        if (!channels.isEmpty()) {
            items.add(UItem.asHeader(tr("Каналы в ленте", "Channels in the Feed")));
            for (int i = 0; i < channels.size(); i++) {
                final TLRPC.Chat chat = channels.get(i);
                items.add(UItem.asCheck(CHANNEL_FIRST + i, chat.title).setChecked(!Feed.isHidden(chat.id)));
            }
            items.add(UItem.asShadow(tr(
                "Снимите отметку, чтобы посты канала не попадали в ленту. На подписку это не влияет.",
                "Clear a mark to keep a channel's posts out of the feed. This does not change what you follow.")));
        }
    }

    private static String shorten(String text) {
        final String flat = text.replace('\n', ' ');
        return flat.length() > 18 ? flat.substring(0, 18) + "…" : flat;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= CHANNEL_FIRST && item.id < CHANNEL_FIRST + channels.size()) {
            final TLRPC.Chat chat = channels.get(item.id - CHANNEL_FIRST);
            Feed.setHidden(chat.id, !Feed.isHidden(chat.id));
            changed = true;
            refresh();
            return;
        }
        switch (item.id) {
            case TAB:
                NgramConfig.set("feedTab", !NgramConfig.feedTab);
                rebuildOnLeave = true;
                break;
            case MUTED:
                flip(Feed.KEY_MUTED, true);
                break;
            case ARCHIVED:
                flip(Feed.KEY_ARCHIVED, false);
                break;
            case FORWARDS:
                flip(Feed.KEY_FORWARDS, true);
                break;
            case MEDIA:
                flip(Feed.KEY_MEDIA, true);
                break;
            case COMPACT:
                flip(Feed.KEY_COMPACT, false);
                break;
            case FLAT:
                flip(Feed.KEY_FLAT, false);
                break;
            case VIEWS:
                Opt.set(Feed.KEY_VIEWS, !Opt.on(Feed.KEY_VIEWS, true));
                break;
            case WORDS:
                Prompt.show(this, tr("Скрывать посты со словами", "Hide Posts With Words"),
                    tr("Через запятую, например: реклама, розыгрыш, ставки", "Comma-separated, for example: ad, giveaway, betting"),
                    tr("Слова", "Words"), Feed.words(), Prompt.MULTILINE, value -> {
                        Feed.setWords(value.length() > 600 ? value.substring(0, 600) : value);
                        changed = true;
                        refresh();
                    });
                return;
            default:
                return;
        }
        refresh();
    }

    private void flip(String key, boolean byDefault) {
        Opt.set(key, !Opt.on(key, byDefault));
        changed = true;
    }

    @Override
    public void onFragmentDestroy() {
        if (changed) {
            // What goes in or how it looks has changed: the feed is put together again.
            Feed.of(currentAccount).refresh();
        }
        super.onFragmentDestroy();
    }
}
