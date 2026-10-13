package org.ngram.messenger;

import static org.ngram.messenger.N.tr;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import org.ngram.core.NgramConfig;
import org.ngram.core.NgramHooks;
import org.ngram.messenger.debug.Demo;
import org.ngram.messenger.features.ChatTools;
import org.ngram.messenger.features.Details;
import org.ngram.messenger.features.Secure;
import org.ngram.messenger.plugins.PluginMenus;
import org.ngram.messenger.plugins.Plugins;
import org.ngram.messenger.ui.FeedFragment;
import org.ngram.messenger.ui.HubFragment;
import org.ngram.messenger.ui.ai.AiInChat;
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

    private static final int ID_HUB = NgramHooks.SETTINGS_ID_BASE + 1;

    @Override
    public void fillSettings(ArrayList<UItem> items) {
        // One row: everything of Ngram's own is behind it, on a screen of its own.
        items.add(SettingsActivity.SettingCell.Factory.of(
            ID_HUB, 0xFF39C0FF, 0xFF7A5CFF, R.drawable.ngram_set_about,
            "Ngram",
            tr("Оформление, лента, ассистент, плагины", "Look, feed, assistant, plugins")));
    }

    @Override
    public BaseFragment openSettings(int id) {
        return id == ID_HUB ? new HubFragment() : null;
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

    // Texts in which the app speaks of itself while asking for a permission. Telegram's own
    // wording is kept, in whatever language it comes, with the app's name put right.
    private static final int[] SELF_NAMED = sorted(
        org.telegram.messenger.R.string.ContactsPermissionAlert, org.telegram.messenger.R.string.NotificationsPermissionAlertSubtitle,
        org.telegram.messenger.R.string.AllowReadCall, org.telegram.messenger.R.string.AllowReadCallAndLog,
        org.telegram.messenger.R.string.AllowReadCallLog, org.telegram.messenger.R.string.AllowFillNumber,
        org.telegram.messenger.R.string.PermissionStorageWithHint, org.telegram.messenger.R.string.PermissionNoAudioWithHint,
        org.telegram.messenger.R.string.PermissionNoBluetoothWithHint, org.telegram.messenger.R.string.PermissionNoAudioVideoWithHint,
        org.telegram.messenger.R.string.PermissionNoCameraWithHint, org.telegram.messenger.R.string.PermissionNoCameraMicVideo,
        org.telegram.messenger.R.string.PermissionNoLocation, org.telegram.messenger.R.string.PermissionNoLocationStory,
        org.telegram.messenger.R.string.PermissionNoLocationFriends, org.telegram.messenger.R.string.PermissionNoLocationNavigation,
        org.telegram.messenger.R.string.PermissionNoContactsSharing, org.telegram.messenger.R.string.PermissionNoContactsSaving,
        org.telegram.messenger.R.string.PermissionNoStorageAvatar, org.telegram.messenger.R.string.PermissionNoAudioStorageStory,
        org.telegram.messenger.R.string.PermissionDrawAboveOtherApps, org.telegram.messenger.R.string.PermissionDrawAboveOtherAppsGroupCall,
        org.telegram.messenger.R.string.PermissionXiaomiLockscreen, org.telegram.messenger.R.string.PermissionFSILockscreen,
        org.telegram.messenger.R.string.PermissionBackgroundLocation, org.telegram.messenger.R.string.AllowBackgroundActivityInfo,
        org.telegram.messenger.R.string.AllowBackgroundActivityInfoOneUIBelowS, org.telegram.messenger.R.string.AllowBackgroundActivityInfoOneUIAboveS);
    private static final ThreadLocal<Boolean> renaming = new ThreadLocal<>();
    private static final android.util.SparseArray<String> renamed = new android.util.SparseArray<>();
    private static String renamedFor = "";

    private static int[] sorted(int... ids) {
        java.util.Arrays.sort(ids);
        return ids;
    }

    /** Telegram's text for {@code resId} in the current language, with the app's name put right. */
    private static String selfNamed(int resId) {
        if (Boolean.TRUE.equals(renaming.get())) {
            // The look-up below has come back here: let Telegram's own text through this once.
            return null;
        }
        final java.util.Locale locale = org.telegram.messenger.LocaleController.getInstance().getCurrentLocale();
        final String language = locale == null ? "" : locale.toString();
        synchronized (renamed) {
            if (!language.equals(renamedFor)) {
                renamed.clear();
                renamedFor = language;
            }
            final String ready = renamed.get(resId);
            if (ready != null) {
                return ready;
            }
        }
        renaming.set(true);
        final String original;
        try {
            original = org.telegram.messenger.LocaleController.getString(resId);
        } finally {
            renaming.set(false);
        }
        if (original == null) {
            return null;
        }
        final String fixed = original.replace("Telegram", "Ngram");
        synchronized (renamed) {
            renamed.put(resId, fixed);
        }
        return fixed;
    }

    @Override
    public String string(int resId, String key) {
        if (resId == org.telegram.messenger.R.string.AppName || resId == org.telegram.messenger.R.string.AppNameBeta) {
            return "Ngram";
        }
        if (resId != 0 && java.util.Arrays.binarySearch(SELF_NAMED, resId) >= 0) {
            return selfNamed(resId);
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

    @Override
    public void onChat(ChatActivity chat, boolean opened) {
        Plugins.onChat(chat, opened);
    }

    @Override
    public boolean onOpenUrl(Context context, android.net.Uri uri) {
        return Plugins.onOpenUrl(context, uri);
    }

    @Override
    public BaseFragment createFeed(android.os.Bundle args) {
        return new FeedFragment(args);
    }

    @Override
    public CharSequence feedTitle() {
        return tr("Лента", "Feed");
    }

    @Override
    public String iconTitle(String key) {
        switch (key) {
            case "NgGlassIcon":
                return tr("Стекло", "Glass");
            case "NgTerminalIcon":
                return tr("Терминал", "Terminal");
            case "NgPixelIcon":
                return "3310";
            case "NgRetroIcon":
                return tr("Ретро 95", "Retro 95");
            case "NgSynthIcon":
                return tr("Синтвейв", "Synthwave");
            case "NgBratIcon":
                return "brat";
            case "NgShadesIcon":
                return "Deal With It";
            case "NgRainbowIcon":
                return tr("Нян", "Nyan");
            case "NgStonksIcon":
                return "Stonks";
            case "NgCatIcon":
                return tr("Кот", "Cat");
            default:
                return null;
        }
    }
}
