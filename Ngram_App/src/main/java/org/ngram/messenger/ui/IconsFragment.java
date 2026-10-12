package org.ngram.messenger.ui;

import static org.ngram.messenger.N.tr;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.ngram.core.NgramMotion;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.AppIconsSelectorCell;
import org.telegram.ui.Components.AppIconBulletinLayout;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.LauncherIconController;

import java.util.ArrayList;

/**
 * All of Ngram's app icons on one page, four in a row. Telegram's own settings show the same
 * icons as one long strip; here they can be seen side by side.
 */
public class IconsFragment extends NgramFragment {

    private static final int COLUMNS = 4;
    private static final int ROW_HEIGHT = 108;

    private LinearLayout grid;
    private final ArrayList<View> cells = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return tr("Значок приложения", "App Icon");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final int count = LauncherIconController.LauncherIcon.values().length;
        final int rows = (count + COLUMNS - 1) / COLUMNS;
        items.add(UItem.asCustom(grid(), rows * ROW_HEIGHT + 20));
        items.add(UItem.asShadow(tr(
            "Значок меняется на домашнем экране и в списке приложений. Некоторым оболочкам нужно несколько секунд, а ярлык на рабочем столе иногда приходится поставить заново.",
            "The icon changes on the home screen and in the app list. Some launchers need a few seconds, and a home-screen shortcut sometimes has to be placed again.")));
    }

    private View grid() {
        if (grid != null) {
            return grid;
        }
        final Context context = getContext();
        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(8), dp(12), dp(8), dp(8));
        LinearLayout row = null;
        final LauncherIconController.LauncherIcon[] icons = LauncherIconController.LauncherIcon.values();
        for (int i = 0; i < icons.length; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, ROW_HEIGHT));
            }
            final View cell = cell(context, icons[i]);
            cells.add(cell);
            row.addView(cell, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        }
        for (int i = icons.length; i % COLUMNS != 0; i++) {
            row.addView(new View(context), LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        }
        return grid;
    }

    private View cell(Context context, LauncherIconController.LauncherIcon icon) {
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton, getResourceProvider());
        final FrameLayout cell = new FrameLayout(context) {
            private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (LauncherIconController.isEnabled(icon)) {
                    ring.setStyle(Paint.Style.STROKE);
                    ring.setStrokeWidth(dp(2.5f));
                    ring.setColor(accent);
                    canvas.drawCircle(getWidth() / 2f, dp(6 + 31), dp(34), ring);
                }
            }
        };
        cell.setWillNotDraw(false);

        final AppIconsSelectorCell.AdaptiveIconImageView image = new AppIconsSelectorCell.AdaptiveIconImageView(context);
        image.setImageResource(icon.background);
        image.setForeground(icon.foreground);
        image.setOuterPadding(dp(8));
        image.setBackgroundOuterPadding(dp(36));
        cell.addView(image, LayoutHelper.createFrame(62, 62, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 6, 0, 0));

        final TextView name = new TextView(context);
        name.setText(icon.getTitle());
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()));
        name.setGravity(Gravity.CENTER);
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 2, 76, 2, 0));

        NgramMotion.apply(cell);
        cell.setOnClickListener(v -> {
            if (LauncherIconController.isEnabled(icon)) {
                return;
            }
            LauncherIconController.setIcon(icon);
            for (View other : cells) {
                other.invalidate();
            }
            NgramMotion.pop(image, 0.14f);
            final AppIconBulletinLayout layout = new AppIconBulletinLayout(context, icon, getResourceProvider());
            Bulletin.make(this, layout, Bulletin.DURATION_SHORT).show();
        });
        return cell;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
    }
}
