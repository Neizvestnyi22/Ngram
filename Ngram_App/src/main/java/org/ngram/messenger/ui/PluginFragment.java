package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;

import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;
import org.ngram.messenger.plugins.Plugins;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/**
 * The page of one plugin: what it is, the switch that turns it on, the settings it declares
 * and what it has registered in the app. A sub-page of its settings is the same screen opened
 * with a path.
 */
public class PluginFragment extends NgramFragment {

    private static final int ENABLED = 1;
    private static final int SHOW_ERROR = 2;
    private static final int RESET = 3;
    private static final int REMOVE = 4;
    private static final int FIRST_SETTING = 1000;

    private final String pluginId;
    /** The trail of sub-page rows that leads here, e.g. "3/0"; empty for the plugin's own page. */
    private final String path;
    private final String title;
    private boolean busy;
    private JSONArray rows = new JSONArray();

    public PluginFragment(String pluginId, String path, String title) {
        this.pluginId = pluginId;
        this.path = path == null ? "" : path;
        this.title = title;
    }

    private Plugins.Info plugin() {
        return Plugins.find(pluginId);
    }

    @Override
    protected CharSequence getTitle() {
        if (title != null) {
            return title;
        }
        final Plugins.Info p = plugin();
        return p == null ? tr("Плагин", "Plugin") : p.name;
    }

    @Override
    public boolean onFragmentCreate() {
        Plugins.onChanged = this::refresh;
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        Plugins.onChanged = this::refresh;
        super.onResume();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final Plugins.Info p = plugin();
        if (p == null) {
            return;
        }
        final boolean root = path.isEmpty();
        if (root) {
            items.add(UItem.asHeader(tr("Плагин", "Plugin")));
            items.add(UItem.asCheck(ENABLED, busy ? tr("Подождите…", "Please wait…") : tr("Включён", "Switched On")).setChecked(p.enabled));
            if (!p.version.isEmpty()) {
                items.add(UItem.asButton(0, tr("Версия", "Version"), p.version));
            }
            if (!p.author.isEmpty()) {
                items.add(UItem.asButton(0, tr("Автор", "Author"), p.author));
            }
            if (!p.error.isEmpty()) {
                items.add(UItem.asButton(SHOW_ERROR, R.drawable.msg_info, tr("Показать ошибку", "Show the Error")).red());
            }
            final String note = !p.error.isEmpty()
                ? tr("Плагин выключен из-за ошибки. ", "The plugin is off because of an error. ")
                : "";
            items.add(UItem.asShadow(note + (p.description.isEmpty() ? tr("Автор не оставил описания.", "The author left no description.") : p.description)));
        }

        rows = p.loaded ? Plugins.settings(pluginId, path) : new JSONArray();
        boolean open = false;
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                continue;
            }
            final int id = FIRST_SETTING + i;
            final String text = row.optString("text");
            final String subtext = row.optString("subtext");
            switch (row.optString("type")) {
                case "header":
                    items.add(UItem.asHeader(text));
                    open = true;
                    break;
                case "divider":
                    items.add(UItem.asShadow(text.isEmpty() ? null : text));
                    open = false;
                    break;
                case "switch":
                    items.add(UItem.asCheck(id, text).setChecked(row.optBoolean("value", row.optBoolean("default"))));
                    open = true;
                    break;
                case "selector": {
                    final JSONArray options = row.optJSONArray("items");
                    final int value = row.optInt("value", row.optInt("default"));
                    final String current = options != null && value >= 0 && value < options.length() ? options.optString(value) : "";
                    items.add(UItem.asButton(id, text, current));
                    open = true;
                    break;
                }
                case "input":
                case "edit": {
                    final String value = row.optString("value", row.optString("default"));
                    final String shownValue = value.replace('\n', ' ');
                    items.add(UItem.asButton(id, text, shownValue.length() > 18 ? shownValue.substring(0, 18) + "…" : shownValue));
                    open = true;
                    break;
                }
                case "text": {
                    UItem item = subtext.isEmpty() ? UItem.asButton(id, text) : UItem.asButton(id, text, subtext.length() > 18 ? subtext.substring(0, 18) + "…" : subtext);
                    if (row.optBoolean("red")) {
                        item = item.red();
                    } else if (row.optBoolean("accent")) {
                        item = item.accent();
                    }
                    items.add(item);
                    open = true;
                    break;
                }
                default:
                    // A row made of Telegram views that the plugin builds itself: not shown here.
                    break;
            }
        }
        if (open) {
            items.add(UItem.asShadow(null));
        }

        if (root && p.loaded) {
            final JSONObject about = Plugins.describe(pluginId);
            final StringBuilder sb = new StringBuilder();
            final JSONArray commands = about.optJSONArray("commands");
            for (int i = 0; commands != null && i < commands.length(); i++) {
                final JSONArray command = commands.optJSONArray(i);
                if (command != null) {
                    sb.append(command.optString(0));
                    if (!command.optString(1).isEmpty()) {
                        sb.append(" — ").append(command.optString(1));
                    }
                    sb.append('\n');
                }
            }
            final JSONArray hooks = about.optJSONArray("hooks");
            if (hooks != null && hooks.length() > 0) {
                sb.append(tr("Следит за: ", "Watches: "));
                for (int i = 0; i < hooks.length(); i++) {
                    sb.append(i == 0 ? "" : ", ").append(hooks.optString(i));
                }
                sb.append('\n');
            }
            if (about.optInt("menu") > 0) {
                sb.append(tr("Пунктов меню: ", "Menu entries: ")).append(about.optInt("menu")).append('\n');
            }
            if (about.optInt("timers") > 0) {
                sb.append(tr("Таймеров: ", "Timers: ")).append(about.optInt("timers")).append('\n');
            }
            if (sb.length() > 0) {
                items.add(UItem.asShadow(tr("Что добавляет плагин:\n", "What the plugin adds:\n") + sb.toString().trim()));
            }
        }

        if (root) {
            if (p.loaded && rows.length() > 0) {
                items.add(UItem.asButton(RESET, R.drawable.msg_retry, tr("Сбросить настройки плагина", "Reset the Plugin's Settings")));
            }
            items.add(UItem.asButton(REMOVE, R.drawable.msg_delete, tr("Удалить плагин", "Remove the Plugin")).red());
            items.add(UItem.asShadow(tr("Файл: ", "File: ") + p.file.getName() + tr(", строк: ", ", lines: ") + p.lines));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final Plugins.Info p = plugin();
        if (p == null || getContext() == null) {
            return;
        }
        if (item.id >= FIRST_SETTING) {
            clickSetting(item.id - FIRST_SETTING);
            return;
        }
        switch (item.id) {
            case ENABLED:
                if (busy) {
                    return;
                }
                busy = true;
                refresh();
                Plugins.setEnabled(p, !p.enabled, error -> {
                    busy = false;
                    refresh();
                    if (error != null) {
                        PluginsFragment.failed(this, tr("Плагин не запустился.", "The plugin did not start."), error);
                    }
                });
                break;
            case SHOW_ERROR:
                PluginsFragment.failed(this, tr("Ошибка плагина", "Plugin Error"), p.error);
                break;
            case RESET:
                Plugins.resetSettings(pluginId);
                refresh();
                break;
            case REMOVE: {
                final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
                builder.setTitle(tr("Удалить плагин?", "Remove the Plugin?"));
                builder.setMessage(tr("Его файл, настройки и данные будут стёрты с устройства.", "Its file, settings and data will be erased from the device."));
                builder.setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> Plugins.remove(p, error -> finishFragment()));
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                final AlertDialog dialog = builder.create();
                showDialog(dialog);
                dialog.redPositive();
                break;
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= FIRST_SETTING) {
            final JSONObject row = rows.optJSONObject(item.id - FIRST_SETTING);
            if (row != null && row.optBoolean("long")) {
                Plugins.clickSetting(pluginId, path, item.id - FIRST_SETTING, true);
                return true;
            }
        }
        return false;
    }

    private void clickSetting(int index) {
        final JSONObject row = rows.optJSONObject(index);
        if (row == null) {
            return;
        }
        final String text = row.optString("text");
        switch (row.optString("type")) {
            case "switch":
                Plugins.setSetting(pluginId, path, index, !row.optBoolean("value", row.optBoolean("default")));
                refresh();
                break;
            case "selector": {
                final JSONArray options = row.optJSONArray("items");
                if (options == null || options.length() == 0) {
                    return;
                }
                final CharSequence[] names = new CharSequence[options.length()];
                final int value = row.optInt("value", row.optInt("default"));
                for (int i = 0; i < names.length; i++) {
                    names[i] = (i == value ? "✓  " : "     ") + options.optString(i);
                }
                final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
                builder.setTitle(text);
                builder.setItems(names, (dialog, which) -> {
                    Plugins.setSetting(pluginId, path, index, which);
                    refresh();
                });
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                showDialog(builder.create());
                break;
            }
            case "input":
            case "edit": {
                final String subtext = row.optString("subtext");
                Prompt.show(this, text, subtext.isEmpty() ? null : subtext, text, row.optString("value", row.optString("default")),
                    row.optBoolean("multiline") ? Prompt.MULTILINE : Prompt.EXACT, value -> {
                        final int max = row.optInt("max");
                        Plugins.setSetting(pluginId, path, index, max > 0 && value.length() > max ? value.substring(0, max) : value);
                        refresh();
                    });
                break;
            }
            case "text":
                if (row.optBoolean("sub")) {
                    presentFragment(new PluginFragment(pluginId, path.isEmpty() ? String.valueOf(index) : path + "/" + index, text));
                } else if (row.optBoolean("clickable")) {
                    Plugins.clickSetting(pluginId, path, index, false);
                }
                break;
        }
    }
}
