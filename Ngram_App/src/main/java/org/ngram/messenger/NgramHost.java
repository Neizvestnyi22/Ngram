package org.ngram.messenger;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.ChatActivity;
import org.ngram.messenger.ui.ai.AiInChat;
import org.ngram.messenger.ui.ai.AiSettingsFragment;
import org.ngram.messenger.ui.ai.AiChatFragment;
import org.ngram.messenger.ai.Ai;
import static org.ngram.messenger.N.tr;

import android.app.Activity;
import android.content.Intent;

import org.ngram.core.NgramConfig;
import org.ngram.core.NgramHooks;
import org.ngram.messenger.debug.Demo;
import org.ngram.messenger.ui.AboutFragment;
import org.ngram.messenger.ui.LookFragment;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.SettingsActivity;

import java.util.ArrayList;

/** Ngram's side of {@link NgramHooks}: what Telegram's code gets when it asks Ngram. */
public final class NgramHost implements NgramHooks.Host {

    private static final int ID_LOOK = NgramHooks.SETTINGS_ID_BASE + 1;
    private static final int ID_AI = NgramHooks.SETTINGS_ID_BASE + 2;
    private static final int ID_ABOUT = NgramHooks.SETTINGS_ID_BASE + 9;

    @Override
    public void fillSettings(ArrayList<UItem> items) {
        items.add(UItem.asHeader("Ngram"));
        items.add(SettingsActivity.SettingCell.Factory.of(
            ID_LOOK, 0xFF39C0FF, 0xFF2E86FF, R.drawable.ngram_set_look,
            tr("Оформление", "Look"),
            tr("Стекло, размытие, отклик кнопок", "Glass, blur, button press")));
        final Ai.Provider provider = Ai.active();
        items.add(SettingsActivity.SettingCell.Factory.of(
            ID_AI, 0xFFB659FF, 0xFF617CFF, R.drawable.ngram_set_ai,
            tr("Ассистент", "Assistant"),
            provider == null || !provider.ready()
                ? tr("ИИ с вашим ключом API", "AI with your own API key")
                : provider.title() + " · " + provider.model));
        items.add(SettingsActivity.SettingCell.Factory.of(
            ID_ABOUT, 0xFF8E8E93, 0xFF636366, R.drawable.ngram_set_about,
            tr("О приложении", "About"),
            "Ngram " + BuildConfig.VERSION_NAME + " · Telegram " + BuildConfig.TELEGRAM_VERSION));
    }

    @Override
    public BaseFragment openSettings(int id) {
        if (id == ID_LOOK) {
            return new LookFragment();
        } else if (id == ID_AI) {
            return Ai.ready() ? new AiChatFragment() : new AiSettingsFragment();
        } else if (id == ID_ABOUT) {
            return new AboutFragment();
        }
        return null;
    }

    @Override
    public void onLaunch(Activity activity, Intent intent) {
        if (BuildConfig.DEBUG) {
            Demo.onLaunch(intent);
        }
    }

    @Override
    public boolean onIntent(Activity activity, Intent intent) {
        return false;
    }

    @Override
    public TLObject onRequest(int account, TLObject request, int flags, NgramHooks.LocalReply reply) {
        // The made-up account of the debug demo must never talk to Telegram: the server would
        // answer "not signed in" and the app would log the demo out.
        if (NgramConfig.demo) {
            if (BuildConfig.DEBUG) {
                if (Demo.harmless(request, flags)) {
                    return request;
                }
                Demo.answer(request, reply);
            }
            return null;
        }
        return request;
    }

    @Override
    public void onResponse(int account, TLObject request, TLObject response, TLRPC.TL_error error) {
    }

    @Override
    public void onUpdates(int account, TLRPC.Updates updates) {
    }

    @Override
    public String string(int resId, String key) {
        if (resId == org.telegram.messenger.R.string.AppName || resId == org.telegram.messenger.R.string.AppNameBeta) {
            return "Ngram";
        }
        if (resId == org.telegram.messenger.R.string.Page1Message) {
            return tr("Клиент Telegram в жидком стекле.\n**Быстрый**, **бесплатный** и **безопасный**.",
                "A Telegram client in liquid glass.\n**Fast**, **free** and **secure**.");
        }
        if (resId == 0 && key != null && (key.equals("AppName") || key.equals("AppNameBeta"))) {
            return "Ngram";
        }
        return null;
    }

    @Override
    public void fillMessageMenu(ChatActivity chat, MessageObject message, ArrayList<Integer> icons, ArrayList<CharSequence> items, ArrayList<Integer> options) {
        AiInChat.fillMessageMenu(message, icons, items, options);
    }

    @Override
    public void onMessageMenu(ChatActivity chat, int option, MessageObject message) {
        AiInChat.onMessageMenu(chat, option, message);
    }

    @Override
    public CharSequence chatMenuTitle(ChatActivity chat) {
        return AiInChat.chatMenuTitle();
    }

    @Override
    public void onChatMenu(ChatActivity chat) {
        AiInChat.onChatMenu(chat);
    }
}
