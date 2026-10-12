package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.ngram.core.NgramMotion;
import org.ngram.messenger.Opt;
import org.ngram.messenger.ai.Ai;
import org.ngram.messenger.features.Feed;
import org.ngram.messenger.ui.ai.AiSettingsFragment;
import org.ngram.messenger.ui.ai.AiSheet;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsActivity;

import java.util.ArrayList;

/**
 * The Feed tab: the newest posts of the user's channels in one list. A tap opens the post in
 * its channel; pressing and holding offers to put it aside, send it to Saved Messages or
 * leave the channel out of the feed.
 */
public class FeedFragment extends NgramFragment implements Feed.Listener, MainTabsActivity.TabFragmentDelegate {

    private static final int MENU_REFRESH = 1, MENU_DIGEST = 2, MENU_SETTINGS = 3;
    private static final int MORE = 10;
    private static final int FILTER_ALL = 0, FILTER_NEW = 1, FILTER_SAVED = 2;

    private final boolean inTabs;
    private Feed feed;
    private int filter = FILTER_ALL;
    private LinearLayout chips;
    private View header;
    private boolean visible;

    public FeedFragment(Bundle args) {
        super();
        inTabs = args != null && args.getBoolean("hasMainTabs", false);
    }

    @Override
    protected CharSequence getTitle() {
        return tr("Лента", "Feed");
    }

    @Override
    public boolean onFragmentCreate() {
        feed = Feed.of(currentAccount);
        feed.addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (feed != null) {
            feed.removeListener(this);
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        super.createView(context);
        if (inTabs) {
            // A tab screen stands inside the pager of the main screen, which has no place for
            // a header of the usual kind: the title and the buttons are the first row of the
            // list instead, and the list makes its own room for the status bar and the tab bar.
            actionBar.setAddToContainer(false);
            listView.setPadding(0, AndroidUtilities.statusBarHeight, 0, AndroidUtilities.navigationBarHeight + dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS));
            listView.setClipToPadding(false);
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(fragmentView, (v, insets) -> {
                final androidx.core.graphics.Insets system = AndroidUtilities.getDefaultWindowInsets(insets, false);
                listView.setPadding(0, system.top, 0, system.bottom + dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS));
                return androidx.core.view.WindowInsetsCompat.CONSUMED;
            });
        }
        final ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_DIGEST, R.drawable.msg_translate).setContentDescription(tr("Пересказ ленты", "Feed digest"));
        menu.addItem(MENU_REFRESH, R.drawable.msg_retry).setContentDescription(tr("Обновить", "Refresh"));
        menu.addItem(MENU_SETTINGS, R.drawable.msg_settings_old).setContentDescription(tr("Настройки ленты", "Feed settings"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else {
                    onMenu(id);
                }
            }
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                final LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (dy > 0 && lm != null && filter != FILTER_SAVED && feed.hasMore() && !feed.loading()
                    && lm.findLastVisibleItemPosition() >= lm.getItemCount() - 5) {
                    feed.loadMore();
                }
            }
        });
        return fragmentView;
    }

    private void onMenu(int id) {
        if (id == MENU_REFRESH) {
            feed.refresh();
            listView.scrollToPosition(0);
        } else if (id == MENU_SETTINGS) {
            presentFragment(new FeedSettingsFragment());
        } else if (id == MENU_DIGEST) {
            digest();
        }
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (!inTabs) {
            super.onInsets(left, top, right, bottom);
        }
    }

    /** The first row of the list on the tab: a large title with the screen's buttons beside it. */
    private View header() {
        if (header == null) {
            final Context context = getContext();
            final android.widget.FrameLayout row = new android.widget.FrameLayout(context);
            final TextView title = new TextView(context);
            title.setText(getTitle());
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()));
            row.addView(title, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 20, 0, 0, 0));
            final int[] ids = {MENU_SETTINGS, MENU_REFRESH, MENU_DIGEST};
            final int[] icons = {R.drawable.msg_settings_old, R.drawable.msg_retry, R.drawable.msg_translate};
            final String[] names = {tr("Настройки ленты", "Feed settings"), tr("Обновить", "Refresh"), tr("Пересказ ленты", "Feed digest")};
            for (int i = 0; i < ids.length; i++) {
                final int id = ids[i];
                final android.widget.ImageView button = new android.widget.ImageView(context);
                button.setScaleType(android.widget.ImageView.ScaleType.CENTER);
                button.setImageResource(icons[i]);
                button.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()), android.graphics.PorterDuff.Mode.SRC_IN));
                button.setBackground(Theme.createRoundRectDrawable(dp(21), Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider())));
                button.setContentDescription(names[i]);
                NgramMotion.apply(button);
                button.setOnClickListener(v -> onMenu(id));
                row.addView(button, LayoutHelper.createFrame(42, 42, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 14 + i * 50, 0));
            }
            header = row;
        }
        return header;
    }

    @Override
    public void onResume() {
        super.onResume();
        visible = true;
        if (feed != null && feed.stale() && !feed.loading()) {
            feed.refresh();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        visible = false;
    }

    @Override
    public void onFeedChanged() {
        refresh();
    }

    @Override
    public void onParentScrollToTop() {
        if (listView != null) {
            listView.smoothScrollToPosition(0);
        }
    }

    // ---- the list -----------------------------------------------------------------------

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (inTabs) {
            items.add(UItem.asCustom(header(), 60));
        }
        items.add(UItem.asCustom(chips(), 54));
        items.add(UItem.asShadow(null));
        if (filter == FILTER_SAVED) {
            fillSaved(items);
            return;
        }
        final boolean flat = Opt.on(Feed.KEY_FLAT, false);
        int shown = 0;
        for (Feed.Post post : feed.items()) {
            if (filter == FILTER_NEW && !post.unread) {
                continue;
            }
            if (shown > 0 && !flat) {
                items.add(UItem.asShadow(null));
            }
            items.add(FeedCell.Factory.of(post));
            shown++;
        }
        if (shown == 0) {
            items.add(UItem.asShadow(emptyText()));
        } else if (feed.loading()) {
            items.add(UItem.asShadow(tr("Загружаю…", "Loading…")));
        } else if (feed.hasMore()) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asButton(MORE, R.drawable.msg_arrow_forward, tr("Показать более старые", "Show Older Posts")).accent());
            items.add(UItem.asShadow(null));
        } else {
            items.add(UItem.asShadow(tr("Это все посты, которые есть в ленте.", "These are all the posts in the feed.")));
        }
    }

    private String emptyText() {
        if (feed.loading()) {
            return tr("Собираю посты ваших каналов…", "Gathering posts from your channels…");
        } else if (feed.sourceCount() == 0) {
            return tr("Здесь появятся посты каналов, на которые вы подписаны. Сейчас в ленте нет ни одного канала: проверьте её настройки или подпишитесь на канал.",
                "Posts of the channels you follow will appear here. There is no channel in the feed now: check its settings or follow a channel.");
        } else if (filter == FILTER_NEW) {
            return tr("Непрочитанных постов нет.", "No unread posts.");
        }
        return tr("Постов пока нет.", "No posts yet.");
    }

    private void fillSaved(ArrayList<UItem> items) {
        final ArrayList<Feed.Saved> saved = Feed.saved();
        if (saved.isEmpty()) {
            items.add(UItem.asShadow(tr(
                "Сюда попадают посты, которые вы отложили: нажмите на пост и удерживайте, затем «Отложить». Список хранится только на этом устройстве.",
                "Posts you put aside land here: press and hold a post, then Read Later. The list is kept on this device only.")));
            return;
        }
        for (int i = 0; i < saved.size(); i++) {
            final Feed.Saved s = saved.get(i);
            final String text = s.text.isEmpty() ? tr("Пост без текста", "A post without text") : s.text.replace('\n', ' ');
            final UItem row = UItem.asButton(1000 + i, s.title, org.telegram.messenger.LocaleController.stringForMessageListDate(s.date));
            row.object = s;
            items.add(row);
            items.add(UItem.asShadow(text.length() > 220 ? text.substring(0, 220) + "…" : text));
        }
    }

    private View chips() {
        if (chips == null) {
            chips = new LinearLayout(getContext());
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setGravity(Gravity.CENTER_VERTICAL);
            chips.setPadding(dp(12), dp(8), dp(12), dp(8));
            final String[] titles = {tr("Все", "All"), tr("Новые", "New"), tr("Отложенное", "Read Later")};
            for (int i = 0; i < titles.length; i++) {
                final int index = i;
                final TextView chip = new TextView(getContext());
                chip.setText(titles[i]);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
                chip.setTypeface(AndroidUtilities.bold());
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(dp(16), 0, dp(16), 0);
                NgramMotion.apply(chip);
                chip.setOnClickListener(v -> {
                    if (filter != index) {
                        filter = index;
                        paintChips();
                        refresh();
                        listView.scrollToPosition(0);
                    }
                });
                chips.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, 0, 0, 8, 0));
            }
        }
        paintChips();
        return chips;
    }

    private void paintChips() {
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton, getResourceProvider());
        final int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider());
        final int card = Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider());
        for (int i = 0; i < chips.getChildCount(); i++) {
            final TextView chip = (TextView) chips.getChildAt(i);
            final boolean on = i == filter;
            chip.setTextColor(on ? 0xFFFFFFFF : text);
            chip.setBackground(Theme.createRoundRectDrawable(dp(18), on ? accent : card));
        }
    }

    // ---- taps ---------------------------------------------------------------------------

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == MORE) {
            feed.loadMore();
        } else if (item.object instanceof Feed.Post) {
            final Feed.Post post = (Feed.Post) item.object;
            open(post.channel.id, post.message.getId());
        } else if (item.object instanceof Feed.Saved) {
            final Feed.Saved s = (Feed.Saved) item.object;
            open(s.channelId, s.messageId);
        }
    }

    private void open(long channelId, int messageId) {
        final Bundle args = new Bundle();
        args.putLong("chat_id", channelId);
        args.putInt("message_id", messageId);
        presentFragment(new ChatActivity(args));
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof Feed.Saved) {
            final Feed.Saved s = (Feed.Saved) item.object;
            ItemOptions.makeOptions(this, view)
                .add(R.drawable.msg_delete, tr("Убрать из отложенного", "Remove from Read Later"), () -> {
                    Feed.removeSaved(s);
                    refresh();
                })
                .show();
            return true;
        }
        if (!(item.object instanceof Feed.Post)) {
            return false;
        }
        final Feed.Post post = (Feed.Post) item.object;
        final boolean saved = Feed.isSaved(post.channel.id, post.message.getId());
        final TLRPC.Chat channel = post.channel;
        final ItemOptions options = ItemOptions.makeOptions(this, view);
        options.add(R.drawable.msg_saved, saved ? tr("Убрать из отложенного", "Remove from Read Later") : tr("Отложить", "Read Later"), () -> {
            final boolean now = Feed.toggleSaved(post);
            refresh();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.saved_messages,
                now ? tr("Пост отложен. Он во вкладке «Отложенное».", "Put aside. Find it under Read Later.") : tr("Убрано из отложенного.", "Removed from Read Later.")).show();
        });
        if (!channel.noforwards && !post.message.messageOwner.noforwards && post.message.canForwardMessage()) {
            options.add(R.drawable.msg_forward, tr("Переслать себе", "Forward to Myself"), () -> {
                final ArrayList<org.telegram.messenger.MessageObject> list = new ArrayList<>();
                list.add(post.message);
                final long self = UserConfig.getInstance(currentAccount).getClientUserId();
                final int failed = getSendMessagesHelper().sendMessage(list, self, false, false, true, 0, 0);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.saved_messages,
                    failed == 0 ? tr("Переслано в «Избранное».", "Forwarded to Saved Messages.") : tr("Не удалось переслать.", "Could not forward.")).show();
            });
        }
        if (ChatObject.isPublic(channel)) {
            options.add(R.drawable.msg_link, tr("Скопировать ссылку", "Copy Link"), () -> {
                AndroidUtilities.addToClipboard("https://t.me/" + ChatObject.getPublicUsername(channel) + "/" + post.message.getId());
                BulletinFactory.of(this).createCopyLinkBulletin().show();
            });
        }
        options.addGap();
        options.add(R.drawable.msg_archive_hide, tr("Убрать канал из ленты", "Leave This Channel Out"), () -> {
            Feed.setHidden(channel.id, true);
            feed.refresh();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_ban,
                tr("Канал убран из ленты. Вернуть его можно в настройках ленты.", "The channel is left out. Bring it back in the feed's settings.")).show();
        });
        options.show();
        return true;
    }

    private void digest() {
        if (!Ai.ready()) {
            presentFragment(new AiSettingsFragment());
            return;
        }
        final String source = feed.digestSource(40, 9000);
        if (source.isEmpty()) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, tr("В ленте пока нет текста для пересказа.", "There is no text in the feed to digest yet.")).show();
            return;
        }
        AiSheet.run(this, tr("Пересказ ленты", "Feed Digest"),
            tr("Ниже — свежие посты из Telegram-каналов, на которые подписан пользователь. Сделай короткий дайджест: 5–9 пунктов, самое важное сверху, "
                    + "у каждого пункта в скобках название канала. Не выдумывай того, чего нет в постах.\n\n",
                "Below are recent posts from the Telegram channels the user follows. Write a short digest: 5 to 9 points, the most important first, "
                    + "each with the channel's name in brackets. Do not invent anything that is not in the posts.\n\n") + source,
            null);
    }
}
