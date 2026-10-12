package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;

import android.os.Build;
import android.view.View;

import org.ngram.core.NgramConfig;
import org.ngram.messenger.BuildConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/** What this build is made of, in a form that can be copied into a bug report. */
public class AboutFragment extends NgramFragment {

    private static final int COPY = 1;

    @Override
    protected CharSequence getTitle() {
        return tr("О приложении", "About");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Ngram"));
        items.add(UItem.asButton(0, tr("Версия Ngram", "Ngram version"), BuildConfig.VERSION_NAME));
        items.add(UItem.asButton(0, tr("Основа", "Based on"), "Telegram " + BuildConfig.TELEGRAM_VERSION));
        items.add(UItem.asButton(0, tr("Слой API", "API layer"), String.valueOf(TLRPC.LAYER)));
        items.add(UItem.asButton(0, tr("Устройство", "Device"), Build.MANUFACTURER + " " + Build.MODEL));
        items.add(UItem.asButton(0, "Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));
        if (NgramConfig.demo) {
            items.add(UItem.asButton(0, tr("Режим", "Mode"), tr("демо без аккаунта", "demo, no account")));
        }
        items.add(UItem.asShadow(tr(
            "Ngram построен на исходном коде Telegram для Android (лицензия GPL-2.0) и не является официальным приложением Telegram.",
            "Ngram is built on the source code of Telegram for Android (GPL-2.0 licence) and is not an official Telegram app.")));

        items.add(UItem.asButton(COPY, R.drawable.msg_copy, tr("Скопировать сведения", "Copy Details")).accent());
        items.add(UItem.asShadow(tr(
            "Приложите их, когда сообщаете об ошибке.",
            "Attach them when you report a problem.")));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == COPY) {
            final String report = "Ngram " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                + "\nTelegram " + BuildConfig.TELEGRAM_VERSION + ", layer " + TLRPC.LAYER
                + "\n" + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nABI " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?")
                + "\nGlass " + Math.round(NgramConfig.glassTint * 100) + "/" + Math.round(NgramConfig.glassBlur * 100) + "/"
                + Math.round(NgramConfig.frostBlur * 100) + "/" + Math.round(NgramConfig.refraction * 100) + "/" + Math.round(NgramConfig.rim * 100)
                + ", press " + NgramConfig.pressMotion;
            if (AndroidUtilities.addToClipboard(report)) {
                BulletinFactory.of(this).createCopyBulletin(tr("Сведения скопированы", "Details copied")).show();
            }
        }
    }
}
