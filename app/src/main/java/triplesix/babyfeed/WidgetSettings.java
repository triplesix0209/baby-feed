package triplesix.babyfeed;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

/** Local, per-widget preferences; never synchronized with feeding records. */
final class WidgetSettings {
    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("widgets", Context.MODE_PRIVATE);
    }

    static int refreshMinutes(Context c, int id) {
        return refreshStep(prefs(c).getInt("refresh-" + id, 5));
    }

    static int refreshStep(int minutes) {
        return minutes < 3 ? 1 : minutes < 8 ? 5 : 10;
    }

    static int transparencyStep(int percent) {
        return Math.round(Math.max(0, Math.min(100, percent)) / 10f) * 10;
    }

    static int transparency(Context c, int id) {
        return transparencyStep(prefs(c).getInt("transparency-" + id, 0));
    }

    static String appearance(Context c, int id) {
        return appearance(prefs(c).getString("appearance-" + id, "system"));
    }

    static String appearance(String mode) {
        return "light".equals(mode) || "dark".equals(mode) ? mode : "system";
    }

    static FeedUi palette(Context c, String mode) {
        Configuration config = new Configuration(c.getResources().getConfiguration());
        int night =
                "system".equals(appearance(mode))
                        ? Resources.getSystem().getConfiguration().uiMode
                                & Configuration.UI_MODE_NIGHT_MASK
                        : "dark".equals(mode)
                                ? Configuration.UI_MODE_NIGHT_YES
                                : Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return new FeedUi(c.createConfigurationContext(config));
    }

    static int backgroundAlpha(int transparency) {
        return Math.round(255 * (100 - Math.max(0, Math.min(100, transparency))) / 100f);
    }

    static long nextRefresh(Context c, int id) {
        return prefs(c).getLong("next-" + id, 0);
    }

    static void setNextRefresh(Context c, int id, long at) {
        prefs(c).edit().putLong("next-" + id, at).apply();
    }

    static void save(Context c, int id, int minutes, int transparency, String appearance) {
        prefs(c).edit()
                .putInt("refresh-" + id, refreshStep(minutes))
                .putInt("transparency-" + id, transparencyStep(transparency))
                .putString("appearance-" + id, appearance(appearance))
                .apply();
    }

    static void remove(Context c, int id) {
        prefs(c).edit()
                .remove("refresh-" + id)
                .remove("transparency-" + id)
                .remove("appearance-" + id)
                .remove("next-" + id)
                .apply();
    }
}
