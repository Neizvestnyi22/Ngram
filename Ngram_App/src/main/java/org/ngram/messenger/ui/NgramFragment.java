package org.ngram.messenger.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalFragment;

/**
 * The base of Ngram's settings screens: Telegram's list kit, set up the way its own newer
 * settings screens are (rounded sections, a header that blends into the list, edge to edge).
 */
public abstract class NgramFragment extends UniversalFragment {

    /**
     * A setting was changed that screens read only while they are built (glass, the size of
     * stickers, the tab bar): the screens under this one are rebuilt once it is closed.
     */
    protected boolean rebuildOnLeave;

    @Override
    public View createView(Context context) {
        super.createView(context);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
        return fragmentView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (listView != null) {
            listView.setPadding(0, 0, 0, bottom);
            listView.setClipToPadding(false);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    /** Rebuilds the list after a setting changed. */
    protected void refresh() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from a screen opened from this one: its values may be shown here.
        refresh();
    }

    @Override
    public void onFragmentDestroy() {
        final INavigationLayout layout = getParentLayout();
        super.onFragmentDestroy();
        if (rebuildOnLeave) {
            // After this screen is gone: the ones under it are built again with the new values.
            AndroidUtilities.runOnUIThread(() -> rebuildScreens(layout), 250);
        }
    }

    protected void rebuildScreens(INavigationLayout layout) {
        rebuildOnLeave = false;
        if (layout != null) {
            try {
                layout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
            } catch (Throwable ignore) {
            }
        }
    }
}
