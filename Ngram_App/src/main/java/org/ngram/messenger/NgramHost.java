package org.ngram.messenger;

import static org.ngram.messenger.N.tr;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import org.ngram.core.NgramConfig;
import org.ngram.core.NgramHooks;
import org.ngram.messenger.ai.Ai;
import org.ngram.messenger.debug.Demo;
import org.ngram.messenger.features.ChatTools;
import org.ngram.messenger.features.Details;
import org.ngram.messenger.features.Secure;
import org.ngram.messenger.plugins.PluginMenus;
import org.ngram.messenger.plugins.Plugins;
import org.ngram.messenger.ui.AboutFragment;
import org.ngram.messenger.ui.ChatsFragment;
import org.ngram.messenger.ui.DataFragment;
import org.ngram.messenger.ui.LookFragment;
import org.ngram.messenger.ui.PluginsFragment;
import org.ngram.messenger.ui.ai.AiChatFragment;
import org.ngram.messenger.ui.ai.AiInChat;
import org.ngram.messenger.ui.ai.AiSettingsFragment;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.SettingsActivity;

import java.util.ArrayList;

/** Ngram's side of {@link NgramHooks}: what Telegram's code gets when it asks Ngram. */
public final class NgramHost implements NgramHooks.Host {

    private static final int ID_LOOK = NgramHooks.SETTINGS_ID_BASE + 1;
    private static final int ID_AI = NgramHooks.SETTINGS_ID_BASE + 2;
    private static final int ID_CHATS = NgramHooks.SETTINGS_ID_BASE + 3;
    private static final int ID_PLUGINS = NgramHooks.SETTINGS_ID_BASE + 4;
    private static final int ID_DATA = NgramHooks.SETTINGS_ID_BASE + 5;
    private static final int ID_ABOUT = NgramHooks.SETTINGS_ID_BASE + 9;

    @Override
    public void fillSettings(ArrayList<UItem> items) {
        items.add(UItem.asHeader("Ngram"));
        items.add(SettingsActivity.SettingCell.Factory.of(
            ID_LOOK, 0xFF39C0FF, 0xFF2E86FF, R.drawable.ngram_set_look,
            tr("Оформление", "Look"),
            tr("Стекло, оттенок, отклик кнопок", "Glass, tint, button press")));
        items.add(SettingsActivity.SettingCell.Factory.of(
            ID_CHATS, 0xFF5DD66B, 0xFF2BB673, R.drawable.ngram_set_chats,
            tr("Чаты", "Chats"),
            tr("Сообщения, двойное нажатие, шаблоны", "Messages, double tap, templates")));
        final Ai.Provider provider = Ai.active();
        items.add(SettingsActivity.SettingCell.Factory.of(
            ID_AI, 0xFFB659FF, 0xFF617CFF, R.drawable.ngram_set_ai,
            tr("Ассистент", "Assistant"),
            provider == null || !provider.ready()
                ? tr("ИИ с вашим ключом API", "AI with your own API key")
                : provider.title() + " · " + provider.model));
        items.add(SettingsActivity.SettingCell.Factory.of(
            ID_PLUGINS, 0xFFFFB13B, 0xFFFF7A2F, R.drawable.ngram_set_plugins,
            tr("Плагины", "Plugins"),
            Plugins.summary()));
        items.add(SettingsActivity.SettingCell.Factory.of(
            ID_DATA, 0xFF5AC8FA, 0xFF3478F6, R.drawable.ngram_set_data,
            tr("Данные", "Data"),
            tr("Копия настроек, диагностика", "Settings backup, diagnostics")));
        items.add(SettingsActivity.SettingCell.Factory.of(
            ID_ABOUT, 0xFF8E8E93, 0xFF636366, R.drawable.ngram_set_about,
            tr("О приложении", "About"),
            "Ngram " + BuildConfig.VERSION_NAME + " · Telegram " + BuildConfig.TELEGRAM_VERSION));
    }

    @Override
    public BaseFragment openSettings(int id) {
        if (id == ID_LOOK) {
            return new LookFragment();
        } else if (id == ID_CHATS) {
            return new ChatsFragment();
        } else if (id == ID_AI) {
            return Ai.ready() ? new AiChatFragment() : new AiSettingsFragment();
        } else if (id == ID_PLUGINS) {
            return new PluginsFragment();
        } else if (id == ID_DATA) {
            return new DataFragment();
        } else if (id == ID_ABOUT) {
            return new AboutFragment();
        }
        return null;
    }

    @Override
    public void onLaunch(Activity activity, Intent intent) {
        Secure.attach(activity);
        if (BuildConfig.DEBUG) {
            Demo.onLaunch(intent);
        }
        Plugins.onLaunch(activity, intent);
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
        return Plugins.onRequest(account, request);
    }

    @Override
    public void onResponse(int account, TLObject request, TLObject response, TLRPC.TL_error error) {
        Plugins.onResponse(account, request, response, error);
    }

    @Override
    public void onUpdates(int account, TLRPC.Updates updates) {
        Plugins.onUpdates(account, updates);
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
        // The app icons on offer are Ngram's own, under their own names.
        if (resId == org.telegram.messenger.R.string.AppIconDefault) {
            return "Ngram";
        } else if (resId == org.telegram.messenger.R.string.AppIconVintage) {
            return tr("Белый", "White");
        } else if (resId == org.telegram.messenger.R.string.AppIconAqua) {
            return tr("Мята", "Mint");
        } else if (resId == org.telegram.messenger.R.string.AppIconPremium) {
            return tr("Закат", "Sunset");
        } else if (resId == org.telegram.messenger.R.string.AppIconTurbo) {
            return tr("Золото", "Gold");
        } else if (resId == org.telegram.messenger.R.string.AppIconNox) {
            return tr("Графит", "Graphite");
        }
        if (resId == 0 && key != null && (key.equals("AppName") || key.equals("AppNameBeta"))) {
            return "Ngram";
        }
        return null;
    }

    @Override
    public void fillMessageMenu(ChatActivity chat, MessageObject message, ArrayList<Integer> icons, ArrayList<CharSequence> items, ArrayList<Integer> options) {
        AiInChat.fillMessageMenu(chat, message, icons, items, options);
        ChatTools.fillMessageMenu(chat, message, icons, items, options);
        PluginMenus.fillMessageMenu(chat, message, icons, items, options);
    }

    @Override
    public void onMessageMenu(ChatActivity chat, int option, MessageObject message) {
        if (ChatTools.onMessageMenu(chat, option, message) || PluginMenus.onMessageMenu(chat, option, message)) {
            return;
        }
        AiInChat.onMessageMenu(chat, option, message);
    }

    @Override
    public CharSequence chatMenuTitle(ChatActivity chat) {
        return ChatTools.chatMenuTitle(chat);
    }

    @Override
    public void onChatMenu(ChatActivity chat) {
        ChatTools.onChatMenu(chat);
    }

    @Override
    public CharSequence profileMenuTitle(BaseFragment profile) {
        return PluginMenus.hasProfileItems() ? "Ngram" : tr("Сведения", "Details");
    }

    @Override
    public void onProfileMenu(BaseFragment profile, long dialogId, long topicId) {
        if (PluginMenus.hasProfileItems()) {
            PluginMenus.showProfileMenu(profile, dialogId, topicId);
        } else {
            Details.showPeer(profile, dialogId, topicId);
        }
    }

    @Override
    public void onDoubleTap(ChatActivity chat, MessageObject message) {
        ChatTools.onDoubleTap(chat, message);
    }

    @Override
    public void confirm(Context context, int kind, CharSequence subject, Runnable action) {
        ChatTools.confirm(context, kind, subject, action);
    }

    @Override
    public SendMessagesHelper.SendMessageParams onSendMessage(int account, SendMessagesHelper.SendMessageParams params) {
        return Plugins.onSendMessage(account, params);
    }
}
