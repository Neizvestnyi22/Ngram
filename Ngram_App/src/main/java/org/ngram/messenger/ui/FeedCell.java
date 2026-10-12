package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.ngram.core.NgramConfig;
import org.ngram.messenger.Opt;
import org.ngram.messenger.features.Feed;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * One post in the feed: whose it is and when, its text, a preview of what is attached, and
 * how many have seen it. Drawn with ordinary views; the list recycles them.
 */
public class FeedCell extends LinearLayout {

    private final Theme.ResourcesProvider rp;
    private final int account;
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final BackupImageView avatar;
    private final TextView name, when, from, text, attachment, views;
    private final ImageView viewsIcon, savedIcon;
    private final View unreadDot;
    private final Media media;
    private final TextView moreBadge;
    private final FrameLayout mediaFrame;
    private final LinearLayout footer;

    public FeedCell(Context context, int account, Theme.ResourcesProvider rp) {
        super(context);
        this.rp = rp;
        this.account = account;
        setOrientation(VERTICAL);
        setPadding(dp(14), dp(12), dp(14), dp(12));

        final FrameLayout header = new FrameLayout(context);
        avatar = new BackupImageView(context);
        avatar.setRoundRadius(dp(19));
        header.addView(avatar, LayoutHelper.createFrame(38, 38, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
        name.setTypeface(AndroidUtilities.bold());
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 50, 0, 34, 0));

        when = new TextView(context);
        when.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        when.setSingleLine();
        header.addView(when, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 50, 20, 34, 0));

        unreadDot = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                paint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, FeedCell.this.rp));
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, dp(4), paint);
            }
        };
        header.addView(unreadDot, LayoutHelper.createFrame(12, 12, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 2, 0));

        savedIcon = new ImageView(context);
        savedIcon.setScaleType(ImageView.ScaleType.CENTER);
        savedIcon.setImageResource(R.drawable.msg_saved);
        header.addView(savedIcon, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
        addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40));

        from = new TextView(context);
        from.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        from.setSingleLine();
        from.setEllipsize(TextUtils.TruncateAt.END);
        addView(from, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        text.setLineSpacing(dp(2), 1f);
        text.setEllipsize(TextUtils.TruncateAt.END);
        addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        mediaFrame = new FrameLayout(context);
        media = new Media(context);
        mediaFrame.addView(media, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        moreBadge = new TextView(context);
        moreBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        moreBadge.setTypeface(AndroidUtilities.bold());
        moreBadge.setTextColor(0xFFFFFFFF);
        moreBadge.setPadding(dp(9), dp(3), dp(9), dp(4));
        moreBadge.setBackground(Theme.createRoundRectDrawable(dp(12), 0x99000000));
        mediaFrame.addView(moreBadge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 8, 8, 0));
        addView(mediaFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        attachment = new TextView(context);
        attachment.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        attachment.setSingleLine();
        attachment.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        attachment.setPadding(dp(12), dp(9), dp(12), dp(9));
        addView(attachment, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        footer = new LinearLayout(context);
        footer.setOrientation(HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        viewsIcon = new ImageView(context);
        viewsIcon.setImageResource(R.drawable.msg_views);
        viewsIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        footer.addView(viewsIcon, LayoutHelper.createLinear(16, 16, 0, 0, 5, 0));
        views = new TextView(context);
        views.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        views.setSingleLine();
        footer.addView(views, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        addView(footer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 9, 0, 0));
    }

    private int color(int key) {
        return Theme.getColor(key, rp);
    }

    public void bind(Feed.Post post) {
        final MessageObject m = post.message;
        final TLRPC.Message owner = m.messageOwner;
        final boolean compact = Opt.on(Feed.KEY_COMPACT, false);
        final int gray = color(Theme.key_windowBackgroundWhiteGrayText), black = color(Theme.key_windowBackgroundWhiteBlackText);
        final int accent = color(Theme.key_windowBackgroundWhiteBlueText);

        avatarDrawable.setInfo(account, post.channel);
        avatar.setForUserOrChat(post.channel, avatarDrawable);
        name.setTextColor(black);
        name.setText(post.channel.title);
        when.setTextColor(gray);
        when.setText(LocaleController.stringForMessageListDate(owner.date));
        unreadDot.setVisibility(post.unread ? VISIBLE : GONE);
        final boolean saved = Feed.isSaved(post.channel.id, m.getId());
        savedIcon.setVisibility(saved ? VISIBLE : GONE);
        savedIcon.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));

        String origin = null;
        if (owner.fwd_from != null) {
            origin = owner.fwd_from.from_name;
            if (origin == null && owner.fwd_from.from_id != null) {
                final long id = MessageObject.getPeerId(owner.fwd_from.from_id);
                origin = id < 0
                    ? title(org.telegram.messenger.MessagesController.getInstance(account).getChat(-id))
                    : org.telegram.messenger.UserObject.getUserName(org.telegram.messenger.MessagesController.getInstance(account).getUser(id));
            }
        }
        from.setVisibility(origin == null || origin.isEmpty() ? GONE : VISIBLE);
        from.setTextColor(accent);
        from.setText(origin == null ? "" : tr("Переслано из: ", "Forwarded from ") + origin);

        final String raw = owner.message == null ? "" : owner.message;
        if (raw.isEmpty()) {
            text.setVisibility(GONE);
        } else {
            text.setVisibility(VISIBLE);
            text.setTextColor(black);
            text.setLinkTextColor(accent);
            text.setMaxLines(compact ? 4 : 14);
            final SpannableStringBuilder styled = new SpannableStringBuilder(raw);
            try {
                MessageObject.addEntitiesToText(styled, owner.entities, false, false, false, true);
            } catch (Throwable ignore) {
                // The plain text is still there.
            }
            text.setText(Emoji.replaceEmoji(styled, text.getPaint().getFontMetricsInt(), false));
        }

        // What is attached: a picture or a video frame, or a line that names the attachment.
        final boolean showMedia = Opt.on(Feed.KEY_MEDIA, true);
        final TLRPC.PhotoSize big = m.photoThumbs == null ? null : FileLoader.getClosestPhotoSizeWithSize(m.photoThumbs, 800);
        final boolean visual = big != null && (m.isPhoto() || m.isVideo() || m.isGif()) && !m.isSticker() && !m.isAnimatedSticker();
        if (visual && showMedia) {
            mediaFrame.setVisibility(VISIBLE);
            final TLRPC.PhotoSize small = FileLoader.getClosestPhotoSizeWithSize(m.photoThumbs, 40);
            final boolean allowed = DownloadController.getInstance(account).canDownloadMedia(m) || m.isVideo() || m.isGif();
            media.setRatio(big.w, big.h, compact);
            media.play = m.isVideo() || m.isGif();
            media.getImageReceiver().setImage(
                allowed ? ImageLocation.getForObject(big, m.photoThumbsObject) : null, "720_720",
                small == null || small == big ? null : ImageLocation.getForObject(small, m.photoThumbsObject), "b",
                0, null, m, 0);
            moreBadge.setVisibility(post.more > 0 ? VISIBLE : GONE);
            moreBadge.setText("+" + post.more);
        } else {
            mediaFrame.setVisibility(GONE);
            media.getImageReceiver().clearImage();
        }

        final String label = visual && showMedia ? null : attachmentLabel(m, visual);
        attachment.setVisibility(label == null ? GONE : VISIBLE);
        if (label != null) {
            attachment.setText(label);
            attachment.setTextColor(accent);
            attachment.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.multAlpha(accent, 0.1f)));
        }

        footer.setVisibility(owner.views > 0 ? VISIBLE : GONE);
        views.setTextColor(gray);
        views.setText(NgramConfig.exactNumbers ? NgramConfig.whole(owner.views) : NgramConfig.shortCount(owner.views));
        viewsIcon.setColorFilter(new PorterDuffColorFilter(gray, PorterDuff.Mode.SRC_IN));
    }

    private static String title(TLRPC.Chat chat) {
        return chat == null ? null : chat.title;
    }

    private static String attachmentLabel(MessageObject m, boolean visual) {
        if (visual) {
            return m.isVideo() ? tr("Видео", "Video") : m.isGif() ? "GIF" : tr("Фото", "Photo");
        }
        if (m.isPoll()) {
            return tr("Опрос", "Poll");
        } else if (m.isVoice()) {
            return tr("Голосовое сообщение", "Voice message");
        } else if (m.isRoundVideo()) {
            return tr("Видеосообщение", "Video message");
        } else if (m.isMusic()) {
            return tr("Музыка: ", "Music: ") + m.getMusicTitle();
        } else if (m.isSticker() || m.isAnimatedSticker()) {
            return tr("Стикер", "Sticker");
        } else if (m.getDocument() != null) {
            final String name = m.getDocumentName();
            return tr("Файл", "File") + (name == null || name.isEmpty() ? "" : ": " + name);
        } else if (m.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || m.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
            return tr("Место на карте", "Location");
        } else if (m.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
            return tr("Контакт", "Contact");
        } else if (m.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && m.messageOwner.media.webpage != null
            && m.messageOwner.media.webpage.site_name != null) {
            return tr("Ссылка: ", "Link: ") + m.messageOwner.media.webpage.site_name;
        }
        return null;
    }

    /** The picture of a post: as wide as the card, as tall as its own shape allows. */
    private static final class Media extends BackupImageView {
        private float ratio = 0.6f;
        private boolean compact;
        boolean play;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path triangle = new Path();

        Media(Context context) {
            super(context);
            setRoundRadius(dp(14));
        }

        void setRatio(int w, int h, boolean compact) {
            final float value = w <= 0 || h <= 0 ? 0.6f : h / (float) w;
            if (value != ratio || compact != this.compact) {
                ratio = value;
                this.compact = compact;
                requestLayout();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int height = Math.max(dp(110), Math.min(dp(compact ? 180 : 330), Math.round(width * ratio)));
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!play) {
                return;
            }
            final float cx = getWidth() / 2f, cy = getHeight() / 2f, r = dp(24);
            paint.setColor(0x80000000);
            canvas.drawCircle(cx, cy, r, paint);
            paint.setColor(0xFFFFFFFF);
            triangle.rewind();
            triangle.moveTo(cx - dp(7), cy - dp(10));
            triangle.lineTo(cx + dp(12), cy);
            triangle.lineTo(cx - dp(7), cy + dp(10));
            triangle.close();
            canvas.drawPath(triangle, paint);
        }
    }

    /** Lets Telegram's list kit build, recycle and compare these cells. */
    public static class Factory extends UItem.UItemFactory<FeedCell> {
        static {
            setup(new Factory());
        }

        @Override
        public FeedCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new FeedCell(context, currentAccount, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final Feed.Post post = (Feed.Post) item.object;
            ((FeedCell) view).bind(post);
            // On screen, or about to be: the post counts as viewed, as it does inside its channel.
            Feed.current().seen(post);
        }

        public static UItem of(Feed.Post post) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.object = post;
            // The same post keeps the same id from one refresh to the next.
            item.id = (int) (post.dialogId * 31 + post.message.getId());
            return item;
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            final Feed.Post x = (Feed.Post) a.object, y = (Feed.Post) b.object;
            return x != null && y != null && x.dialogId == y.dialogId && x.message.getId() == y.message.getId();
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            final Feed.Post x = (Feed.Post) a.object, y = (Feed.Post) b.object;
            return x != null && y != null && x.message == y.message && x.more == y.more && x.unread == y.unread;
        }
    }
}
