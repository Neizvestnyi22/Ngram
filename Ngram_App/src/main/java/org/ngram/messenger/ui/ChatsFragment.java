package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;

import android.view.View;

import org.ngram.core.NgramConfig;
import org.ngram.messenger.Opt;
import org.ngram.messenger.features.ChatTools;
import org.ngram.messenger.features.Secure;
import org.ngram.messenger.features.Templates;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/**
 * Ngram's options for chats: how messages are shown, what a double tap does, what the app asks
 * about before it acts, which of Ngram's entries the menu of a message holds.
 */
public class ChatsFragment extends NgramFragment {

    private static final int SECONDS = 1;
    private static final int EXACT = 2;
    private static final int DOUBLE_TAP = 3;
    private static final int PULL_NEXT = 4;
    private static final int HIDE_KEYBOARD = 5;
    private static final int ASK_STICKER = 10;
    private static final int ASK_GIF = 11;
    private static final int ASK_CALL = 12;
    private static final int VOICE_PREVIEW = 13;
    private static final int MENU_SAVE = 20;
    private static final int MENU_REPEAT = 21;
    private static final int MENU_COPY_PHOTO = 22;
    private static final int MENU_STICKER = 23;
    private static final int MENU_DETAILS = 24;
    private static final int MENU_REMIND = 25;
    private static final int TEMPLATES = 30;
    private static final int HIDE_PHONE = 40;
    private static final int SECURE = 41;

    private static final int[] TAPS = {
        NgramConfig.TAP_REACTION, NgramConfig.TAP_REPLY, NgramConfig.TAP_EDIT, NgramConfig.TAP_COPY,
        NgramConfig.TAP_SAVE, NgramConfig.TAP_DETAILS, NgramConfig.TAP_NONE
    };

    @Override
    protected CharSequence getTitle() {
        return tr("Переписка", "Messaging");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(tr("Сообщения", "Messages")));
        items.add(UItem.asCheck(SECONDS, tr("Секунды во времени", "Seconds in the Time")).setChecked(NgramConfig.seconds));
        items.add(UItem.asCheck(EXACT, tr("Точные числа", "Exact Numbers")).setChecked(NgramConfig.exactNumbers));
        items.add(UItem.asButton(DOUBLE_TAP, tr("Двойное нажатие", "Double Tap"), ChatTools.doubleTapName(NgramConfig.doubleTap)));
        items.add(UItem.asShadow(tr(
            "Секунды — время сообщения как 14:05:37. Точные числа — 12 345 просмотров вместо 12,3K. Двойное нажатие на сообщение по умолчанию ставит реакцию; здесь ему можно дать другое дело.",
            "Seconds show a message's time as 14:05:37. Exact numbers show 12 345 views instead of 12.3K. A double tap on a message sets a reaction by default; here it can be given another job.")));

        items.add(UItem.asHeader(tr("Размер стикеров", "Sticker Size")));
        items.add(UItem.asIntSlideView(1, 40, Math.round(NgramConfig.stickerScale * 100), 100, val -> val + "%", val -> {
            NgramConfig.set("stickerScale", val / 100f);
            rebuildOnLeave = true;
        }));
        items.add(UItem.asShadow(tr(
            "100% — как в Telegram. Меньше — стикеры занимают меньше места в переписке.",
            "100% is Telegram's own size. Lower makes stickers take less room in a chat.")));

        items.add(UItem.asHeader(tr("Прокрутка", "Scrolling")));
        items.add(UItem.asCheck(PULL_NEXT, tr("Следующий канал потягиванием", "Pull Up for the Next Channel")).setChecked(NgramConfig.pullNext));
        items.add(UItem.asCheck(HIDE_KEYBOARD, tr("Прятать клавиатуру при прокрутке", "Hide the Keyboard on Scroll")).setChecked(NgramConfig.hideKeyboardOnScroll));
        items.add(UItem.asShadow(tr(
            "В конце канала Telegram предлагает потянуть вверх и перейти к следующему непрочитанному каналу — это можно отключить, чтобы не уходить из канала случайно.",
            "At the end of a channel Telegram offers to pull up and jump to the next unread channel; switch it off to stop leaving a channel by accident.")));

        items.add(UItem.asHeader(tr("Спрашивать перед отправкой", "Ask Before")));
        items.add(UItem.asCheck(ASK_STICKER, tr("Стикера", "Sending a Sticker")).setChecked(NgramConfig.confirmSticker));
        items.add(UItem.asCheck(ASK_GIF, "GIF").setChecked(NgramConfig.confirmGif));
        items.add(UItem.asCheck(ASK_CALL, tr("Звонком", "Starting a Call")).setChecked(NgramConfig.confirmCall));
        items.add(UItem.asCheck(VOICE_PREVIEW, tr("Голосовое: сначала прослушать", "Voice: Listen First")).setChecked(NgramConfig.voicePreview));
        items.add(UItem.asShadow(tr(
            "Защита от случайных нажатий. «Сначала прослушать»: когда вы отпускаете кнопку микрофона, запись не уходит сразу, а остаётся в поле ввода — её можно прослушать, отправить или удалить.",
            "A guard against accidental taps. Listen First: when you let go of the microphone button the recording is not sent at once but stays in the message field, to be played, sent or deleted.")));

        items.add(UItem.asHeader(tr("Меню сообщения", "Message Menu")));
        items.add(UItem.asCheck(MENU_SAVE, tr("В Избранное", "To Saved Messages")).setChecked(Opt.menu(Opt.MENU_SAVE)));
        items.add(UItem.asCheck(MENU_REMIND, tr("Напомнить", "Remind Me")).setChecked(Opt.menu(Opt.MENU_REMIND)));
        items.add(UItem.asCheck(MENU_REPEAT, tr("Повторить", "Repeat")).setChecked(Opt.menu(Opt.MENU_REPEAT)));
        items.add(UItem.asCheck(MENU_COPY_PHOTO, tr("Копировать фото", "Copy Photo")).setChecked(Opt.menu(Opt.MENU_COPY_PHOTO)));
        items.add(UItem.asCheck(MENU_STICKER, tr("Сохранить стикер как картинку", "Save a Sticker as a Picture")).setChecked(Opt.menu(Opt.MENU_STICKER)));
        items.add(UItem.asCheck(MENU_DETAILS, tr("Подробности", "Details")).setChecked(Opt.menu(Opt.MENU_DETAILS)));
        items.add(UItem.asShadow(tr(
            "Пункты, которые Ngram добавляет в меню сообщения. «Повторить» отправляет то же сообщение в этот чат ещё раз от вашего имени. В чатах, где владелец запретил копирование, эти пункты не появляются.",
            "The entries Ngram adds to the menu of a message. Repeat sends the same message into this chat once more as your own. In chats whose owner has switched copying off these entries do not appear.")));

        items.add(UItem.asHeader(tr("Шаблоны", "Templates")));
        final int count = Templates.all().size();
        items.add(UItem.asButton(TEMPLATES, tr("Готовые фразы", "Ready-Made Phrases"), count == 0 ? tr("нет", "none") : String.valueOf(count)));
        items.add(UItem.asShadow(tr(
            "Фразы, которые вставляются в поле ввода в два нажатия: меню чата (три точки) → Ngram → Шаблоны.",
            "Phrases put into the message field in two taps: the chat's menu (three dots) → Ngram → Templates.")));

        items.add(UItem.asHeader(tr("На этом устройстве", "On This Device")));
        items.add(UItem.asCheck(HIDE_PHONE, tr("Прятать мой номер на экране", "Mask My Number on Screen")).setChecked(NgramConfig.hidePhone));
        items.add(UItem.asCheck(SECURE, tr("Запретить снимки экрана", "No Screenshots")).setChecked(NgramConfig.secureScreen));
        items.add(UItem.asShadow(tr(
            "«Скрывать мой номер» заменяет ваш номер точками в настройках и в вашем профиле — на случай, если вы показываете экран. Другим людям ваш номер виден так, как задано в «Конфиденциальности» Telegram. «Запретить снимки экрана» также прячет приложение в списке недавних.",
            "Hide My Number replaces your number with dots in Settings and in your profile, for when you show your screen. What other people see of your number is set under Telegram's Privacy. No Screenshots also hides the app in the list of recent apps.")));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case SECONDS:
                NgramConfig.set("seconds", !NgramConfig.seconds);
                rebuildOnLeave = true;
                break;
            case EXACT:
                NgramConfig.set("exactNumbers", !NgramConfig.exactNumbers);
                rebuildOnLeave = true;
                break;
            case DOUBLE_TAP:
                chooseDoubleTap();
                return;
            case PULL_NEXT:
                NgramConfig.set("pullNext", !NgramConfig.pullNext);
                break;
            case HIDE_KEYBOARD:
                NgramConfig.set("hideKeyboardOnScroll", !NgramConfig.hideKeyboardOnScroll);
                break;
            case ASK_STICKER:
                NgramConfig.set("confirmSticker", !NgramConfig.confirmSticker);
                break;
            case ASK_GIF:
                NgramConfig.set("confirmGif", !NgramConfig.confirmGif);
                break;
            case ASK_CALL:
                NgramConfig.set("confirmCall", !NgramConfig.confirmCall);
                break;
            case VOICE_PREVIEW:
                NgramConfig.set("voicePreview", !NgramConfig.voicePreview);
                break;
            case MENU_SAVE:
                Opt.set(Opt.MENU_SAVE, !Opt.menu(Opt.MENU_SAVE));
                break;
            case MENU_REMIND:
                Opt.set(Opt.MENU_REMIND, !Opt.menu(Opt.MENU_REMIND));
                break;
            case MENU_REPEAT:
                Opt.set(Opt.MENU_REPEAT, !Opt.menu(Opt.MENU_REPEAT));
                break;
            case MENU_COPY_PHOTO:
                Opt.set(Opt.MENU_COPY_PHOTO, !Opt.menu(Opt.MENU_COPY_PHOTO));
                break;
            case MENU_STICKER:
                Opt.set(Opt.MENU_STICKER, !Opt.menu(Opt.MENU_STICKER));
                break;
            case MENU_DETAILS:
                Opt.set(Opt.MENU_DETAILS, !Opt.menu(Opt.MENU_DETAILS));
                break;
            case TEMPLATES:
                presentFragment(new TemplatesFragment());
                return;
            case HIDE_PHONE:
                NgramConfig.set("hidePhone", !NgramConfig.hidePhone);
                rebuildOnLeave = true;
                break;
            case SECURE:
                NgramConfig.set("secureScreen", !NgramConfig.secureScreen);
                Secure.refresh();
                break;
            default:
                return;
        }
        refresh();
    }

    private void chooseDoubleTap() {
        if (getContext() == null) {
            return;
        }
        final CharSequence[] names = new CharSequence[TAPS.length];
        for (int i = 0; i < TAPS.length; i++) {
            names[i] = (TAPS[i] == NgramConfig.doubleTap ? "✓  " : "     ") + ChatTools.doubleTapName(TAPS[i]);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(tr("Двойное нажатие на сообщение", "Double Tap on a Message"));
        builder.setItems(names, (dialog, which) -> {
            if (which >= 0 && which < TAPS.length) {
                NgramConfig.set("doubleTap", TAPS[which]);
                refresh();
            }
        });
        builder.setNegativeButton(LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
        showDialog(builder.create());
    }
}
