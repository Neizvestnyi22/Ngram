package org.ngram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.ngram.core.NgramConfig;
import org.telegram.messenger.Utilities;

import java.io.File;

/**
 * Ngram 2.x was a different app under the same name: it kept its accounts in TDLib folders,
 * which this version cannot read. After an update over 2.x those folders are only dead weight
 * (often hundreds of megabytes), so they are removed once. The assistant's providers, keys and
 * the installed plugin files are left alone.
 */
final class Legacy {

    private Legacy() {
    }

    static void cleanUpOnce(Context context) {
        final SharedPreferences prefs = NgramConfig.prefs();
        if (prefs == null || prefs.getBoolean("legacyCleaned", false)) {
            return;
        }
        final File files = context.getFilesDir();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final File[] children = files.listFiles();
                if (children != null) {
                    for (File child : children) {
                        final String name = child.getName();
                        if (child.isDirectory() && (name.equals("tdlib") || name.startsWith("tdlib_"))) {
                            delete(child);
                        }
                    }
                }
                prefs.edit().putBoolean("legacyCleaned", true).apply();
            } catch (Throwable ignore) {
                // Not cleaned this time: it is tried again at the next start.
            }
        });
    }

    private static void delete(File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
