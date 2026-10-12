package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import org.ngram.messenger.Share;
import org.ngram.messenger.features.Backup;
import org.ngram.messenger.features.Diagnostics;
import org.ngram.messenger.features.Secure;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Ngram's own data: a backup of its settings as a file, and a report to send along when
 * something goes wrong.
 */
public class DataFragment extends NgramFragment {

    private static final int EXPORT = 1;
    private static final int IMPORT = 2;
    private static final int REPORT = 10;
    private static final int FORGET_CRASH = 11;
    private static final int CLEAR_TEMP = 20;

    private static final int PICK_FILE = 9301;
    private static final int MAX_BACKUP_BYTES = 2 * 1024 * 1024;

    @Override
    protected CharSequence getTitle() {
        return tr("Данные Ngram", "Ngram Data");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(tr("Резервная копия настроек", "Settings Backup")));
        items.add(UItem.asButton(EXPORT, R.drawable.msg_shareout, tr("Сохранить настройки в файл", "Save Settings to a File")));
        items.add(UItem.asButton(IMPORT, R.drawable.msg_download, tr("Загрузить настройки из файла", "Load Settings from a File")));
        items.add(UItem.asShadow(tr(
            "В файл попадают настройки Ngram: оформление, наборы стекла, параметры чатов, шаблоны, провайдеры ассистента. Ключи API в файл не записываются — после загрузки их нужно ввести заново. Аккаунт, переписка и настройки самого Telegram не затрагиваются.",
            "The file holds Ngram's settings: the look, glass sets, chat options, templates, the assistant's providers. API keys are not written into it — enter them again after loading. Your account, your chats and Telegram's own settings are not touched.")));

        items.add(UItem.asHeader(tr("Диагностика", "Diagnostics")));
        items.add(UItem.asButton(REPORT, R.drawable.msg_log, tr("Отчёт", "Report")));
        final boolean crashed = Diagnostics.lastCrash() != null;
        if (crashed) {
            items.add(UItem.asButton(FORGET_CRASH, R.drawable.msg_delete, tr("Забыть последний сбой", "Forget the Last Crash")));
        }
        items.add(UItem.asShadow(tr(
            "Отчёт пригодится, если что-то работает не так: версия, устройство, включённые эффекты, настройки Ngram и последний сбой" + (crashed ? " (он записан)" : "") + ". В нём нет сообщений, имён, номера телефона и ключей; перед копированием он показывается целиком.",
            "The report helps when something goes wrong: the build, the device, the effects in use, Ngram's settings and the last crash" + (crashed ? " (there is one)" : "") + ". It holds no messages, names, phone number or keys, and is shown in full before it is copied.")));

        items.add(UItem.asHeader(tr("Временные файлы", "Temporary Files")));
        items.add(UItem.asButton(CLEAR_TEMP, tr("Очистить", "Clear"), AndroidUtilities.formatFileSize(Diagnostics.tempSize())));
        items.add(UItem.asShadow(tr(
            "Экспортированные переписки, копии настроек и картинки, подготовленные для других приложений. Удаляются сами через сутки.",
            "Exported chats, settings backups and pictures prepared for other apps. They are removed by themselves after a day.")));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case EXPORT:
                export();
                break;
            case IMPORT:
                pickFile();
                break;
            case REPORT:
                InfoSheet.show(this, tr("Диагностика", "Diagnostics"), Diagnostics.report(), tr("Поделиться", "Share"), this::shareReport);
                break;
            case FORGET_CRASH:
                Diagnostics.forgetCrash();
                refresh();
                break;
            case CLEAR_TEMP:
                Diagnostics.clearTemp();
                refresh();
                break;
        }
    }

    private void shareReport() {
        if (getContext() == null) {
            return;
        }
        try {
            final File file = Share.file("ngram-report_" + stamp() + ".txt");
            Share.write(file, Diagnostics.report().text());
            Share.send(getContext(), file, "text/plain", tr("Отчёт Ngram", "Ngram Report"));
        } catch (Throwable e) {
            FileLog.e(e);
            error(e);
        }
    }

    private void export() {
        if (getContext() == null) {
            return;
        }
        final File file;
        try {
            file = Share.file("ngram-settings_" + stamp() + ".json");
            Share.write(file, Backup.export());
        } catch (Throwable e) {
            FileLog.e(e);
            error(e);
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(tr("Файл готов", "The File Is Ready"));
        builder.setMessage(file.getName() + "\n\n" + tr("Ключей API в нём нет.", "It holds no API keys."));
        builder.setPositiveButton(tr("Поделиться", "Share"), (d, w) -> Share.send(getContext(), file, "application/json", tr("Настройки Ngram", "Ngram Settings")));
        builder.setNeutralButton(tr("В «Загрузки»", "To Downloads"), (d, w) ->
            MediaController.saveFile(file.getAbsolutePath(), getContext(), 3, file.getName(), "application/json", uri -> {
                if (getContext() != null) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_save_to_gallery, tr("Сохранено в «Загрузки»", "Saved to Downloads")).show();
                }
            }));
        builder.setNegativeButton(LocaleController.getString(R.string.Close), null);
        showDialog(builder.create());
        refresh();
    }

    private void pickFile() {
        try {
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // File managers disagree on what a .json file is, so every type is offered.
            intent.setType("*/*");
            startActivityForResult(intent, PICK_FILE);
        } catch (Throwable e) {
            FileLog.e(e);
            error(e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_FILE) {
            super.onActivityResultFragment(requestCode, resultCode, data);
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null || getContext() == null) {
            return;
        }
        final Uri uri = data.getData();
        final String json;
        try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IllegalStateException("empty file");
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_BACKUP_BYTES) {
                    throw new IllegalStateException("the file is too large");
                }
            }
            json = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            FileLog.e(e);
            error(e);
            return;
        }
        confirmRestore(json);
    }

    private void confirmRestore(String json) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(tr("Загрузить настройки?", "Load the Settings?"));
        builder.setMessage(tr(
            "Настройки Ngram из файла заменят нынешние. Аккаунт и переписка не затрагиваются.",
            "Ngram's settings from the file will replace the present ones. Your account and chats are not touched."));
        builder.setPositiveButton(tr("Загрузить", "Load"), (d, w) -> {
            try {
                final int applied = Backup.restore(json);
                Secure.refresh();
                rebuildOnLeave = true;
                refresh();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.done, tr("Настроек загружено: ", "Settings loaded: ") + applied).show();
            } catch (Throwable e) {
                FileLog.e(e);
                final AlertDialog.Builder failed = new AlertDialog.Builder(getContext(), getResourceProvider());
                failed.setTitle(tr("Не получилось", "It Did Not Work"));
                failed.setMessage(tr("Это не файл настроек Ngram или он повреждён.", "This is not a Ngram settings file, or it is damaged."));
                failed.setPositiveButton(LocaleController.getString(R.string.OK), null);
                showDialog(failed.create());
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void error(Throwable e) {
        if (getContext() != null) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, tr("Ошибка: ", "Error: ") + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())).show();
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date());
    }
}
