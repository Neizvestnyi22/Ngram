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
        /** "kit" for a plugin written with Ngram's own kit, "class" for the exteraGram kind. */
        public String kind = "class";
        /** What the author says the plugin needs, in the author's words (a kit passport's @needs). */
        public String needs = "";
        /** Python packages it asks for, and where a newer version of the file may be found. */
        public String requires = "", update = "";
        /** What its code is able to do, found by reading it: network, app, send, watch, files, shell. */
        public final ArrayList<String> uses = new ArrayList<>();
        /** Packages it asks for that are not on the device yet. */
        public final ArrayList<String> missing = new ArrayList<>();
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
    private static final Pattern PASSPORT = Pattern.compile("^#\\s*@([a-z_]+)\\s+(.*?)\\s*$");

    /** A kit plugin's passport: the "# @id hello" comment lines before its first line of code. */
    private static void readPassport(String source, Info info) {
        for (String line : source.split("\n")) {
            final String s = line.trim();
            if (s.isEmpty() || s.startsWith("#!")) {
                continue;
            }
            if (!s.startsWith("#")) {
                break;
            }
            final Matcher m = PASSPORT.matcher(s);
            if (!m.matches()) {
                continue;
            }
            final String value = m.group(2);
            switch (m.group(1)) {
                case "id":
                    if (info.id.isEmpty()) {
                        info.id = value;
                    }
                    break;
                case "name":
                    info.name = value;
                    break;
                case "about":
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
                case "needs":
                    info.needs = value;
                    break;
                case "requires":
                    info.requires = value;
                    break;
                case "update":
                    info.update = value;
                    break;
            }
        }
        if (!info.id.isEmpty()) {
            info.kind = "kit";
        }
    }

    /** What a plugin file says about itself, read without running it. */
    private static Info readInfo(File file) {
        try {
            final String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            final Info info = new Info();
            info.file = file;
            info.lines = source.split("\n", -1).length;
            readPassport(source, info);
            final Matcher m = META.matcher(source);
            while (info.kind.equals("class") && m.find()) {
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
            return tr("пока ни одного", "none yet");
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
                .put("author", p.author).put("version", p.version).put("icon", p.icon)
                .put("kind", p.kind).put("requires", p.requires).toString();
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
                final String found = h.callAttr("read_metadata", source.getAbsolutePath()).toString();
                info = readInfo(source);
                if (info == null) {
                    throw new IllegalStateException("The plugin has no valid id");
                }
                fillInspection(info, found);
                if (!info.missing.isEmpty()) {
                    // The user saw the list of packages and agreed before this was called.
                    final String failed = h.callAttr("pip_install", info.requires).toString();
                    if (!failed.isEmpty()) {
                        throw new IllegalStateException(failed);
                    }
                    info.missing.clear();
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

    private static void fillInspection(Info info, String json) {
        try {
            final JSONObject o = new JSONObject(json);
            info.uses.clear();
            info.missing.clear();
            final JSONArray uses = o.optJSONArray("uses");
            for (int i = 0; uses != null && i < uses.length(); i++) {
                info.uses.add(uses.optString(i));
            }
            final JSONArray missing = o.optJSONArray("missing");
            for (int i = 0; missing != null && i < missing.length(); i++) {
                info.missing.add(missing.optString(i));
            }
        } catch (Throwable ignore) {
        }
    }

    public interface Inspected {
        void run(Info info, String error);
    }

    /**
     * Reads a file that is about to be installed: who it says it is and what its code can do.
     * Starts Python when it is not up yet; answers on the main thread.
     */
    public static void inspect(File source, Inspected done) {
        queue().postRunnable(() -> {
            start(false);
            final PyObject h = host;
            Info info = null;
            String error = null;
            if (h == null) {
                error = startError.isEmpty() ? "Python did not start" : startError;
            } else {
                try {
                    final String found = h.callAttr("read_metadata", source.getAbsolutePath()).toString();
                    info = readInfo(source);
                    if (info == null) {
                        error = "The plugin has no valid id";
                    } else {
                        fillInspection(info, found);
                    }
                } catch (Throwable e) {
                    error = reason(e);
                }
            }
            final Info result = info;
            final String failure = error;
            AndroidUtilities.runOnUIThread(() -> done.run(failure == null ? result : null, failure));
        });
    }

    /** Reads the plugin's file again and starts it afresh: for someone writing a plugin. */
    public static void reload(Info p, Done done) {
        queue().postRunnable(() -> {
            start(false);
            final PyObject h = host;
            if (h == null) {
                finish(done, startError);
                return;
            }
            try {
                h.callAttr("unload", p.id);
            } catch (Throwable ignore) {
            }
            PluginMenus.removeAll(p.id);
            final Info fresh = readInfo(p.file);
            if (fresh == null || !fresh.id.equals(p.id)) {
                p.loaded = false;
                p.error = "The file no longer has the id " + p.id;
                finish(done, p.error);
                return;
            }
            p.name = fresh.name;
            p.description = fresh.description;
            p.author = fresh.author;
            p.version = fresh.version;
            p.kind = fresh.kind;
            p.needs = fresh.needs;
            p.requires = fresh.requires;
            p.update = fresh.update;
            p.lines = fresh.lines;
            load(p);
            if (p.loaded && !p.enabled) {
                p.enabled = true;
                saveEnabled();
            }
            finish(done, p.loaded ? null : p.error);
        });
    }

    public interface Downloaded {
        void run(File file, String error);
    }

    /** Fetches a plugin file from an https address into a temporary file; answers on the main thread. */
    public static void download(String address, Downloaded done) {
        new Thread(() -> {
            File file = null;
            String error = null;
            java.net.HttpURLConnection connection = null;
            try {
                final java.net.URL url = new java.net.URL(address.trim());
                if (!"https".equalsIgnoreCase(url.getProtocol())) {
                    throw new IllegalArgumentException(tr("Нужна ссылка, которая начинается с https://", "The link has to start with https://"));
                }
                connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "Ngram/" + BuildConfig.VERSION_NAME);
                final int code = connection.getResponseCode();
                if (code != 200) {
                    throw new IllegalStateException("HTTP " + code);
                }
                file = new File(ApplicationLoader.applicationContext.getCacheDir(), "downloaded.plugin");
                final int limit = 2 * 1024 * 1024;
                try (java.io.InputStream in = connection.getInputStream(); java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                    final byte[] buffer = new byte[16384];
                    int total = 0, read;
                    while ((read = in.read(buffer)) > 0) {
                        total += read;
                        if (total > limit) {
                            throw new IllegalStateException(tr("Файл больше 2 МБ — это не похоже на плагин", "The file is over 2 MB: that does not look like a plugin"));
                        }
                        out.write(buffer, 0, read);
                    }
                }
            } catch (Throwable e) {
                error = reason(e);
                file = null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final File result = file;
            final String failure = error;
            AndroidUtilities.runOnUIThread(() -> done.run(result, failure));
        }, "ngram-plugin-download").start();
    }

    /** -1, 0 or 1 as version a is older than, the same as or newer than b ("1.10" is newer than "1.9"). */
    public static int compareVersions(String a, String b) {
        final String[] x = (a == null ? "" : a).split("[^0-9]+"), y = (b == null ? "" : b).split("[^0-9]+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            final long l = i < x.length && !x[i].isEmpty() ? Long.parseLong(x[i].length() > 9 ? x[i].substring(0, 9) : x[i]) : 0;
            final long r = i < y.length && !y[i].isEmpty() ? Long.parseLong(y[i].length() > 9 ? y[i].substring(0, 9) : y[i]) : 0;
            if (l != r) {
                return l < r ? -1 : 1;
            }
        }
        return 0;
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

    // ---- the app's own moments, for kit plugins -----------------------------------------

    /** Names of the moments some plugin listens for; the others never cross into Python. */
    private static volatile Set<String> events = Collections.emptySet();
    /** Notifications of the app that are being passed on: name -> id in NotificationCenter. */
    private static final java.util.HashMap<String, Integer> watched = new java.util.HashMap<>();
    private static final org.telegram.messenger.NotificationCenter.NotificationCenterDelegate observer = (id, account, args) -> {
        String name = null;
        synchronized (watched) {
            for (java.util.Map.Entry<String, Integer> e : watched.entrySet()) {
                if (e.getValue() == id) {
                    name = e.getKey();
                    break;
                }
            }
        }
        if (name != null) {
            emit("app:" + name, new String[]{"account", "args"}, new Object[]{account, args});
        }
    };

    static void setEvents(String[] names) {
        final HashSet<String> set = new HashSet<>();
        if (names != null) {
            Collections.addAll(set, names);
        }
        events = set;
        AndroidUtilities.runOnUIThread(() -> {
            // "app:didReceiveNewMessages" asks for one of the app's own notifications by the name of its field.
            for (String name : set) {
                if (!name.startsWith("app:")) {
                    continue;
                }
                final String field = name.substring(4);
                synchronized (watched) {
                    if (watched.containsKey(field)) {
                        continue;
                    }
                }
                try {
                    final int id = org.telegram.messenger.NotificationCenter.class.getField(field).getInt(null);
                    synchronized (watched) {
                        watched.put(field, id);
                    }
                    org.telegram.messenger.NotificationCenter.getInstance(org.telegram.messenger.UserConfig.selectedAccount).addObserver(observer, id);
                    org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(observer, id);
                } catch (Throwable e) {
                    log("", "W", "The app has no notification called " + field);
                }
            }
        });
    }

    /** Tells kit plugins that something happened; true when one of them stopped it. */
    public static boolean emit(String name, String[] keys, Object[] values) {
        final PyObject h = host;
        if (h == null || !events.contains(name)) {
            return false;
        }
        try {
            return h.callAttr("dispatch_event", name, keys, values).toBoolean();
        } catch (Throwable e) {
            log("", "E", name + ": " + reason(e));
            return false;
        }
    }

    /** The app came to the front (true) or went to the background. */
    public static void onApp(boolean resumed) {
        final PyObject h = host;
        if (h == null) {
            return;
        }
        try {
            h.callAttr("app_event", resumed ? "RESUME" : "PAUSE");
        } catch (Throwable e) {
            log("", "E", "app event: " + reason(e));
        }
    }

    /** A chat screen opened or closed for good. */
    public static void onChat(org.telegram.ui.ChatActivity chat, boolean opened) {
        final String name = opened ? "chat.open" : "chat.close";
        if (host != null && events.contains(name)) {
            emit(name, new String[]{"chat_id", "account", "fragment"}, new Object[]{chat.getDialogId(), chat.getCurrentAccount(), chat});
        }
    }

    /** A link is about to be opened: true when a plugin took it over. */
    public static boolean onOpenUrl(Context context, android.net.Uri uri) {
        if (host == null || !events.contains("link.open")) {
            return false;
        }
        return emit("link.open", new String[]{"url", "context"}, new Object[]{uri.toString(), context});
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
