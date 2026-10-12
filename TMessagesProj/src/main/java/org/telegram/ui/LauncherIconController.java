package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconDefault),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_6_foreground_sa, R.string.AppIconVintage),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua),
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium), // Ngram: its own artwork in every slot, so no slot is a Premium perk
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_5_foreground_sa, R.string.AppIconTurbo),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox),
        // Ngram: more icons of its own; their names come from Ngram and not from Telegram's texts.
        NG_GLASS("NgGlassIcon", R.drawable.ngram_ic_glass_bg, R.drawable.ngram_ic_glass_fg, 0),
        NG_TERMINAL("NgTerminalIcon", R.drawable.ngram_ic_terminal_bg, R.drawable.ngram_ic_terminal_fg, 0),
        NG_PIXEL("NgPixelIcon", R.drawable.ngram_ic_pixel_bg, R.drawable.ngram_ic_pixel_fg, 0),
        NG_RETRO("NgRetroIcon", R.drawable.ngram_ic_retro_bg, R.drawable.ngram_ic_retro_fg, 0),
        NG_SYNTH("NgSynthIcon", R.drawable.ngram_ic_synth_bg, R.drawable.ngram_ic_synth_fg, 0),
        NG_BRAT("NgBratIcon", R.drawable.ngram_ic_brat_bg, R.drawable.ngram_ic_brat_fg, 0),
        NG_SHADES("NgShadesIcon", R.drawable.ngram_ic_shades_bg, R.drawable.ngram_ic_shades_fg, 0),
        NG_RAINBOW("NgRainbowIcon", R.drawable.ngram_ic_rainbow_bg, R.drawable.ngram_ic_rainbow_fg, 0),
        NG_STONKS("NgStonksIcon", R.drawable.ngram_ic_stonks_bg, R.drawable.ngram_ic_stonks_fg, 0),
        NG_CAT("NgCatIcon", R.drawable.ngram_ic_cat_bg, R.drawable.ngram_ic_cat_fg, 0);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;

        private ComponentName componentName;

        /** Ngram: the name shown under the icon. */
        public String getTitle() {
            final String own = org.ngram.core.NgramHooks.iconTitle(key);
            if (own != null) {
                return own;
            }
            return title == 0 ? key : org.telegram.messenger.LocaleController.getString(title);
        }

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
        }
    }
}
