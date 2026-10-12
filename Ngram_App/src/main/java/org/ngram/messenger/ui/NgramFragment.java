package org.ngram.messenger.ui;

import android.content.Context;
import android.view.View;

import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalFragment;

/**
 * The base of Ngram's settings screens: Telegram's list kit, set up the way its own newer
 * settings screens are (rounded sections, a header that blends into the list, edge to edge).
 */
public abstract class NgramFragment extends UniversalFragment {

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
}
