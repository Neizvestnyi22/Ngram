package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import org.ngram.core.NgramConfig;
import org.ngram.messenger.features.LookPresets;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/**
 * Ngram's look: how clear, blurred, bent and tinted the liquid glass is, ready-made sets of
 * those values, and what buttons do under the finger. Telegram's own appearance settings
 * (themes, wallpapers, message corners) stay where they are, under Chat Settings.
 */
public class LookFragment extends NgramFragment {

    private static final int RESET = 1;
    private static final int APPLY = 2;
    private static final int RESET_TELEGRAM = 3;
    private static final int SAVE_PRESET = 4;
    private static final int MOTION_JELLY = 10;
    private static final int MOTION_GLASS = 11;
    private static final int MOTION_PLAIN = 12;
    private static final int IOS_SWITCH = 20;
    private static final int TAB_TITLES = 21;
    private static final int STORY_BUTTON = 22;
    private static final int SYSTEM_BOLD = 23;
    private static final int STORIES = 24;
    private static final int TITLE = 25;
    private static final int BACK_COUNTER = 30;
    private static final int FIT_TITLE = 31;
    private static final int IOS_INPUT = 32;
    private static final int COLOR_PROFILES = 33;
    private static final int OPEN_GLASS = 100;
    private static final int OPEN_MOTION = 101;
    private static final int OPEN_CHAT = 102;
    private static final int OPEN_MAIN = 103;
    private static final int OPEN_ICONS = 104;
    private static final int PRESET_FIRST = 200;

    /** Which page of the look this screen shows; 0 is the list of pages. */
    private final int page;

    public LookFragment() {
        this(0);
    }

    private LookFragment(int page) {
        this.page = page;
    }

    /** The colours offered for tinting the glass; 0 is "no tint", 1 stands for the theme's accent. */
    private static final int[] COLORS = {
        0, 1, 0xFF0A84FF, 0xFF32ADE6, 0xFF00C7BE, 0xFF34C759, 0xFFFFCC00, 0xFFFF9500, 0xFFFF3B30, 0xFFFF2D55, 0xFFAF52DE, 0xFF5856D6, 0xFF8E8E93
    };

    private final ArrayList<LookPresets.Preset> presets = new ArrayList<>();
    private HorizontalScrollView colorRow;
    private LinearLayout colorList;

    @Override
    protected CharSequence getTitle() {
        switch (page) {
            case OPEN_GLASS:
                return tr("Стекло", "Glass");
            case OPEN_MOTION:
                return tr("Движение и элементы", "Motion and Controls");
            case OPEN_CHAT:
                return tr("Экран чата и профили", "Chat Screen and Profiles");
            case OPEN_MAIN:
                return tr("Главный экран", "Main Screen");
            default:
                return tr("Оформление Ngram", "Ngram Look");
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        switch (page) {
            case OPEN_GLASS:
                fillGlass(items);
                break;
            case OPEN_MOTION:
                fillMotion(items);
                break;
            case OPEN_CHAT:
                fillChat(items);
                break;
            case OPEN_MAIN:
                fillMain(items);
                break;
            default:
                fillPages(items);
                break;
        }
    }

    private void fillPages(ArrayList<UItem> items) {
        items.add(UItem.asButton(OPEN_GLASS, org.telegram.messenger.R.drawable.msg_blur_radial, tr("Стекло", "Glass"),
            Math.round(NgramConfig.glassTint * 100) + "%"));
        items.add(UItem.asButton(OPEN_MOTION, org.telegram.messenger.R.drawable.msg_reactions, tr("Движение и элементы", "Motion and Controls"),
            NgramConfig.pressMotion == NgramConfig.MOTION_JELLY ? tr("желе", "jelly") : NgramConfig.pressMotion == NgramConfig.MOTION_GLASS ? tr("стекло", "glass") : tr("обычный", "plain")));
        items.add(UItem.asButton(OPEN_CHAT, org.telegram.messenger.R.drawable.msg_discussion, tr("Экран чата и профили", "Chat Screen and Profiles"),
            NgramConfig.iosInput ? tr("как в iOS", "iOS style") : tr("как в Telegram", "as in Telegram")));
        items.add(UItem.asButton(OPEN_MAIN, org.telegram.messenger.R.drawable.msg_list, tr("Главный экран", "Main Screen"),
            NgramConfig.mainTitle.isEmpty() ? "Ngram" : NgramConfig.mainTitle));
        items.add(UItem.asButton(OPEN_ICONS, org.telegram.messenger.R.drawable.msg_emoji_activities, tr("Значок приложения", "App Icon")));
        items.add(UItem.asShadow(tr(
            "Здесь то, как выглядит и движется сам Ngram. Темы, обои и скругление сообщений — по-прежнему в настройках чатов Telegram.",
            "This is how Ngram itself looks and moves. Themes, wallpapers and message corners are still under Telegram's Chat Settings.")));
    }

    private void fillChat(ArrayList<UItem> items) {
        items.add(UItem.asHeader(tr("Шапка чата", "Chat Header")));
        items.add(UItem.asCheck(BACK_COUNTER, tr("Число непрочитанных у стрелки «Назад»", "Unread Count Next to the Back Arrow")).setChecked(NgramConfig.backCounter));
        items.add(UItem.asCheck(FIT_TITLE, tr("Заголовок по длине названия", "Title as Wide as the Name")).setChecked(NgramConfig.fitTitle));
        items.add(UItem.asShadow(tr(
            "Число — сколько непрочитанных сообщений ждёт в других чатах, то же, что на вкладке «Чаты». Заголовок: стеклянная капсула с названием чата занимает столько места, сколько нужно тексту, и стоит по центру; на остальных экранах слишком длинный заголовок сначала уменьшается и только потом обрезается.",
            "The count is how many unread messages wait in other chats, the same number as on the Chats tab. The title: the glass capsule with the chat's name takes as much room as the text needs and sits in the middle; on other screens a title that is too long first gets smaller and only then is cut.")));

        items.add(UItem.asHeader(tr("Строка ввода", "Message Field")));
        items.add(UItem.asCheck(IOS_INPUT, tr("Кнопки отдельно от поля", "Buttons Apart from the Field")).setChecked(NgramConfig.iosInput));
        items.add(UItem.asShadow(tr(
            "Скрепка стоит в собственном стеклянном круге слева от поля, кнопка записи и отправки — отдельно справа, как в iOS. Скрепка остаётся на месте, пока вы печатаете.",
            "The paperclip stands in a glass circle of its own to the left of the field, the record and send button apart on the right, as on iOS. The paperclip stays where it is while you type.")));

        items.add(UItem.asHeader(tr("Профили", "Profiles")));
        items.add(UItem.asCheck(COLOR_PROFILES, tr("Цветная шапка у каждого профиля", "A Coloured Header for Every Profile")).setChecked(NgramConfig.colorProfiles));
        items.add(UItem.asShadow(tr(
            "Профиль человека, группы или канала, у которого нет своего цвета, получает шапку в цвет его аватара. Это видите только вы: чужой профиль не меняется.",
            "The profile of a person, group or channel that has no colour of its own gets a header in the colour of its avatar. Only you see this: nobody's profile is changed.")));
    }

    private void fillGlass(ArrayList<UItem> items) {
        presets.clear();
        presets.addAll(LookPresets.builtIn());
        presets.addAll(LookPresets.saved());
        items.add(UItem.asHeader(tr("Наборы стекла", "Glass Sets")));
        for (int i = 0; i < presets.size(); i++) {
            final LookPresets.Preset p = presets.get(i);
            items.add(UItem.asRadio(PRESET_FIRST + i, p.name, p.builtIn ? null : tr("ваш", "yours")).setChecked(p.isCurrent()));
        }
        items.add(UItem.asButton(SAVE_PRESET, org.telegram.messenger.R.drawable.msg_add, tr("Сохранить текущее как набор", "Save the Current Glass as a Set")).accent());
        items.add(UItem.asShadow(tr(
            "Набор задаёт сразу все ползунки ниже. Свой набор удаляется долгим нажатием.",
            "A set moves all the sliders below at once. Press and hold a set of your own to remove it.")));

        items.add(UItem.asHeader(tr("Плотность стекла", "Glass Density")));
        items.add(slider(30, 140, NgramConfig.glassTint, v -> NgramConfig.set("glassTint", v)));
        items.add(UItem.asShadow(tr(
            "Меньше — стекло прозрачнее и сквозь него лучше видно чат. Больше — молочнее. 100% — как в Telegram, в Ngram по умолчанию 80%.",
            "Lower makes glass clearer, so the chat shows through. Higher makes it milkier. 100% is Telegram's own; Ngram starts at 80%.")));

        items.add(UItem.asHeader(tr("Размытие под прозрачным стеклом", "Blur Under Clear Glass")));
        items.add(slider(0, 300, NgramConfig.glassBlur, v -> NgramConfig.set("glassBlur", v)));
        items.add(UItem.asShadow(tr(
            "Панель вкладок и круглые кнопки. 0% — чистая линза без размытия.",
            "The tab bar and the round buttons. 0% is a clean lens with no blur.")));

        items.add(UItem.asHeader(tr("Размытие под матовым стеклом", "Blur Under Frosted Glass")));
        items.add(slider(25, 160, NgramConfig.frostBlur, v -> NgramConfig.set("frostBlur", v)));
        items.add(UItem.asShadow(tr(
            "Заголовки, поле ввода и панели.",
            "Headers, the message field and panels.")));

        items.add(UItem.asHeader(tr("Преломление по краю", "Edge Refraction")));
        items.add(slider(0, 220, NgramConfig.refraction, v -> NgramConfig.set("refraction", v)));
        items.add(UItem.asShadow(tr(
            "Насколько край стекла искажает то, что под ним, — как толстая линза. Работает на Android 13 и новее, если в настройках энергосбережения включено «Жидкое стекло».",
            "How much the edge of the glass bends what is under it, like a thick lens. Needs Android 13 or newer and Liquid Glass switched on under Power Saving.")));

        items.add(UItem.asHeader(tr("Блик по краю", "Edge Highlight")));
        items.add(slider(0, 250, NgramConfig.rim, v -> NgramConfig.set("rim", v)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(tr("Оттенок стекла", "Glass Tint")));
        items.add(UItem.asCustom(colorRow(), 60));
        if (NgramConfig.glassColor != 0) {
            items.add(UItem.asIntSlideView(1, 5, Math.round(NgramConfig.glassColorStrength * 100), 60, val -> val + "%", val -> {
                NgramConfig.set("glassColorStrength", val / 100f);
                rebuildOnLeave = true;
            }));
        }
        items.add(UItem.asShadow(tr(
            "Цвет, который подмешивается во все стеклянные панели. Первый кружок — без оттенка, второй — цвет вашей темы.",
            "A colour mixed into every glass panel. The first circle is no tint, the second is your theme's colour.")));

        items.add(UItem.asButton(APPLY, tr("Применить сейчас", "Apply Now")).accent());
        items.add(UItem.asButton(RESET, tr("Вернуть вид Ngram", "Back to Ngram's Look")));
        items.add(UItem.asButton(RESET_TELEGRAM, tr("Сделать как в Telegram", "Make It Telegram's Own")));
        items.add(UItem.asShadow(tr(
            "Стекло собирается при открытии экрана. Уже открытые экраны обновятся, когда вы выйдете отсюда или нажмёте «Применить сейчас».",
            "Glass is built when a screen opens. Screens already open are refreshed when you leave this one or tap Apply Now.")));

    }

    private void fillMotion(ArrayList<UItem> items) {
        items.add(UItem.asHeader(tr("Отклик кнопок", "Button Press")));
        items.add(UItem.asRadio(MOTION_JELLY, tr("Желе", "Jelly"), tr("сплющивается и дрожит", "squashes and wobbles"))
            .setChecked(NgramConfig.pressMotion == NgramConfig.MOTION_JELLY));
        items.add(UItem.asRadio(MOTION_GLASS, tr("Стекло", "Glass"), tr("набухает, как капля", "swells like a drop"))
            .setChecked(NgramConfig.pressMotion == NgramConfig.MOTION_GLASS));
        items.add(UItem.asRadio(MOTION_PLAIN, tr("Обычный", "Plain"), tr("как в Telegram", "as in Telegram"))
            .setChecked(NgramConfig.pressMotion == NgramConfig.MOTION_PLAIN));
        items.add(UItem.asShadow(tr(
            "Так ведут себя кнопки, плитки, вкладки и стекло под кнопками в шапке чата и у строки ввода. Действует на то, что создано после изменения.",
            "This is how buttons, tiles, tabs and the glass under the buttons of the chat header and message field behave. Applies to what is created after the change.")));

        items.add(UItem.asHeader(tr("Элементы", "Controls")));
        items.add(UItem.asCheck(IOS_SWITCH, tr("Переключатели как в iOS", "iOS-Style Switches")).setChecked(NgramConfig.iosSwitch));
        items.add(UItem.asCheck(SYSTEM_BOLD, tr("Жирный шрифт системы", "System Bold Font")).setChecked(SharedConfig.useSystemBoldFont));
        items.add(UItem.asShadow(tr(
            "Переключатели — широкая зелёная капсула вместо тонкого переключателя Telegram. Шрифт — заголовки и имена рисуются шрифтом телефона, а не встроенным Roboto.",
            "Switches become a wide green capsule instead of Telegram's thin one. The font option draws titles and names with the phone's own font instead of the bundled Roboto.")));

    }

    private void fillMain(ArrayList<UItem> items) {
        items.add(UItem.asHeader(tr("Главный экран", "Main Screen")));
        items.add(UItem.asButton(TITLE, tr("Заголовок", "Title"), NgramConfig.mainTitle.isEmpty() ? "Ngram" : NgramConfig.mainTitle));
        items.add(UItem.asCheck(TAB_TITLES, tr("Подписи на панели вкладок", "Titles on the Tab Bar")).setChecked(NgramConfig.tabTitles));
        items.add(UItem.asCheck(STORIES, tr("Истории над списком чатов", "Stories Above the Chat List")).setChecked(NgramConfig.stories));
        items.add(UItem.asCheck(STORY_BUTTON, tr("Кнопка камеры для историй", "Story Camera Button")).setChecked(NgramConfig.storyButton));
        items.add(UItem.asShadow(tr(
            "Заголовок — текст над списком чатов вместо надписи Ngram. Без подписей на панели остаются одни значки. Без ряда историй список чатов начинается сразу под заголовком; истории по-прежнему открываются из профилей. Кнопка камеры — круглая кнопка над «Новым сообщением».",
            "The title is the text above the chat list instead of the Ngram wordmark. Without titles the tab bar keeps icons only. Without the row of stories the chat list starts right under the header; stories still open from profiles. The camera button is the round one above New Message.")));
    }

    private interface FloatSetter {
        void set(float value);
    }

    /** A percent slider over a setting stored as a factor (1.0 = 100%). */
    private UItem slider(int min, int max, float current, FloatSetter setter) {
        return UItem.asIntSlideView(
            1,
            min, Math.round(current * 100), max,
            val -> val + "%",
            val -> {
                setter.set(val / 100f);
                rebuildOnLeave = true;
            }
        );
    }

    // ---- the row of tint colours --------------------------------------------------------

    private View colorRow() {
        final Context context = getContext();
        if (colorRow == null) {
            colorList = new LinearLayout(context);
            colorList.setOrientation(LinearLayout.HORIZONTAL);
            colorList.setPadding(dp(16), dp(8), dp(16), dp(8));
            for (int color : COLORS) {
                final ColorDot dot = new ColorDot(context, color);
                ScaleStateListAnimator.apply(dot);
                dot.setOnClickListener(v -> {
                    final boolean hadColor = NgramConfig.glassColor != 0;
                    NgramConfig.set("glassColor", dot.value());
                    rebuildOnLeave = true;
                    if (hadColor != (NgramConfig.glassColor != 0)) {
                        // The strength slider comes and goes with the tint.
                        refresh();
                    }
                    for (int i = 0; i < colorList.getChildCount(); i++) {
                        colorList.getChildAt(i).invalidate();
                    }
                });
                colorList.addView(dot, LayoutHelper.createLinear(44, 44, 0, 0, 6, 0));
            }
            colorRow = new HorizontalScrollView(context);
            colorRow.setHorizontalScrollBarEnabled(false);
            colorRow.addView(colorList, new HorizontalScrollView.LayoutParams(HorizontalScrollView.LayoutParams.WRAP_CONTENT, HorizontalScrollView.LayoutParams.MATCH_PARENT));
        }
        return colorRow;
    }

    /** One circle of the tint row. */
    private final class ColorDot extends View {
        private final int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ColorDot(Context context, int color) {
            super(context);
            this.color = color;
        }

        /** The colour this circle sets: the theme's accent is taken at the moment of the tap. */
        int value() {
            return color == 1 ? (Theme.getColor(Theme.key_featuredStickers_addButton, getResourceProvider()) | 0xFF000000) : color;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float cx = getWidth() / 2f, cy = getHeight() / 2f;
            final float radius = Math.min(cx, cy) - dp(5);
            final int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider());
            final boolean selected = color == 0 ? NgramConfig.glassColor == 0 : NgramConfig.glassColor == value();
            if (color == 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.5f));
                paint.setColor(Theme.multAlpha(text, 0.4f));
                canvas.drawCircle(cx, cy, radius - dp(1), paint);
                final float d = (radius - dp(1)) * 0.7071f;
                canvas.drawLine(cx - d, cy + d, cx + d, cy - d, paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(value());
                canvas.drawCircle(cx, cy, radius, paint);
            }
            if (selected) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(color == 0 ? text : value());
                canvas.drawCircle(cx, cy, radius + dp(3.5f), paint);
            }
        }
    }

    // ---- taps ---------------------------------------------------------------------------

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= PRESET_FIRST && item.id < PRESET_FIRST + presets.size()) {
            LookPresets.apply(presets.get(item.id - PRESET_FIRST));
            rebuildOnLeave = true;
            refreshAll();
            return;
        }
        switch (item.id) {
            case SAVE_PRESET:
                Prompt.show(this, tr("Новый набор", "New Set"), tr("Стекло, как оно настроено сейчас, сохранится под этим именем.", "The glass as it is set up now will be saved under this name."),
                    tr("Название", "Name"), "", Prompt.PLAIN, name -> {
                        if (!name.isEmpty()) {
                            LookPresets.saveCurrent(name.length() > 32 ? name.substring(0, 32) : name);
                            refresh();
                        }
                    });
                break;
            case RESET:
                NgramConfig.resetGlass(false);
                rebuildOnLeave = true;
                refreshAll();
                break;
            case RESET_TELEGRAM:
                NgramConfig.resetGlass(true);
                rebuildOnLeave = true;
                refreshAll();
                break;
            case APPLY:
                rebuildScreens(getParentLayout());
                break;
            case MOTION_JELLY:
                setMotion(NgramConfig.MOTION_JELLY);
                break;
            case MOTION_GLASS:
                setMotion(NgramConfig.MOTION_GLASS);
                break;
            case MOTION_PLAIN:
                setMotion(NgramConfig.MOTION_PLAIN);
                break;
            case IOS_SWITCH:
                NgramConfig.set("iosSwitch", !NgramConfig.iosSwitch);
                refresh();
                break;
            case BACK_COUNTER:
                NgramConfig.set("backCounter", !NgramConfig.backCounter);
                refresh();
                break;
            case FIT_TITLE:
                NgramConfig.set("fitTitle", !NgramConfig.fitTitle);
                refresh();
                break;
            case IOS_INPUT:
                NgramConfig.set("iosInput", !NgramConfig.iosInput);
                refresh();
                break;
            case COLOR_PROFILES:
                NgramConfig.set("colorProfiles", !NgramConfig.colorProfiles);
                refresh();
                break;
            case OPEN_GLASS:
            case OPEN_MOTION:
            case OPEN_CHAT:
            case OPEN_MAIN:
                presentFragment(new LookFragment(item.id));
                break;
            case OPEN_ICONS:
                presentFragment(new IconsFragment());
                break;
            case SYSTEM_BOLD:
                SharedConfig.toggleUseSystemBoldFont();
                rebuildOnLeave = true;
                refresh();
                break;
            case TAB_TITLES:
                NgramConfig.set("tabTitles", !NgramConfig.tabTitles);
                rebuildOnLeave = true;
                refresh();
                break;
            case STORIES:
                NgramConfig.set("stories", !NgramConfig.stories);
                rebuildOnLeave = true;
                refresh();
                break;
            case STORY_BUTTON:
                NgramConfig.set("storyButton", !NgramConfig.storyButton);
                rebuildOnLeave = true;
                refresh();
                break;
            case TITLE:
                Prompt.show(this, tr("Заголовок главного экрана", "Main Screen Title"), tr("Оставьте пустым, чтобы вернуть надпись Ngram.", "Leave it empty to bring the Ngram wordmark back."),
                    "Ngram", NgramConfig.mainTitle, Prompt.PLAIN, title -> {
                        NgramConfig.set("mainTitle", title.length() > 24 ? title.substring(0, 24) : title);
                        rebuildOnLeave = true;
                        refresh();
                    });
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < PRESET_FIRST || item.id >= PRESET_FIRST + presets.size() || getContext() == null) {
            return false;
        }
        final LookPresets.Preset preset = presets.get(item.id - PRESET_FIRST);
        if (preset.builtIn) {
            return false;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(preset.name);
        builder.setMessage(tr("Удалить этот набор? ", "Remove this set? ") + preset.summary());
        builder.setPositiveButton(tr("Удалить", "Remove"), (d, w) -> {
            LookPresets.remove(preset.name);
            refresh();
        });
        builder.setNegativeButton(LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
        showDialog(builder.create());
        return true;
    }

    /** After the glass values changed in bulk: the list and the circles of the tint row. */
    private void refreshAll() {
        refresh();
        if (colorList != null) {
            for (int i = 0; i < colorList.getChildCount(); i++) {
                colorList.getChildAt(i).invalidate();
            }
        }
    }

    private void setMotion(int motion) {
        NgramConfig.set("pressMotion", motion);
        rebuildOnLeave = true;
        refresh();
    }
}
