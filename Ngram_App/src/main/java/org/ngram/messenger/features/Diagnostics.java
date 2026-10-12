package org.ngram.messenger.features;

import static org.ngram.messenger.N.tr;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.ngram.core.NgramConfig;
import org.ngram.messenger.BuildConfig;
import org.ngram.messenger.Share;
import org.ngram.messenger.ai.Ai;
import org.ngram.messenger.plugins.Plugins;
import org.ngram.messenger.ui.InfoSheet;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * What to send along when something goes wrong: the build, the device, Ngram's settings and
 * the last crash. The report holds no messages, no names, no phone number and no keys — it is
 * shown in full before it is copied, so the user can check.
 */
public final class Diagnostics {

    private static final String CRASH_FILE = "ngram_crash.txt";
    private static final int CRASH_LIMIT = 16000;

    private Diagnostics() {
    }

    /** Remembers the last uncaught error, then lets the app end the way it would have. */
    public static void install(Context context) {
        final File file = new File(context.getFilesDir(), CRASH_FILE);
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                String text = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                    + "\nNgram " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ", " + BuildConfig.BUILD_TYPE + ")"
                    + "\nAndroid " + Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + "), " + Build.MANUFACTURER + " " + Build.MODEL
                    + "\nthread: " + thread.getName() + "\n\n" + Log.getStackTraceString(error);
                if (text.length() > CRASH_LIMIT) {
                    text = text.substring(0, CRASH_LIMIT) + "\n…";
                }
                Share.write(file, text);
            } catch (Throwable ignore) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    public static String lastCrash() {
        try {
            final File file = new File(ApplicationLoader.applicationContext.getFilesDir(), CRASH_FILE);
            if (!file.exists()) {
                return null;
            }
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        }
    }

    public static void forgetCrash() {
        try {
            new File(ApplicationLoader.applicationContext.getFilesDir(), CRASH_FILE).delete();
        } catch (Throwable ignore) {
        }
    }

    public static InfoSheet.Rows report() {
        final InfoSheet.Rows rows = new InfoSheet.Rows();
        final Context context = ApplicationLoader.applicationContext;

        rows.add("Ngram", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ", " + BuildConfig.BUILD_TYPE + ")");
        rows.add("Telegram", BuildConfig.TELEGRAM_VERSION);
        rows.add(tr("Устройство", "Device"), Build.MANUFACTURER + " " + Build.MODEL);
        rows.add("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        rows.add(tr("Архитектура", "Architecture"), Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?");
        rows.add(tr("Экран", "Screen"), AndroidUtilities.displaySize.x + " × " + AndroidUtilities.displaySize.y + ", " + AndroidUtilities.density + "x");
        final Locale locale = LocaleController.getInstance().getCurrentLocale();
        rows.add(tr("Язык", "Language"), locale == null ? "?" : locale.toString());

        rows.section(tr("Скорость и эффекты", "Speed and Effects"));
        final int performance = SharedConfig.getDevicePerformanceClass();
        rows.add(tr("Класс устройства", "Device Class"), performance == SharedConfig.PERFORMANCE_CLASS_HIGH ? tr("быстрое", "high")
            : performance == SharedConfig.PERFORMANCE_CLASS_AVERAGE ? tr("среднее", "average") : tr("медленное", "low"));
        rows.add(tr("Жидкое стекло", "Liquid Glass"), onOff(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)));
        rows.add(tr("Размытие в чате", "Blur in Chats"), onOff(LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)));
        rows.add(tr("Преломление стекла", "Glass Refraction"), Build.VERSION.SDK_INT >= 33 ? tr("доступно", "available") : tr("нужен Android 13", "needs Android 13"));
        final Runtime runtime = Runtime.getRuntime();
        rows.add(tr("Память приложения", "App Memory"), mb(runtime.totalMemory() - runtime.freeMemory()) + " / " + mb(runtime.maxMemory()));
        try {
            rows.add(tr("Свободно на диске", "Free Storage"), AndroidUtilities.formatFileSize(context.getFilesDir().getUsableSpace()));
        } catch (Throwable ignore) {
        }

        rows.section(tr("Приложение", "The App"));
        rows.add(tr("Аккаунтов", "Accounts"), UserConfig.getActivatedAccountsCount());
        try {
            final SharedPreferences notifications = org.telegram.messenger.MessagesController.getGlobalNotificationsSettings();
            rows.add(tr("Фоновое соединение", "Background Connection"), onOff(notifications.getBoolean("pushConnection", true)));
        } catch (Throwable ignore) {
        }
        final Ai.Provider provider = Ai.active();
        rows.add(tr("Ассистент", "Assistant"), provider == null ? tr("не настроен", "not set up")
            : provider.title() + " · " + (provider.model.isEmpty() ? "—" : provider.model) + " · " + tr("провайдеров: ", "providers: ") + Ai.providers().size());
        rows.add(tr("Плагины", "Plugins"), Plugins.summary());

        final String changed = changedSettings();
        if (!changed.isEmpty()) {
            rows.section(tr("Настройки Ngram", "Ngram's Settings"));
            rows.add(tr("Изменено", "Changed"), changed);
        }

        final String crash = lastCrash();
        if (crash != null) {
            rows.section(tr("Последний сбой", "Last Crash"));
            rows.add(tr("Что произошло", "What Happened"), crash.length() > 2400 ? crash.substring(0, 2400) + "\n…" : crash);
        }
        return rows;
    }

    /** The stored settings as "key=value", without the ones that hold the user's own words. */
    private static String changedSettings() {
        final SharedPreferences prefs = NgramConfig.prefs();
        if (prefs == null) {
            return "";
        }
        final ArrayList<String> items = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            final String key = entry.getKey();
            if (entry.getValue() instanceof String) {
                // Templates, saved sets, the title: the user's text stays out of a report.
                items.add(key + "=…");
            } else {
                items.add(key + "=" + entry.getValue());
            }
        }
        Collections.sort(items);
        final StringBuilder sb = new StringBuilder();
        for (String item : items) {
            sb.append(sb.length() == 0 ? "" : ", ").append(item);
        }
        return sb.toString();
    }

    private static String onOff(boolean on) {
        return on ? tr("включено", "on") : tr("выключено", "off");
    }

    private static String mb(long bytes) {
        return (bytes / (1024 * 1024)) + tr(" МБ", " MB");
    }

    /** How much Ngram's own temporary files take. */
    public static long tempSize() {
        long total = 0;
        try {
            final File[] files = Share.dir().listFiles();
            if (files != null) {
                for (File f : files) {
                    total += f.length();
                }
            }
        } catch (Throwable ignore) {
        }
        return total;
    }

    public static void clearTemp() {
        try {
            final File[] files = Share.dir().listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        } catch (Throwable ignore) {
        }
    }
}
