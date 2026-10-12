package org.ngram.messenger.features;

import static org.ngram.messenger.N.tr;

import org.ngram.core.NgramConfig;
import org.ngram.messenger.ai.Ai;
import org.ngram.messenger.plugins.PluginMenus;
import org.ngram.messenger.ui.FeedFragment;
import org.ngram.messenger.ui.ai.AiChatFragment;
import org.ngram.messenger.ui.ai.AiSettingsFragment;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.LaunchActivity;

/**
 * Ngram's entries in the menu of the main screen (the three dots above the chat list): the
 * shortest way to the assistant, to the feed when it has no tab, and to what plugins add.
 */
public final class MainMenu {

    private MainMenu() {
    }

    public static void fill(ItemOptions options) {
        options.addGap();
        if (!NgramConfig.feedTab) {
            options.add(R.drawable.msg_channel, tr("Лента", "Feed"), () -> open(new FeedFragment(null)));
        }
        options.add(R.drawable.msg_bot, tr("Ассистент", "Assistant"), () -> open(Ai.ready() ? new AiChatFragment() : new AiSettingsFragment()));
        PluginMenus.fillMainMenu(options);
    }

    private static void open(BaseFragment fragment) {
        final BaseFragment last = LaunchActivity.getSafeLastFragment();
        if (last != null) {
            last.presentFragment(fragment);
        }
    }
}
