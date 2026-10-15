package triplesix.babyfeed;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;

/** Appearance is a local preference; system mode follows configuration changes. */
final class Appearance {
    static String mode(Context c) {
        String mode = c.getSharedPreferences("settings", 0).getString("appearance", "system");
        return "light".equals(mode) || "dark".equals(mode) ? mode : "system";
    }

    static void configure(Context c, Configuration config) {
        String mode = mode(c);
        if (!mode.equals("system"))
            config.uiMode =
                    (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                            | ("dark".equals(mode)
                                    ? Configuration.UI_MODE_NIGHT_YES
                                    : Configuration.UI_MODE_NIGHT_NO);
    }

    static boolean dark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static void applyTheme(Activity activity) {
        activity.setTheme(dark(activity) ? R.style.AppTheme_Dark : R.style.AppTheme);
    }

    static void styleWindow(Activity activity, FeedUi ui) {
        PlatformUi.styleBars(activity.getWindow(), ui.BG, ui.dark);
        activity.getWindow().setBackgroundDrawable(new ColorDrawable(ui.BG));
    }
}
