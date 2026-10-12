package org.ngram.messenger;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Files Ngram makes for the user to take out of the app: an exported chat, a settings backup,
 * a picture for the clipboard. They are written to a folder the app's file provider already
 * serves, handed over by a content address, and removed again a day later.
 */
public final class Share {

    private static final long KEEP_MS = 24L * 60 * 60 * 1000;

    private Share() {
    }

    public static File dir() {
        // "files/cache" is one of the folders Telegram's FileProvider is allowed to serve.
        final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache/ngram");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** A file in the outgoing folder under a name that is safe on any file system. */
    public static File file(String name) {
        return new File(dir(), safeName(name));
    }

    public static String safeName(String name) {
        if (name == null) {
            return "file";
        }
        String clean = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (clean.length() > 80) {
            clean = clean.substring(0, 80);
        }
        return clean.isEmpty() ? "file" : clean;
    }

    public static Uri uri(File file) {
        return FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.getApplicationId() + ".provider", file);
    }

    public static void write(File file, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Opens the system's "share" sheet for a file. */
    public static boolean send(Context context, File file, String mime, CharSequence title) {
        try {
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mime);
            intent.putExtra(Intent.EXTRA_STREAM, uri(file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            final Intent chooser = Intent.createChooser(intent, title);
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(chooser);
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /** Removes what was made more than a day ago; called at start. */
    public static void cleanOld() {
        try {
            final File[] files = dir().listFiles();
            if (files == null) {
                return;
            }
            final long now = System.currentTimeMillis();
            for (File f : files) {
                if (f.isFile() && now - f.lastModified() > KEEP_MS) {
                    f.delete();
                }
            }
        } catch (Throwable ignore) {
        }
    }
}
