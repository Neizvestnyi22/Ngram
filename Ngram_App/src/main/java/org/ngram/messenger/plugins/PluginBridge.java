package org.ngram.messenger.plugins;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.text.Html;
import android.widget.Toast;

import org.ngram.core.NgramConfig;
import org.ngram.messenger.BuildConfig;
import org.ngram.messenger.ui.Prompt;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

/**
 * What the Python half of the plugin engine calls: every method here is used by name from
 * plugin_host.py and the SDK modules next to it. Anything that touches the screen hops to the
 * main thread by itself, so a plugin may call it from anywhere.
 */
public final class PluginBridge {

    private PluginBridge() {
    }

    private static void ui(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                r.run();
            } catch (Throwable e) {
                Plugins.log("", "E", String.valueOf(e));
            }
        } else {
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    r.run();
                } catch (Throwable e) {
                    Plugins.log("", "E", String.valueOf(e));
                }
            });
        }
    }

    // ---- the engine ---------------------------------------------------------------------

    public static void log(String pluginId, String level, String message) {
        Plugins.log(pluginId, level, message);
    }

    public static void setHooks(String[] exact, String[] partial, boolean send) {
        Plugins.setHooks(exact, partial, send);
    }

    public static void setEvents(String[] names) {
        Plugins.setEvents(names);
    }

    /** The language the app is shown in: "ru", "en"... */
    public static String language() {
        try {
            final java.util.Locale locale = LocaleController.getInstance().getCurrentLocale();
            return locale == null ? "en" : locale.getLanguage();
        } catch (Throwable e) {
            return "en";
        }
    }

    /** The account on screen. */
    public static int account() {
        return org.telegram.messenger.UserConfig.selectedAccount;
    }

    public static void pluginFailed(String pluginId, String reason) {
        ui(() -> Plugins.failed(pluginId, reason));
    }

    public static void reloadSettings(String pluginId) {
        final Runnable r = Plugins.onChanged;
        if (r != null) {
            AndroidUtilities.runOnUIThread(r);
        }
    }

    public static void registerMenuItem(String pluginId, String itemId, String type, String text, String subtext, String icon, int priority, boolean conditional) {
        PluginMenus.register(pluginId, itemId, type, text, subtext, icon, priority, conditional);
    }

    public static void removeMenuItem(String itemId) {
        PluginMenus.remove(itemId);
    }

    public static boolean isUiThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    public static void runOnUi(int callback, int delayMs) {
        AndroidUtilities.runOnUIThread(() -> Plugins.runCallback(callback), Math.max(0, delayMs));
    }

    public static String pluginsDir() {
        return Plugins.dir().getAbsolutePath();
    }

    public static String cacheDir() {
        return ApplicationLoader.applicationContext.getCacheDir().getAbsolutePath();
    }

    public static String appVersion() {
        return BuildConfig.VERSION_NAME;
    }

    public static boolean isDemo() {
        return NgramConfig.demo;
    }

    public static Activity activity() {
        return Plugins.activity();
    }

    // ---- what is on screen --------------------------------------------------------------

    public static BaseFragment lastFragment() {
        try {
            return LaunchActivity.getSafeLastFragment();
        } catch (Throwable e) {
            return null;
        }
    }

    public static long currentChatId() {
        final BaseFragment fragment = lastFragment();
        return fragment instanceof ChatActivity ? ((ChatActivity) fragment).getDialogId() : 0;
    }

    public static void openChat(long dialogId) {
        ui(() -> {
            final BaseFragment fragment = lastFragment();
            if (fragment == null || dialogId == 0) {
                return;
            }
            final Bundle args = new Bundle();
            if (dialogId > 0) {
                args.putLong("user_id", dialogId);
            } else {
                args.putLong("chat_id", -dialogId);
            }
            if (MessagesController.getInstance(fragment.getCurrentAccount()).checkCanOpenChat(args, fragment)) {
                fragment.presentFragment(new ChatActivity(args));
            }
        });
    }

    public static void openUrl(String url) {
        ui(() -> {
            final Activity activity = activity();
            if (activity != null) {
                Browser.openUrl(activity, url);
            }
        });
    }

    // ---- notices and questions ----------------------------------------------------------

    public static void toast(String text) {
        ui(() -> {
            final BaseFragment fragment = lastFragment();
            if (fragment != null && fragment.getContext() != null) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, text).show();
            } else {
                Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** A note at the bottom of the screen of the given kind (info, ok, error), with an optional button. */
    public static void notice(String text, String kind, String button, int callback) {
        ui(() -> {
            final BaseFragment fragment = lastFragment();
            if (fragment == null || fragment.getContext() == null) {
                Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show();
                return;
            }
            final int icon = "error".equals(kind) ? R.raw.error : "ok".equals(kind) ? R.raw.done : R.raw.info;
            if (!empty(button) && callback != 0) {
                BulletinFactory.of(fragment).createSimpleBulletin(icon, text, button, () -> Plugins.runCallback(callback)).show();
            } else {
                BulletinFactory.of(fragment).createSimpleBulletin(icon, text).show();
            }
        });
    }

    /** A sheet of labelled values; a tap on a value copies it. */
    public static void sheet(String title, String[] labels, String[] values) {
        ui(() -> {
            final BaseFragment fragment = lastFragment();
            if (fragment == null || fragment.getContext() == null || labels == null || values == null) {
                return;
            }
            final org.ngram.messenger.ui.InfoSheet.Rows rows = new org.ngram.messenger.ui.InfoSheet.Rows();
            for (int i = 0; i < labels.length && i < values.length; i++) {
                rows.add(labels[i], values[i]);
            }
            org.ngram.messenger.ui.InfoSheet.show(fragment, title, rows);
        });
    }

    public static void alert(String title, String message, String positive, String negative, String neutral, int callback) {
        alert(title, message, positive, negative, neutral, callback, false);
    }

    /** The callback gets -1, -2 or -3 for the positive, negative and neutral button. */
    public static void alert(String title, String message, String positive, String negative, String neutral, int callback, boolean red) {
        ui(() -> {
            final BaseFragment fragment = lastFragment();
            final Context context = fragment != null && fragment.getContext() != null ? fragment.getContext() : activity();
            if (context == null) {
                return;
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(context, fragment == null ? null : fragment.getResourceProvider());
            if (title != null && !title.isEmpty()) {
                builder.setTitle(title);
            }
            if (message != null && !message.isEmpty()) {
                builder.setMessage(message);
            }
            final boolean none = empty(positive) && empty(negative) && empty(neutral);
            if (none || !empty(positive)) {
                builder.setPositiveButton(none ? LocaleController.getString(R.string.OK) : positive, (d, w) -> Plugins.runCallback(callback, -1));
            }
            if (!empty(negative)) {
                builder.setNegativeButton(negative, (d, w) -> Plugins.runCallback(callback, -2));
            }
            if (!empty(neutral)) {
                builder.setNeutralButton(neutral, (d, w) -> Plugins.runCallback(callback, -3));
            }
            final AlertDialog dialog = builder.create();
            if (fragment != null) {
                fragment.showDialog(dialog);
            } else {
                dialog.show();
            }
            if (red) {
                dialog.redPositive();
            }
        });
    }

    private static boolean empty(String s) {
        return s == null || s.isEmpty();
    }

    public static void prompt(String title, String message, String placeholder, String initial, int callback) {
        ui(() -> {
            final BaseFragment fragment = lastFragment();
            if (fragment == null || fragment.getContext() == null) {
                return;
            }
            Prompt.show(fragment, title, empty(message) ? null : message, placeholder, initial, Prompt.MULTILINE, text -> Plugins.runCallback(callback, text));
        });
    }

    /** The callback gets the index of the chosen item. */
    public static void choose(String title, String[] items, int callback) {
        ui(() -> {
            final BaseFragment fragment = lastFragment();
            if (fragment == null || fragment.getContext() == null || items == null) {
                return;
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext(), fragment.getResourceProvider());
            if (!empty(title)) {
                builder.setTitle(title);
            }
            builder.setItems(items, (dialog, which) -> Plugins.runCallback(callback, which));
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            fragment.showDialog(builder.create());
        });
    }

    public static void copyToClipboard(String text) {
        ui(() -> AndroidUtilities.addToClipboard(text));
    }

    public static String clipboardText() {
        try {
            final ClipboardManager clipboard = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            final ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return "";
            }
            final CharSequence text = clip.getItemAt(0).coerceToText(ApplicationLoader.applicationContext);
            return text == null ? "" : text.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    public static void shareText(String text) {
        ui(() -> {
            final Activity activity = activity();
            if (activity == null) {
                return;
            }
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            activity.startActivity(Intent.createChooser(intent, null));
        });
    }

    // ---- sending ------------------------------------------------------------------------

    /**
     * Sends a text message on a plugin's behalf. The plugins' own send hooks are skipped for
     * it, so a plugin that answers from its hook cannot call itself for ever.
     */
    public static void sendText(int account, long peer, String text, Object replyTo, String parseMode) {
        if (peer == 0 || text == null || text.isEmpty()) {
            return;
        }
        ui(() -> {
            CharSequence[] message = {"html".equals(parseMode) ? Html.fromHtml(text).toString() : text};
            ArrayList<TLRPC.MessageEntity> entities = null;
            if ("markdown".equals(parseMode)) {
                entities = MediaDataController.getInstance(account).getEntities(message, true);
            }
            final MessageObject reply = replyTo instanceof MessageObject ? (MessageObject) replyTo : null;
            final SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                message[0].toString(), peer, reply, null, null, true, entities, null, null, true, 0, 0, null, false);
            Plugins.sendForPlugin(() -> SendMessagesHelper.getInstance(account).sendMessage(params));
        });
    }

    /** Sends a file from the device: kind is "photo" or anything else for a document. */
    public static void sendFile(int account, String kind, long peer, String path, String caption) {
        if (peer == 0 || path == null || path.isEmpty()) {
            return;
        }
        ui(() -> Plugins.sendForPlugin(() -> {
            final AccountInstance instance = AccountInstance.getInstance(account);
            if ("photo".equals(kind)) {
                SendMessagesHelper.prepareSendingPhoto(instance, path, null, peer, null, null, null, caption, null, null, null, 0, null, true, 0, 0, null);
            } else {
                SendMessagesHelper.prepareSendingDocument(instance, path, path, null, caption, null, peer, null, null, null, null, null, true, 0, null, null, false);
            }
        }));
    }

    /** Replaces the text of one of the user's own messages. */
    public static void editText(int account, Object message, String text) {
        if (!(message instanceof MessageObject) || text == null) {
            return;
        }
        ui(() -> {
            final MessageObject object = (MessageObject) message;
            object.editingMessage = text;
            object.editingMessageEntities = null;
            object.editingMessageSearchWebPage = true;
            SendMessagesHelper.getInstance(account).editMessage(object, null, null, null, null, null, null, false, false, null);
        });
    }
}
