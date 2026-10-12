package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import org.ngram.messenger.plugins.PluginMenus;
import org.ngram.messenger.plugins.Plugins;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Plugins: the ones installed, installing one from a file or from the examples that come with
 * the app, and the engine's log.
 */
public class PluginsFragment extends NgramFragment {

    public static final String GUIDE = "https://github.com/Neizvestnyi22/Ngram/blob/ngram/docs/plugins.md";

    private static final int INSTALL = 1;
    private static final int EXAMPLES = 2;
    private static final int LOG = 3;
    private static final int GUIDE_ROW = 4;
    private static final int LEAVE_SAFE = 5;
    private static final int FIRST = 100;
    private static final int FIRST_ACTION = 500;
    private static final int PICK_FILE = 9302;
    private static final int MAX_PLUGIN_BYTES = 1024 * 1024;

    private final ArrayList<Plugins.Info> shown = new ArrayList<>();
    private final ArrayList<PluginMenus.Item> actions = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return tr("Плагины", "Plugins");
    }

    @Override
    public boolean onFragmentCreate() {
        Plugins.onChanged = this::refresh;
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        Plugins.onChanged = null;
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        Plugins.onChanged = this::refresh;
        super.onResume();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (Plugins.safeMode()) {
            items.add(UItem.asHeader(tr("Безопасный режим", "Safe Mode")));
            items.add(UItem.asButton(LEAVE_SAFE, R.drawable.msg_retry, tr("Запустить плагины", "Start the Plugins")).accent());
            items.add(UItem.asShadow(tr(
                "Приложение дважды подряд закрылось сразу после запуска с плагинами, поэтому сейчас ни один не загружен. Выключите тот, что поставили последним, и запустите остальные.",
                "The app closed twice in a row right after starting with plugins, so none is loaded now. Switch off the one you installed last, then start the rest.")));
        }

        shown.clear();
        shown.addAll(Plugins.all());
        if (!shown.isEmpty()) {
            items.add(UItem.asHeader(tr("Установленные", "Installed")));
            for (int i = 0; i < shown.size(); i++) {
                final Plugins.Info p = shown.get(i);
                final String state = !p.error.isEmpty() ? tr("ошибка", "error") : p.enabled ? tr("включён", "on") : tr("выключен", "off");
                items.add(UItem.asButton(FIRST + i, p.name, state));
            }
            items.add(UItem.asShadow(null));
        }

        items.add(UItem.asButton(INSTALL, R.drawable.msg_download, tr("Установить из файла", "Install from a File")).accent());
        if (Plugins.examples().length > 0) {
            items.add(UItem.asButton(EXAMPLES, R.drawable.msg_add, tr("Примеры плагинов", "Example Plugins")));
        }
        items.add(UItem.asShadow(tr(
            "Плагин — файл .plugin с программой на Python, которая добавляет в Ngram команды, пункты меню и свои настройки. Его также можно установить из чата: нажмите на сообщение с файлом → «Установить плагин».\n\nПлагин работает внутри приложения с его правами: он может читать то, что видит приложение, и отправлять сообщения от вашего имени. Ставьте только те плагины, авторам которых доверяете.",
            "A plugin is a .plugin file with a Python program that adds commands, menu entries and settings of its own to Ngram. It can also be installed from a chat: tap the message with the file → Install Plugin.\n\nA plugin runs inside the app with the app's rights: it can read what the app sees and send messages as you. Install only plugins whose authors you trust.")));

        actions.clear();
        actions.addAll(PluginMenus.general());
        if (!actions.isEmpty()) {
            items.add(UItem.asHeader(tr("Действия плагинов", "Plugin Actions")));
            for (int i = 0; i < actions.size(); i++) {
                items.add(UItem.asButton(FIRST_ACTION + i, actions.get(i).text));
            }
            items.add(UItem.asShadow(null));
        }

        items.add(UItem.asHeader(tr("Движок", "Engine")));
        items.add(UItem.asButton(LOG, R.drawable.msg_log, tr("Журнал", "Log")));
        items.add(UItem.asButton(GUIDE_ROW, R.drawable.msg_info, tr("Как написать плагин", "How to Write a Plugin")));
        final String engine = Plugins.started() ? Plugins.pythonVersion()
            : Plugins.startError().isEmpty() ? tr("Python запускается, когда включён хотя бы один плагин.", "Python starts once at least one plugin is switched on.")
            : tr("Движок не запустился: ", "The engine did not start: ") + Plugins.startError();
        items.add(UItem.asShadow(engine + "\n" + tr(
            "Имена в SDK повторяют exteraGram. Плагины, которые заменяют методы приложения (hook_method), здесь не работают.",
            "The SDK follows exteraGram's names. Plugins that replace the app's methods (hook_method) do not work here.")));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= FIRST_ACTION && item.id < FIRST_ACTION + actions.size()) {
            PluginMenus.clickGeneral(this, actions.get(item.id - FIRST_ACTION));
            return;
        }
        if (item.id >= FIRST && item.id < FIRST + shown.size()) {
            presentFragment(new PluginFragment(shown.get(item.id - FIRST).id, "", null));
            return;
        }
        switch (item.id) {
            case LEAVE_SAFE:
                Plugins.leaveSafeMode();
                refresh();
                break;
            case INSTALL:
                pickFile();
                break;
            case EXAMPLES:
                chooseExample();
                break;
            case LOG: {
                final String text = Plugins.logText();
                final InfoSheet.Rows rows = new InfoSheet.Rows();
                rows.add(tr("Последние записи", "Latest Entries"), text.isEmpty() ? tr("Журнал пуст.", "The log is empty.") : tail(text, 6000));
                InfoSheet.show(this, tr("Журнал плагинов", "Plugin Log"), rows, tr("Очистить", "Clear"), Plugins::clearLog);
                break;
            }
            case GUIDE_ROW:
                if (getContext() != null) {
                    Browser.openUrl(getContext(), GUIDE);
                }
                break;
        }
    }

    private static String tail(String text, int max) {
        return text.length() <= max ? text : "…" + text.substring(text.length() - max);
    }

    private void chooseExample() {
        final String[] names = Plugins.examples();
        if (getContext() == null || names.length == 0) {
            return;
        }
        final CharSequence[] titles = new CharSequence[names.length];
        for (int i = 0; i < names.length; i++) {
            titles[i] = names[i].replace(".plugin", "").replace(".py", "").replace('_', ' ');
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(tr("Примеры плагинов", "Example Plugins"));
        builder.setItems(titles, (dialog, which) -> {
            try {
                offerInstall(this, Plugins.exampleFile(names[which]));
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void pickFile() {
        try {
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_FILE);
        } catch (Throwable e) {
            FileLog.e(e);
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
        try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return;
            }
            final File copy = new File(getContext().getCacheDir(), "picked.plugin");
            AndroidUtilities.copyFile(in, copy);
            if (copy.length() > MAX_PLUGIN_BYTES) {
                copy.delete();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, tr("Файл слишком большой для плагина.", "The file is too large to be a plugin.")).show();
                return;
            }
            offerInstall(this, copy);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ---- installing, from this screen or from a chat -----------------------------------

    /** Shows what the file is and installs it once the user agrees. */
    public static void offerInstall(BaseFragment fragment, File file) {
        if (fragment.getContext() == null) {
            return;
        }
        final AlertDialog progress = new AlertDialog(fragment.getContext(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.showDelayed(300);
        Plugins.ensureStarted(startError -> {
            progress.dismiss();
            if (fragment.getContext() == null) {
                return;
            }
            if (startError != null) {
                failed(fragment, tr("Движок плагинов не запустился.", "The plugin engine did not start."), startError);
                return;
            }
            final Plugins.Info info = Plugins.peek(file);
            if (info == null) {
                failed(fragment, tr("Это не плагин.", "This is not a plugin."),
                    tr("В файле нет строки __id__ = \"…\" с именем плагина.", "The file has no __id__ = \"…\" line naming the plugin."));
                return;
            }
            final boolean update = Plugins.find(info.id) != null;
            final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext(), fragment.getResourceProvider());
            builder.setTitle(update ? tr("Обновить плагин?", "Update the Plugin?") : tr("Установить плагин?", "Install the Plugin?"));
            final StringBuilder text = new StringBuilder();
            text.append(info.name);
            if (!info.version.isEmpty()) {
                text.append(" ").append(info.version);
            }
            if (!info.author.isEmpty()) {
                text.append("\n").append(tr("Автор: ", "Author: ")).append(info.author);
            }
            if (!info.description.isEmpty()) {
                text.append("\n\n").append(info.description);
            }
            text.append("\n\n").append(tr(
                "Плагин — это программа (" + info.lines + " строк), которая будет работать внутри Ngram с доступом к вашему аккаунту в приложении. Устанавливайте, только если доверяете автору.",
                "A plugin is a program (" + info.lines + " lines) that will run inside Ngram with access to your account in the app. Install it only if you trust its author."));
            builder.setMessage(text);
            builder.setPositiveButton(update ? tr("Обновить", "Update") : tr("Установить", "Install"), (d, w) -> {
                final Plugins.Info[] installed = new Plugins.Info[1];
                Plugins.install(file, installed, error -> {
                    if (fragment.getContext() == null) {
                        return;
                    }
                    if (error != null) {
                        failed(fragment, tr("Плагин не установлен.", "The plugin was not installed."), error);
                        return;
                    }
                    fragment.presentFragment(new PluginFragment(installed[0].id, "", null));
                });
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            fragment.showDialog(builder.create());
        });
    }

    static void failed(BaseFragment fragment, String title, String details) {
        if (fragment.getContext() == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext(), fragment.getResourceProvider());
        builder.setTitle(title);
        builder.setMessage(details == null ? "" : details.length() > 1500 ? "…" + details.substring(details.length() - 1500) : details);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        fragment.showDialog(builder.create());
    }
}
