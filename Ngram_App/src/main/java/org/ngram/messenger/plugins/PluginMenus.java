package org.ngram.messenger.plugins;

import static org.ngram.messenger.N.tr;

import android.content.Context;

import org.ngram.core.NgramHooks;
import org.ngram.messenger.features.Details;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The menu items plugins have added, and the places they show up in: the menu of a message,
 * the "Ngram" entry of a chat's menu, the menu of a profile and the Plugins screen itself
 * (for items that in exteraGram live in the side drawer, which Ngram does not have).
 */
public final class PluginMenus {

    public static final String MESSAGE = "MESSAGE_CONTEXT_MENU";
    public static final String CHAT = "CHAT_ACTION_MENU";
    public static final String PROFILE = "PROFILE_ACTION_MENU";
    public static final String DRAWER = "DRAWER_MENU";
    public static final String MAIN = "MAIN_MENU";

    private static final int FIRST_OPTION = NgramHooks.MESSAGE_OPTION_BASE + 100;

    public static final class Item {
        public final String pluginId;
        public final String itemId;
        public final String type;
        public final String text;
        public final String subtext;
        public final String icon;
        public final int priority;
        public final boolean conditional;

        Item(String pluginId, String itemId, String type, String text, String subtext, String icon, int priority, boolean conditional) {
            this.pluginId = pluginId;
            this.itemId = itemId;
            this.type = type;
            this.text = text;
            this.subtext = subtext;
            this.icon = icon;
            this.priority = priority;
            this.conditional = conditional;
        }
    }

    private static final CopyOnWriteArrayList<Item> items = new CopyOnWriteArrayList<>();
    /** The plugin items of the message menu that is open now, in the order of their option ids. */
    private static final ArrayList<Item> shownInMessageMenu = new ArrayList<>();

    private PluginMenus() {
    }

    static void register(String pluginId, String itemId, String type, String text, String subtext, String icon, int priority, boolean conditional) {
        remove(itemId);
        items.add(new Item(pluginId, itemId, type, text, subtext, icon, priority, conditional));
    }

    static void remove(String itemId) {
        for (Item item : items) {
            if (item.itemId.equals(itemId)) {
                items.remove(item);
            }
        }
    }

    static void removeAll(String pluginId) {
        for (Item item : items) {
            if (item.pluginId.equals(pluginId)) {
                items.remove(item);
            }
        }
    }

    private static ArrayList<Item> ofType(String... types) {
        final ArrayList<Item> list = new ArrayList<>();
        for (Item item : items) {
            for (String type : types) {
                if (type.equals(item.type)) {
                    list.add(item);
                    break;
                }
            }
        }
        Collections.sort(list, (a, b) -> b.priority - a.priority);
        return list;
    }

    /** exteraGram's drawable names are Telegram's own, so most of them resolve as they are. */
    private static int icon(String name) {
        if (name != null && !name.isEmpty()) {
            try {
                final Context context = ApplicationLoader.applicationContext;
                final int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
                if (id != 0) {
                    return id;
                }
            } catch (Throwable ignore) {
            }
        }
        return org.ngram.messenger.R.drawable.ngram_set_plugins;
    }

    // ---- what a plugin is told about the place it was tapped in --------------------------

    private static LinkedHashMap<String, Object> context(BaseFragment fragment, long dialogId, MessageObject message) {
        final LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("account", fragment.getCurrentAccount());
        map.put("context", fragment.getParentActivity());
        map.put("fragment", fragment);
        map.put("dialog_id", dialogId);
        final MessagesController controller = fragment.getMessagesController();
        if (dialogId > 0) {
            map.put("userId", dialogId);
            map.put("user", controller.getUser(dialogId));
        } else if (dialogId < 0) {
            map.put("chatId", -dialogId);
            map.put("chat", controller.getChat(-dialogId));
        }
        if (message != null) {
            map.put("message", message);
        }
        return map;
    }

    private static boolean visible(Item item, LinkedHashMap<String, Object> context) {
        return !item.conditional || Plugins.menuVisible(item.itemId, context.keySet().toArray(new String[0]), context.values().toArray());
    }

    private static void click(Item item, LinkedHashMap<String, Object> context) {
        Plugins.clickMenu(item.itemId, context.keySet().toArray(new String[0]), context.values().toArray());
    }

    // ---- the menu of a message ----------------------------------------------------------

    public static void fillMessageMenu(ChatActivity chat, MessageObject message, ArrayList<Integer> icons, ArrayList<CharSequence> names, ArrayList<Integer> options) {
        shownInMessageMenu.clear();
        if (items.isEmpty() || !Plugins.started()) {
            return;
        }
        final LinkedHashMap<String, Object> context = context(chat, chat.getDialogId(), message);
        for (Item item : ofType(MESSAGE)) {
            if (visible(item, context)) {
                names.add(item.text);
                icons.add(icon(item.icon));
                options.add(FIRST_OPTION + shownInMessageMenu.size());
                shownInMessageMenu.add(item);
            }
        }
    }

    public static boolean onMessageMenu(ChatActivity chat, int option, MessageObject message) {
        final int index = option - FIRST_OPTION;
        if (index < 0 || index >= shownInMessageMenu.size()) {
            return false;
        }
        click(shownInMessageMenu.get(index), context(chat, chat.getDialogId(), message));
        return true;
    }

    // ---- the "Ngram" entry of a chat's menu ---------------------------------------------

    public static void addChatItems(ChatActivity chat, ArrayList<CharSequence> names, ArrayList<Integer> icons, ArrayList<Runnable> actions) {
        if (items.isEmpty() || !Plugins.started()) {
            return;
        }
        final LinkedHashMap<String, Object> context = context(chat, chat.getDialogId(), null);
        for (Item item : ofType(CHAT)) {
            if (visible(item, context)) {
                names.add(item.text);
                icons.add(icon(item.icon));
                actions.add(() -> click(item, context));
            }
        }
    }

    // ---- the menu of a profile ----------------------------------------------------------

    public static boolean hasProfileItems() {
        return Plugins.started() && !ofType(PROFILE).isEmpty();
    }

    public static void showProfileMenu(BaseFragment profile, long dialogId, long topicId) {
        if (profile.getContext() == null) {
            return;
        }
        final LinkedHashMap<String, Object> context = context(profile, dialogId, null);
        final ArrayList<CharSequence> names = new ArrayList<>();
        final ArrayList<Integer> icons = new ArrayList<>();
        final ArrayList<Runnable> actions = new ArrayList<>();
        names.add(tr("Сведения", "Details"));
        icons.add(R.drawable.msg_info);
        actions.add(() -> Details.showPeer(profile, dialogId, topicId));
        for (Item item : ofType(PROFILE)) {
            if (visible(item, context)) {
                names.add(item.text);
                icons.add(icon(item.icon));
                actions.add(() -> click(item, context));
            }
        }
        final int[] iconArray = new int[icons.size()];
        for (int i = 0; i < iconArray.length; i++) {
            iconArray[i] = icons.get(i);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(profile.getContext(), profile.getResourceProvider());
        builder.setTitle("Ngram");
        builder.setItems(names.toArray(new CharSequence[0]), iconArray, (dialog, which) -> {
            if (which >= 0 && which < actions.size()) {
                actions.get(which).run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        profile.showDialog(builder.create());
    }

    // ---- items that have no place of their own ------------------------------------------

    /** Items a plugin put into the side drawer or the main menu: shown on the Plugins screen. */
    public static ArrayList<Item> general() {
        return ofType(DRAWER, MAIN);
    }

    public static void clickGeneral(BaseFragment fragment, Item item) {
        click(item, context(fragment, 0, null));
    }

    /** The same items in the menu of the main screen (the three dots above the chat list). */
    public static void fillMainMenu(org.telegram.ui.Components.ItemOptions options) {
        if (items.isEmpty() || !Plugins.started()) {
            return;
        }
        for (Item item : general()) {
            options.add(icon(item.icon), item.text, () -> {
                final BaseFragment last = org.telegram.ui.LaunchActivity.getSafeLastFragment();
                if (last != null) {
                    clickGeneral(last, item);
                }
            });
        }
    }
}
