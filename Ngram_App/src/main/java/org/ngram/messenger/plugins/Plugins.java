package org.ngram.messenger.plugins;

import static org.ngram.messenger.N.tr;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.json.JSONArray;
import org.json.JSONObject;
import org.ngram.core.NgramConfig;
import org.ngram.messenger.BuildConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The plugin engine. Plugins are Python files, as in exteraGram; Python itself is started only
 * when at least one plugin is switched on, a moment after the app's start and on its own thread.
 *
 * A plugin is somebody's code running inside the app with the app's rights: it can read what
 * the app can read and send what the app can send. That is said plainly before a plugin is
 * installed, and nothing is installed or switched on without the user doing it.
 *
 * Safety nets: a plugin whose hooks keep failing is switched off; two starts in a row that the
 * app did not outlive put the engine into safe mode, where no plugin is loaded.
 */
public final class Plugins {

    public static final String TAG = "NgramPlugin";
    private static final String KEY_ENABLED = "pluginsEnabled";
    private static final String KEY_BOOT = "pluginsBoot";
    private static final int START_DELAY_MS = 1500;
    private static final int ALIVE_AFTER_MS = 10000;
    private static final int LOG_LINES = 300;

    /** One plugin file. */
    public static final class Info {
        public String id = "";
        public String name = "";
        public String description = "";
        public String author = "";
        public String version = "";
        public String icon = "";
        public File file;
        public int lines;
        public boolean enabled;
        /** Its code is running now. */
        public boolean loaded;
        /** Why it could not be loaded, or why it was switched off; empty when all is well. */
        public String error = "";
    }

    public interface Done {
        void run(String error);
    }

    private static final ArrayList<Info> plugins = new ArrayList<>();
    private static boolean scanned;
    private static boolean bootChecked;
    private static volatile boolean safeMode;
    private static volatile boolean starting;
    private static volatile PyObject host;
    private static volatile String pythonVersion = "";
    private static volatile String startError = "";
    private static DispatchQueue queue;

    // What plugins watch. Read on every request, so kept as plain volatile snapshots.
    private static volatile Set<String> exactHooks = Collections.emptySet();
    private static volatile String[] partialHooks = new String[0];
    private static volatile boolean sendHook;
    /** Set on the thread that is sending a message on a plugin's behalf: its own hooks are skipped. */
    private static final ThreadLocal<Boolean> sendingForPlugin = new ThreadLocal<>();

    private static final ArrayDeque<String> log = new ArrayDeque<>();
    private static WeakReference<Activity> activity = new WeakReference<>(null);
    /** Screens showing plugins repaint when this is called. */
    public static volatile Runnable onChanged;

    private Plugins() {
    }

    // ---- files --------------------------------------------------------------------------

    public static File dir() {
        final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "plugins");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static Set<String> enabledIds() {
        final SharedPreferences prefs = NgramConfig.prefs();
        return prefs == null ? new HashSet<>() : new HashSet<>(prefs.getStringSet(KEY_ENABLED, new HashSet<>()));
    }

    private static void saveEnabled() {
        final SharedPreferences prefs = NgramConfig.prefs();
        if (prefs == null) {
            return;
        }
        final HashSet<String> ids = new HashSet<>();
        synchronized (plugins) {
            for (Info p : plugins) {
                if (p.enabled) {
                    ids.add(p.id);
                }
            }
        }
        prefs.edit().putStringSet(KEY_ENABLED, ids).apply();
    }

    private static final Pattern META = Pattern.compile("^__(id|name|description|author|version|icon)__\\s*=\\s*(\"\"\"|'''|\"|')(.*?)\\2\\s*$", Pattern.MULTILINE);

    /** The "__id__ = ..." constants at the top of a plugin file, read without running it. */
    private static Info readInfo(File file) {
        try {
            final String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            final Info info = new Info();
            info.file = file;
            info.lines = source.split("\n", -1).length;
            final Matcher m = META.matcher(source);
            while (m.find()) {
                final String value = m.group(3);
                switch (m.group(1)) {
                    case "id":
                        info.id = value;
                        break;
                    case "name":
                        info.name = value;
                        break;
                    case "description":
                        info.description = value;
                        break;
                    case "author":
                        info.author = value;
                        break;
                    case "version":
                        info.version = value;
                        break;
                    case "icon":
                        info.icon = value;
                        break;
                }
            }
            if (!info.id.matches("[A-Za-z0-9_\\-]{2,64}")) {
                return null;
            }
            if (info.name.isEmpty()) {
                info.name = info.id;
            }
            return info;
        } catch (Throwable e) {
            return null;
        }
    }

    /** What a file says about itself, or null when it is not a plugin. */
    public static Info peek(File file) {
        return readInfo(file);
    }

    private static void scan() {
        synchronized (plugins) {
            if (scanned) {
                return;
            }
            scanned = true;
            final Set<String> enabled = enabledIds();
            final File[] files = dir().listFiles((d, name) -> name.endsWith(".plugin"));
            if (files != null) {
                for (File file : files) {
                    final Info info = readInfo(file);
                    if (info != null) {
                        info.enabled = enabled.contains(info.id);
                        plugins.add(info);
                    }
                }
            }
            Collections.sort(plugins, (a, b) -> a.name.compareToIgnoreCase(b.name));
        }
    }

    public static List<Info> all() {
        scan();
        synchronized (plugins) {
            return new ArrayList<>(plugins);
        }
    }

    public static Info find(String id) {
        scan();
        synchronized (plugins) {
            for (Info p : plugins) {
                if (p.id.equals(id)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** One line for the settings list and the diagnostics report. */
    public static String summary() {
        final List<Info> list = all();
        if (list.isEmpty()) {
            return tr("Расширения на Python", "Extensions in Python");
        }
        int on = 0;
        for (Info p : list) {
            if (p.enabled) {
                on++;
            }
        }
        if (safeMode) {
            return tr("безопасный режим", "safe mode");
        }
        return tr("Установлено: ", "Installed: ") + list.size() + tr(", включено: ", ", on: ") + on;
    }

    public static boolean safeMode() {
        return safeMode;
    }

    public static boolean started() {
        return host != null;
    }

    public static String pythonVersion() {
        return pythonVersion;
    }

    public static String startError() {
        return startError;
    }

    public static Activity activity() {
        return activity.get();
    }

    // ---- the log ------------------------------------------------------------------------

    public static void log(String pluginId, String level, String message) {
        final String line = level + " " + (pluginId == null || pluginId.isEmpty() ? "" : "[" + pluginId + "] ") + message;
        synchronized (log) {
            log.addLast(line);
            while (log.size() > LOG_LINES) {
                log.removeFirst();
            }
        }
        if ("E".equals(level)) {
            Log.e(TAG, line);
        } else if ("W".equals(level)) {
            Log.w(TAG, line);
        } else {
            Log.d(TAG, line);
        }
    }

    public static String logText() {
        final StringBuilder sb = new StringBuilder();
        synchronized (log) {
            for (String line : log) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    public static void clearLog() {
        synchronized (log) {
            log.clear();
        }
    }

    private static void changed() {
        final Runnable r = onChanged;
        if (r != null) {
            AndroidUtilities.runOnUIThread(r);
        }
    }

    // ---- starting Python ----------------------------------------------------------------

    private static synchronized DispatchQueue queue() {
        if (queue == null) {
            queue = new DispatchQueue("ngramPlugins");
            queue.setPriority(Thread.MIN_PRIORITY + 1);
        }
        return queue;
    }

    /** Called when the main activity starts. */
    public static void onLaunch(Activity act, Intent intent) {
        activity = new WeakReference<>(act);
        if (BuildConfig.DEBUG && intent != null && intent.hasExtra("plugin")) {
            debugInstall(intent.getStringExtra("plugin"));
        }
        if (bootChecked) {
            return;
        }
        bootChecked = true;
        scan();
        boolean any = false;
        for (Info p : all()) {
            any |= p.enabled;
        }
        if (!any) {
            return;
        }
        final SharedPreferences prefs = NgramConfig.prefs();
        final int unfinished = prefs == null ? 0 : prefs.getInt(KEY_BOOT, 0);
        if (unfinished >= 2) {
            // Twice in a row the app did not live ten seconds after loading plugins.
            safeMode = true;
            log("", "W", "Safe mode: the app did not survive its last two starts with plugins, so none is loaded.");
            return;
        }
        if (prefs != null) {
            prefs.edit().putInt(KEY_BOOT, unfinished + 1).commit();
        }
        queue().postRunnable(() -> start(true), START_DELAY_MS);
        AndroidUtilities.runOnUIThread(() -> {
            final SharedPreferences p = NgramConfig.prefs();
            if (p != null) {
                p.edit().putInt(KEY_BOOT, 0).apply();
            }
        }, START_DELAY_MS + ALIVE_AFTER_MS);
    }

    /** Leaves safe mode and loads the plugins that are switched on. */
    public static void leaveSafeMode() {
        safeMode = false;
        final SharedPreferences prefs = NgramConfig.prefs();
        if (prefs != null) {
            prefs.edit().putInt(KEY_BOOT, 0).apply();
        }
        queue().postRunnable(() -> start(true));
    }

    /** Runs {@code then} on the main thread once Python is up; its argument is the error, or null. */
    public static void ensureStarted(Done then) {
        if (host != null) {
            then.run(null);
            return;
        }
        queue().postRunnable(() -> {
            start(false);
            final String error = host == null ? (startError.isEmpty() ? "Python did not start" : startError) : null;
            AndroidUtilities.runOnUIThread(() -> then.run(error));
        });
    }

    /** On the plugin thread. */
    private static void start(boolean loadEnabled) {
        if (host == null && !starting) {
            starting = true;
            try {
                final Context context = ApplicationLoader.applicationContext;
                if (!Python.isStarted()) {
                    Python.start(new AndroidPlatform(context));
                }
                final PyObject module = Python.getInstance().getModule("plugin_host");
                pythonVersion = module.callAttr("init", dir().getAbsolutePath()).toString();
                host = module;
                startError = "";
                log("", "I", "Engine started: " + pythonVersion);
            } catch (Throwable e) {
                startError = reason(e);
                log("", "E", "The engine did not start: " + startError);
            } finally {
                starting = false;
            }
        }
        if (host != null && loadEnabled && !safeMode) {
            for (Info p : all()) {
                if (p.enabled && !p.loaded) {
                    load(p);
                }
            }
        }
        changed();
    }

    private static String reason(Throwable e) {
        String text = e.getMessage();
        if (text == null || text.isEmpty()) {
            text = e.getClass().getSimpleName();
        }
        return text.length() > 4000 ? text.substring(0, 4000) + "…" : text;
    }

    private static String metaJson(Info p) {
        try {
            return new JSONObject().put("id", p.id).put("name", p.name).put("description", p.description)
                .put("author", p.author).put("version", p.version).put("icon", p.icon).toString();
        } catch (Throwable e) {
            return "{}";
        }
    }

    /** On the plugin thread, with Python up. */
    private static void load(Info p) {
        final PyObject h = host;
        if (h == null) {
            return;
        }
        String error;
        try {
            error = h.callAttr("load", p.file.getAbsolutePath(), metaJson(p)).toString();
        } catch (Throwable e) {
            error = reason(e);
        }
        p.loaded = error.isEmpty();
        p.error = error;
        if (p.loaded) {
            log(p.id, "I", "Loaded " + p.name + " " + p.version);
        } else {
            log(p.id, "E", "Not loaded:\n" + error);
        }
    }

    // ---- managing plugins ---------------------------------------------------------------

    public static void setEnabled(Info p, boolean on, Done done) {
        p.enabled = on;
        p.error = "";
        saveEnabled();
        if (!on) {
            queue().postRunnable(() -> {
                final PyObject h = host;
                if (h != null) {
                    try {
                        h.callAttr("unload", p.id);
                    } catch (Throwable e) {
                        log(p.id, "E", "unload: " + reason(e));
                    }
                }
                p.loaded = false;
                PluginMenus.removeAll(p.id);
                finish(done, null);
            });
            return;
        }
        queue().postRunnable(() -> {
            start(false);
            if (host == null) {
                p.enabled = false;
                saveEnabled();
                finish(done, startError);
                return;
            }
            load(p);
            if (!p.loaded) {
                p.enabled = false;
                saveEnabled();
            }
            finish(done, p.loaded ? null : p.error);
        });
    }

    private static void finish(Done done, String error) {
        AndroidUtilities.runOnUIThread(() -> {
            if (done != null) {
                done.run(error);
            }
            changed();
        });
    }

    /**
     * Checks a file and copies it into the plugins folder. The plugin is left switched off: the
     * user turns it on. {@code done} gets the error or null; on success {@code installed[0]} is
     * the plugin.
     */
    public static void install(File source, Info[] installed, Done done) {
        queue().postRunnable(() -> {
            start(false);
            final PyObject h = host;
            if (h == null) {
                finish(done, startError);
                return;
            }
            final Info info;
            try {
                // Python parses the file without running it and checks that it is a plugin at all.
                h.callAttr("read_metadata", source.getAbsolutePath());
                info = readInfo(source);
                if (info == null) {
                    throw new IllegalStateException("The plugin has no valid __id__");
                }
            } catch (Throwable e) {
                finish(done, reason(e));
                return;
            }
            final Info old = find(info.id);
            if (old != null && old.loaded) {
                try {
                    h.callAttr("unload", old.id);
                } catch (Throwable ignore) {
                }
                PluginMenus.removeAll(old.id);
            }
            final File target = new File(dir(), info.id + ".plugin");
            try {
                AndroidUtilities.copyFile(source, target);
            } catch (Throwable e) {
                finish(done, reason(e));
                return;
            }
            info.file = target;
            synchronized (plugins) {
                if (old != null) {
                    plugins.remove(old);
                    // An update keeps the plugin as it was: on stays on.
                    info.enabled = old.enabled;
                }
                plugins.add(info);
                Collections.sort(plugins, (a, b) -> a.name.compareToIgnoreCase(b.name));
            }
            if (info.enabled) {
                load(info);
            }
            installed[0] = info;
            log(info.id, "I", (old == null ? "Installed " : "Updated ") + info.name + " " + info.version);
            finish(done, null);
        });
    }

    public static void remove(Info p, Done done) {
        queue().postRunnable(() -> {
            final PyObject h = host;
            if (h != null && p.loaded) {
                try {
                    h.callAttr("unload", p.id);
                } catch (Throwable ignore) {
                }
            }
            PluginMenus.removeAll(p.id);
            synchronized (plugins) {
                plugins.remove(p);
            }
            saveEnabled();
            p.file.delete();
            new File(dir(), p.id + ".settings.json").delete();
            deleteTree(new File(dir(), p.id + ".data"));
            log(p.id, "I", "Removed");
            finish(done, null);
        });
    }

    private static void deleteTree(File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        file.delete();
    }

    /** Called by Python when a plugin's hooks failed too many times in a row. */
    static void failed(String id, String why) {
        final Info p = find(id);
        if (p == null) {
            return;
        }
        p.enabled = false;
        p.error = why;
        saveEnabled();
        queue().postRunnable(() -> {
            final PyObject h = host;
            if (h != null) {
                try {
                    h.callAttr("unload", id);
                } catch (Throwable ignore) {
                }
            }
            p.loaded = false;
            PluginMenus.removeAll(id);
            changed();
        });
        log(id, "E", "Switched off: " + why);
    }

    // ---- what Python tells the engine ---------------------------------------------------

    static void setHooks(String[] exact, String[] partial, boolean send) {
        final HashSet<String> set = new HashSet<>();
        if (exact != null) {
            Collections.addAll(set, exact);
        }
        exactHooks = set;
        partialHooks = partial == null ? new String[0] : partial.clone();
        sendHook = send;
    }

    private static boolean watched(String name) {
        if (exactHooks.contains(name)) {
            return true;
        }
        for (String part : partialHooks) {
            if (name.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /** Runs a callback Python registered under a number; any thread. */
    public static void runCallback(int id, Object... args) {
        final PyObject h = host;
        if (h == null || id == 0) {
            return;
        }
        try {
            h.callAttr("run_callback", id, args);
        } catch (Throwable e) {
            log("", "E", "callback: " + reason(e));
        }
    }

    // ---- hooks called by the app --------------------------------------------------------

    /** A request about to leave for Telegram: returns it, a replacement, or null to drop it. */
    public static TLObject onRequest(int account, TLObject request) {
        final PyObject h = host;
        if (h == null || request == null || exactHooks.isEmpty() && partialHooks.length == 0) {
            return request;
        }
        final String name = request.getClass().getSimpleName();
        if (!watched(name)) {
            return request;
        }
        try {
            final List<PyObject> result = h.callAttr("dispatch_request", name, account, request).asList();
            if ("CANCEL".equals(result.get(0).toString())) {
                return null;
            }
            final TLObject changed = result.get(1).toJava(TLObject.class);
            return changed == null ? request : changed;
        } catch (Throwable e) {
            log("", "E", "pre_request_hook " + name + ": " + reason(e));
            return request;
        }
    }

    public static void onResponse(int account, TLObject request, TLObject response, TLRPC.TL_error error) {
        final PyObject h = host;
        if (h == null || request == null || exactHooks.isEmpty() && partialHooks.length == 0) {
            return;
        }
        final String name = request.getClass().getSimpleName();
        if (!watched(name)) {
            return;
        }
        try {
            h.callAttr("dispatch_response", name, account, response, error);
        } catch (Throwable e) {
            log("", "E", "post_request_hook " + name + ": " + reason(e));
        }
    }

    public static void onUpdates(int account, TLRPC.Updates updates) {
        final PyObject h = host;
        if (h == null || updates == null || exactHooks.isEmpty() && partialHooks.length == 0) {
            return;
        }
        try {
            final String container = updates.getClass().getSimpleName();
            if (watched(container)) {
                h.callAttr("dispatch_updates", container, account, updates);
            }
            if (updates.update != null) {
                one(h, account, updates.update);
            }
            if (updates.updates != null) {
                for (int i = 0; i < updates.updates.size(); i++) {
                    one(h, account, updates.updates.get(i));
                }
            }
        } catch (Throwable e) {
            log("", "E", "on_update_hook: " + reason(e));
        }
    }

    private static void one(PyObject h, int account, TLRPC.Update update) {
        if (update == null) {
            return;
        }
        final String name = update.getClass().getSimpleName();
        if (watched(name)) {
            h.callAttr("dispatch_update", name, account, update);
        }
    }

    /** A message about to be sent: returns the parameters to send with, or null to send nothing. */
    public static SendMessagesHelper.SendMessageParams onSendMessage(int account, SendMessagesHelper.SendMessageParams params) {
        final PyObject h = host;
        if (h == null || !sendHook || params == null || Boolean.TRUE.equals(sendingForPlugin.get())) {
            return params;
        }
        try {
            return "CANCEL".equals(h.callAttr("dispatch_send", account, params).toString()) ? null : params;
        } catch (Throwable e) {
            log("", "E", "on_send_message_hook: " + reason(e));
            return params;
        }
    }

    /** Runs a send that a plugin asked for, with the plugins' own send hooks out of the way. */
    static void sendForPlugin(Runnable send) {
        sendingForPlugin.set(true);
        try {
            send.run();
        } finally {
            sendingForPlugin.set(false);
        }
    }

    // ---- menus and settings, called from the screens ------------------------------------

    static boolean menuVisible(String itemId, String[] keys, Object[] values) {
        final PyObject h = host;
        if (h == null) {
            return false;
        }
        try {
            return h.callAttr("menu_visible", itemId, keys, values).toBoolean();
        } catch (Throwable e) {
            return false;
        }
    }

    static void clickMenu(String itemId, String[] keys, Object[] values) {
        final PyObject h = host;
        if (h == null) {
            return;
        }
        try {
            h.callAttr("click_menu", itemId, keys, values);
        } catch (Throwable e) {
            log("", "E", "menu item: " + reason(e));
        }
    }

    /** What a loaded plugin has registered: commands, hooks, menu items. */
    public static JSONObject describe(String id) {
        final PyObject h = host;
        try {
            return h == null ? new JSONObject() : new JSONObject(h.callAttr("describe", id).toString());
        } catch (Throwable e) {
            return new JSONObject();
        }
    }

    public static JSONArray settings(String id, String path) {
        final PyObject h = host;
        try {
            return h == null ? new JSONArray() : new JSONArray(h.callAttr("settings_json", id, path).toString());
        } catch (Throwable e) {
            return new JSONArray();
        }
    }

    public static void setSetting(String id, String path, int index, Object value) {
        final PyObject h = host;
        if (h == null) {
            return;
        }
        try {
            h.callAttr("settings_set", id, path, index, JSONObject.wrap(value) instanceof String ? JSONObject.quote((String) value) : String.valueOf(value));
        } catch (Throwable e) {
            log(id, "E", "setting: " + reason(e));
        }
    }

    public static void clickSetting(String id, String path, int index, boolean longClick) {
        final PyObject h = host;
        if (h == null) {
            return;
        }
        try {
            h.callAttr("settings_click", id, path, index, longClick);
        } catch (Throwable e) {
            log(id, "E", "setting: " + reason(e));
        }
    }

    public static void resetSettings(String id) {
        final PyObject h = host;
        if (h == null) {
            return;
        }
        try {
            h.callAttr("settings_reset", id);
        } catch (Throwable e) {
            log(id, "E", "settings: " + reason(e));
        }
    }

    /** The example plugins that come with the app, as file names under assets/plugin_examples. */
    public static String[] examples() {
        try {
            final String[] names = ApplicationLoader.applicationContext.getAssets().list("plugin_examples");
            return names == null ? new String[0] : names;
        } catch (Throwable e) {
            return new String[0];
        }
    }

    /** Copies a bundled example into a temporary file so that it can be installed like any other. */
    public static File exampleFile(String name) throws Exception {
        final File out = new File(ApplicationLoader.applicationContext.getCacheDir(), "example_" + name);
        try (java.io.InputStream in = ApplicationLoader.applicationContext.getAssets().open("plugin_examples/" + name)) {
            AndroidUtilities.copyFile(in, out);
        }
        return out;
    }

    /** Debug builds: "--es plugin <file>" installs and switches on a plugin file, for tests. */
    public static void debugInstall(String path) {
        if (!BuildConfig.DEBUG || path == null) {
            return;
        }
        final Info[] installed = new Info[1];
        install(new File(path), installed, error -> {
            if (error != null) {
                log("", "E", "debug install: " + error);
            } else if (!installed[0].enabled) {
                setEnabled(installed[0], true, e -> log("", e == null ? "I" : "E", "debug install: " + (e == null ? "enabled " + installed[0].id : e)));
            }
        });
    }
}
