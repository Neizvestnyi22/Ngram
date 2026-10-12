package org.ngram.messenger.features;

import static org.ngram.messenger.N.tr;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.ngram.core.NgramConfig;
import org.ngram.messenger.Opt;

import java.util.ArrayList;

/**
 * Named sets of the glass settings: a few that come with Ngram and the ones the user saves.
 * Applying a set writes its values into the ordinary settings, so the sliders show them.
 */
public final class LookPresets {

    private static final String KEY = "lookPresets";
    public static final int LIMIT = 12;

    public static final class Preset {
        public final String name;
        public final boolean builtIn;
        public float glassTint, glassBlur, frostBlur, refraction, rim, glassColorStrength;
        public int glassColor;

        Preset(String name, boolean builtIn, float glassTint, float glassBlur, float frostBlur, float refraction, float rim) {
            this.name = name;
            this.builtIn = builtIn;
            this.glassTint = glassTint;
            this.glassBlur = glassBlur;
            this.frostBlur = frostBlur;
            this.refraction = refraction;
            this.rim = rim;
            this.glassColorStrength = 0.25f;
        }

        /** A one-line description for the list. */
        public String summary() {
            return Math.round(glassTint * 100) + "% · " + Math.round(glassBlur * 100) + "% · " + Math.round(frostBlur * 100) + "% · "
                + Math.round(refraction * 100) + "% · " + Math.round(rim * 100) + "%" + (glassColor != 0 ? tr(" · с оттенком", " · tinted") : "");
        }

        public boolean isCurrent() {
            return near(glassTint, NgramConfig.glassTint) && near(glassBlur, NgramConfig.glassBlur) && near(frostBlur, NgramConfig.frostBlur)
                && near(refraction, NgramConfig.refraction) && near(rim, NgramConfig.rim) && glassColor == NgramConfig.glassColor
                && (glassColor == 0 || near(glassColorStrength, NgramConfig.glassColorStrength));
        }
    }

    private LookPresets() {
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.006f;
    }

    public static ArrayList<Preset> builtIn() {
        final ArrayList<Preset> list = new ArrayList<>();
        list.add(new Preset("Ngram", true, NgramConfig.NGRAM_TINT, 1f, 1f, NgramConfig.NGRAM_REFRACTION, NgramConfig.NGRAM_RIM));
        list.add(new Preset("Telegram", true, 1f, 1f, 1f, 1f, 1f));
        list.add(new Preset(tr("Чистая линза", "Clean Lens"), true, 0.45f, 0f, 0.6f, 1.8f, 1.6f));
        list.add(new Preset(tr("Матовое", "Frosted"), true, 1.15f, 2.2f, 1.4f, 0.6f, 0.8f));
        list.add(new Preset(tr("Лёгкое", "Light"), true, 0.6f, 0.5f, 0.8f, 1f, 0.7f));
        return list;
    }

    public static ArrayList<Preset> saved() {
        final ArrayList<Preset> list = new ArrayList<>();
        try {
            final String json = Opt.text(KEY);
            if (!json.isEmpty()) {
                final JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    final JSONObject o = array.getJSONObject(i);
                    final Preset p = new Preset(o.optString("name", "?"), false,
                        (float) o.optDouble("glassTint", 1), (float) o.optDouble("glassBlur", 1), (float) o.optDouble("frostBlur", 1),
                        (float) o.optDouble("refraction", 1), (float) o.optDouble("rim", 1));
                    p.glassColor = o.optInt("glassColor", 0);
                    p.glassColorStrength = (float) o.optDouble("glassColorStrength", 0.25);
                    list.add(p);
                }
            }
        } catch (Throwable ignore) {
        }
        return list;
    }

    private static void store(ArrayList<Preset> list) {
        final JSONArray array = new JSONArray();
        try {
            for (Preset p : list) {
                array.put(new JSONObject()
                    .put("name", p.name)
                    .put("glassTint", p.glassTint).put("glassBlur", p.glassBlur).put("frostBlur", p.frostBlur)
                    .put("refraction", p.refraction).put("rim", p.rim)
                    .put("glassColor", p.glassColor).put("glassColorStrength", p.glassColorStrength));
            }
        } catch (Throwable ignore) {
        }
        Opt.text(KEY, array.toString());
    }

    /** Saves the glass as it is now under a name; a set with the same name is replaced. */
    public static void saveCurrent(String name) {
        final ArrayList<Preset> list = saved();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).name.equalsIgnoreCase(name)) {
                list.remove(i);
            }
        }
        final Preset p = new Preset(name, false, NgramConfig.glassTint, NgramConfig.glassBlur, NgramConfig.frostBlur, NgramConfig.refraction, NgramConfig.rim);
        p.glassColor = NgramConfig.glassColor;
        p.glassColorStrength = NgramConfig.glassColorStrength;
        list.add(p);
        while (list.size() > LIMIT) {
            list.remove(0);
        }
        store(list);
    }

    public static void remove(String name) {
        final ArrayList<Preset> list = saved();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).name.equals(name)) {
                list.remove(i);
            }
        }
        store(list);
    }

    public static void apply(Preset p) {
        final SharedPreferences prefs = NgramConfig.prefs();
        if (prefs == null) {
            return;
        }
        prefs.edit()
            .putFloat("glassTint", p.glassTint).putFloat("glassBlur", p.glassBlur).putFloat("frostBlur", p.frostBlur)
            .putFloat("refraction", p.refraction).putFloat("rim", p.rim)
            .putInt("glassColor", p.glassColor).putFloat("glassColorStrength", p.glassColorStrength)
            .apply();
        NgramConfig.reload();
    }
}
