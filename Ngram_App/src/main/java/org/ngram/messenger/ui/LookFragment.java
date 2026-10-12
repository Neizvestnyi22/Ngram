package org.ngram.messenger.ui;

import org.telegram.messenger.SharedConfig;
import static org.ngram.messenger.N.tr;

import android.view.View;

import org.ngram.core.NgramConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/**
 * Ngram's look: how clear, blurred and bent the liquid glass is, and what buttons do under the
 * finger. Telegram's own appearance settings (themes, wallpapers, message corners) stay where
 * they are, under Chat Settings.
 */
public class LookFragment extends NgramFragment {

    private static final int RESET = 1;
    private static final int RESET_TELEGRAM = 3;
    private static final int APPLY = 2;
    private static final int MOTION_JELLY = 10;
    private static final int MOTION_GLASS = 11;
    private static final int MOTION_PLAIN = 12;
    private static final int IOS_SWITCH = 20;
    private static final int TAB_TITLES = 21;
    private static final int STORY_BUTTON = 22;
    private static final int SYSTEM_BOLD = 23;

    /** Glass is built when a screen is, so screens already open are rebuilt once on the way out. */
    private boolean changed;

    @Override
    protected CharSequence getTitle() {
        return tr("Оформление Ngram", "Ngram Look");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
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

        items.add(UItem.asButton(APPLY, tr("Применить сейчас", "Apply Now")).accent());
        items.add(UItem.asButton(RESET, tr("Вернуть вид Ngram", "Back to Ngram's Look")));
        items.add(UItem.asButton(RESET_TELEGRAM, tr("Сделать как в Telegram", "Make It Telegram's Own")));
        items.add(UItem.asShadow(tr(
            "Стекло собирается при открытии экрана. Уже открытые экраны обновятся, когда вы выйдете отсюда или нажмёте «Применить сейчас».",
            "Glass is built when a screen opens. Screens already open are refreshed when you leave this one or tap Apply Now.")));

        items.add(UItem.asHeader(tr("Отклик кнопок", "Button Press")));
        items.add(UItem.asRadio(MOTION_JELLY, tr("Желе", "Jelly"), tr("сплющивается и дрожит", "squashes and wobbles"))
            .setChecked(NgramConfig.pressMotion == NgramConfig.MOTION_JELLY));
        items.add(UItem.asRadio(MOTION_GLASS, tr("Стекло", "Glass"), tr("набухает, как капля", "swells like a drop"))
            .setChecked(NgramConfig.pressMotion == NgramConfig.MOTION_GLASS));
        items.add(UItem.asRadio(MOTION_PLAIN, tr("Обычный", "Plain"), tr("как в Telegram", "as in Telegram"))
            .setChecked(NgramConfig.pressMotion == NgramConfig.MOTION_PLAIN));
        items.add(UItem.asShadow(tr(
            "Действует на кнопки, созданные после изменения.",
            "Applies to buttons created after the change.")));

        items.add(UItem.asHeader(tr("Элементы", "Controls")));
        items.add(UItem.asCheck(IOS_SWITCH, tr("Переключатели как в iOS", "iOS-Style Switches")).setChecked(NgramConfig.iosSwitch));
        items.add(UItem.asCheck(SYSTEM_BOLD, tr("Жирный шрифт системы", "System Bold Font")).setChecked(SharedConfig.useSystemBoldFont));
        items.add(UItem.asShadow(tr(
            "Переключатели — широкая зелёная капсула вместо тонкого переключателя Telegram. Шрифт — заголовки и имена рисуются шрифтом телефона, а не встроенным Roboto.",
            "Switches become a wide green capsule instead of Telegram's thin one. The font option draws titles and names with the phone's own font instead of the bundled Roboto.")));

        items.add(UItem.asHeader(tr("Главный экран", "Main Screen")));
        items.add(UItem.asCheck(TAB_TITLES, tr("Подписи на панели вкладок", "Titles on the Tab Bar")).setChecked(NgramConfig.tabTitles));
        items.add(UItem.asCheck(STORY_BUTTON, tr("Кнопка камеры для историй", "Story Camera Button")).setChecked(NgramConfig.storyButton));
        items.add(UItem.asShadow(tr(
            "Без подписей на панели остаются одни значки. Кнопка камеры — круглая кнопка над «Новым сообщением»; истории можно публиковать и без неё, из профиля.",
            "Without titles the bar keeps icons only. The camera button is the round one above New Message; stories can still be posted without it, from the profile.")));
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
                changed = true;
            }
        );
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case RESET:
                NgramConfig.resetGlass(false);
                changed = true;
                refresh();
                break;
            case RESET_TELEGRAM:
                NgramConfig.resetGlass(true);
                changed = true;
                refresh();
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
            case SYSTEM_BOLD:
                SharedConfig.toggleUseSystemBoldFont();
                changed = true;
                refresh();
                break;
            case TAB_TITLES:
                NgramConfig.set("tabTitles", !NgramConfig.tabTitles);
                changed = true;
                refresh();
                break;
            case STORY_BUTTON:
                NgramConfig.set("storyButton", !NgramConfig.storyButton);
                changed = true;
                refresh();
                break;
        }
    }

    private void setMotion(int motion) {
        NgramConfig.set("pressMotion", motion);
        changed = true;
        listView.adapter.update(true);
    }

    @Override
    public void onFragmentDestroy() {
        final INavigationLayout layout = getParentLayout();
        super.onFragmentDestroy();
        if (changed) {
            // After this screen is gone: the ones under it are built again with the new glass.
            AndroidUtilities.runOnUIThread(() -> rebuildScreens(layout), 250);
        }
    }

    private void rebuildScreens(INavigationLayout layout) {
        changed = false;
        if (layout != null) {
            try {
                layout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
            } catch (Throwable ignore) {
            }
        }
    }
}
