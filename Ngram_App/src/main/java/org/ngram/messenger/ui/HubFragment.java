package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.ngram.core.NgramConfig;
import org.ngram.core.NgramMotion;
import org.ngram.messenger.BuildConfig;
import org.ngram.messenger.R;
import org.ngram.messenger.ai.Ai;
import org.ngram.messenger.features.Feed;
import org.ngram.messenger.plugins.Plugins;
import org.ngram.messenger.ui.ai.AiChatFragment;
import org.ngram.messenger.ui.ai.AiSettingsFragment;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/**
 * Ngram's own corner of the settings: one screen with a tile for each part of it. Telegram's
 * settings list carries a single row that leads here, so the two do not get mixed up.
 */
public class HubFragment extends NgramFragment {

    private static final String REPO = "https://github.com/Neizvestnyi22/Ngram";
    private static final int ABOUT = 1, ICONS = 2, NEWS = 3;

    private interface Opener {
        BaseFragment open();
    }

    private static final class Tile {
        final int icon, from, to;
        final String title;
        final Opener opener;
        TextView status;

        Tile(int icon, int from, int to, String title, Opener opener) {
            this.icon = icon;
            this.from = from;
            this.to = to;
            this.title = title;
            this.opener = opener;
        }
    }

    private final ArrayList<Tile> tiles = new ArrayList<>();
    private View hero;
    private LinearLayout grid;

    @Override
    protected CharSequence getTitle() {
        return "Ngram";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(hero(), 176));
        items.add(UItem.asCustom(grid(), 3 * 124 + 4));
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ICONS, org.telegram.messenger.R.drawable.msg_emoji_activities, tr("Значок приложения", "App Icon")));
        items.add(UItem.asButton(NEWS, org.telegram.messenger.R.drawable.msg_log, tr("Что нового", "What's New")));
        items.add(UItem.asButton(ABOUT, org.telegram.messenger.R.drawable.msg_info, tr("О приложении", "About"), BuildConfig.VERSION_NAME));
        items.add(UItem.asShadow(tr(
            "Здесь только то, чего нет в самом Telegram. Его собственные настройки — темы, уведомления, конфиденциальность — остались на своих местах.",
            "Only what Telegram itself does not have lives here. Its own settings — themes, notifications, privacy — are where they have always been.")));
        updateStatuses();
    }

    // ---- the header card ----------------------------------------------------------------

    private View hero() {
        if (hero != null) {
            return hero;
        }
        final Context context = getContext();
        final FrameLayout card = new FrameLayout(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();
            private final Path wave = new Path();

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                paint.setShader(new LinearGradient(0, 0, w, h, new int[]{0xFF39C0FF, 0xFF2E86FF, 0xFF7A5CFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                final float l = dp(12), t = dp(8), r = getWidth() - dp(12), b = getHeight() - dp(8), radius = dp(26);
                rect.set(l, t, r, b);
                canvas.drawRoundRect(rect, radius, radius, paint);
                // A light sweep across the top, as on the app icon.
                canvas.save();
                wave.rewind();
                wave.addRoundRect(rect, radius, radius, Path.Direction.CW);
                canvas.clipPath(wave);
                wave.rewind();
                wave.moveTo(l, t + (b - t) * 0.12f);
                wave.cubicTo(l + (r - l) * 0.35f, t + (b - t) * 0.42f, l + (r - l) * 0.7f, t - (b - t) * 0.05f, r, t + (b - t) * 0.46f);
                wave.lineTo(r, t);
                wave.lineTo(l, t);
                wave.close();
                light.setColor(0x26FFFFFF);
                canvas.drawPath(wave, light);
                canvas.restore();
                super.dispatchDraw(canvas);
            }
        };
        card.setWillNotDraw(false);

        final View logo = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path n = new Path();

            @Override
            protected void onDraw(Canvas canvas) {
                final float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = Math.min(cx, cy) - dp(1.5f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x38FFFFFF);
                canvas.drawCircle(cx, cy, radius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.3f));
                paint.setColor(0xA6FFFFFF);
                canvas.drawCircle(cx, cy, radius, paint);
                // The N of the icon: (40,70) (40,39) (68,69) (68,38) on a 108 grid.
                final float s = getWidth() / 62f;
                n.rewind();
                n.moveTo(cx - 14 * s, cy + 16 * s);
                n.lineTo(cx - 14 * s, cy - 15 * s);
                n.lineTo(cx + 14 * s, cy + 15 * s);
                n.lineTo(cx + 14 * s, cy - 16 * s);
                paint.setStrokeWidth(8.5f * s);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(0xFFFFFFFF);
                canvas.drawPath(n, paint);
            }
        };
        card.addView(logo, LayoutHelper.createFrame(64, 64, Gravity.LEFT | Gravity.TOP, 32, 30, 0, 0));

        final TextView name = new TextView(context);
        name.setText("Ngram");
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(0xFFFFFFFF);
        card.addView(name, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 112, 32, 24, 0));

        final TextView version = new TextView(context);
        version.setText(BuildConfig.VERSION_NAME + " · Telegram " + BuildConfig.TELEGRAM_VERSION);
        version.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        version.setTextColor(0xD9FFFFFF);
        card.addView(version, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 113, 70, 24, 0));

        final LinearLayout chipRow = new LinearLayout(context);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.addView(chip(context, tr("Исходный код", "Source Code"), () -> Browser.openUrl(context, REPO)), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 0, 0, 8, 0));
        chipRow.addView(chip(context, tr("Сообщить об ошибке", "Report a Problem"), () -> Browser.openUrl(context, REPO + "/issues")), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32));
        card.addView(chipRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.LEFT | Gravity.BOTTOM, 32, 0, 24, 26));

        return hero = card;
    }

    private static View chip(Context context, String text, Runnable action) {
        final TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        chip.setTypeface(AndroidUtilities.bold());
        chip.setTextColor(0xFFFFFFFF);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(13), 0, dp(13), 0);
        chip.setBackground(Theme.createRoundRectDrawable(dp(16), 0x33FFFFFF));
        NgramMotion.apply(chip);
        chip.setOnClickListener(v -> action.run());
        return chip;
    }

    // ---- the tiles ----------------------------------------------------------------------

    private View grid() {
        if (grid != null) {
            return grid;
        }
        final Context context = getContext();
        tiles.clear();
        tiles.add(new Tile(R.drawable.ngram_set_look, 0xFF39C0FF, 0xFF2E86FF, tr("Оформление", "Look"), LookFragment::new));
        tiles.add(new Tile(R.drawable.ngram_set_chats, 0xFF5DD66B, 0xFF2BB673, tr("Переписка", "Messaging"), ChatsFragment::new));
        tiles.add(new Tile(R.drawable.ngram_set_feed, 0xFFFF9F5A, 0xFFFF5E7E, tr("Лента", "Feed"), FeedSettingsFragment::new));
        tiles.add(new Tile(R.drawable.ngram_set_ai, 0xFFB659FF, 0xFF617CFF, tr("Ассистент", "Assistant"), () -> Ai.ready() ? new AiChatFragment() : new AiSettingsFragment()));
        tiles.add(new Tile(R.drawable.ngram_set_plugins, 0xFFFFB13B, 0xFFFF7A2F, tr("Плагины", "Plugins"), PluginsFragment::new));
        tiles.add(new Tile(R.drawable.ngram_set_data, 0xFF5AC8FA, 0xFF3478F6, tr("Копия и отчёт", "Backup & Report"), DataFragment::new));

        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(8), 0, dp(8), dp(4));
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 124));
            }
            row.addView(tileView(context, tiles.get(i)), LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, 4, 4, 4, 4));
        }
        return grid;
    }

    private View tileView(Context context, Tile tile) {
        final int card = Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider());
        final int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider());
        final int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, getResourceProvider());

        final FrameLayout view = new FrameLayout(context);
        view.setBackground(Theme.createRoundRectDrawable(dp(22), card));

        final ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImageResource(tile.icon);
        final GradientDrawable badge = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{tile.from, tile.to});
        badge.setCornerRadius(dp(12));
        icon.setBackground(badge);
        view.addView(icon, LayoutHelper.createFrame(40, 40, Gravity.LEFT | Gravity.TOP, 14, 14, 0, 0));

        final TextView title = new TextView(context);
        title.setText(tile.title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        view.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 14, 62, 10, 0));

        tile.status = new TextView(context);
        tile.status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tile.status.setTextColor(gray);
        tile.status.setSingleLine();
        tile.status.setEllipsize(TextUtils.TruncateAt.END);
        view.addView(tile.status, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 14, 85, 10, 0));

        NgramMotion.apply(view);
        view.setOnClickListener(v -> presentFragment(tile.opener.open()));
        return view;
    }

    /** The grey line under each tile's name says how that part is set up right now. */
    private void updateStatuses() {
        if (tiles.size() < 6 || tiles.get(0).status == null) {
            return;
        }
        tiles.get(0).status.setText(tr("Стекло ", "Glass ") + Math.round(NgramConfig.glassTint * 100) + "%" + (NgramConfig.iosInput ? tr(" · как в iOS", " · iOS style") : ""));
        tiles.get(1).status.setText(tr("Меню, шаблоны, вопросы", "Menus, templates, prompts"));
        final int channels = NgramConfig.demo || getUserConfig().isClientActivated() ? Feed.followed(currentAccount).size() : 0;
        tiles.get(2).status.setText(!NgramConfig.feedTab ? tr("вкладка выключена", "tab is off") : tr("Каналов: ", "Channels: ") + channels);
        final Ai.Provider provider = Ai.active();
        tiles.get(3).status.setText(provider == null || !provider.ready() ? tr("не настроен", "not set up") : provider.model);
        tiles.get(4).status.setText(Plugins.summary());
        tiles.get(5).status.setText(tr("Настройки в файл, отчёт", "Settings file, report"));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ABOUT) {
            presentFragment(new AboutFragment());
        } else if (item.id == ICONS) {
            presentFragment(new IconsFragment());
        } else if (item.id == NEWS && getContext() != null) {
            Browser.openUrl(getContext(), REPO + "/blob/ngram/CHANGELOG.md");
        }
    }
}
