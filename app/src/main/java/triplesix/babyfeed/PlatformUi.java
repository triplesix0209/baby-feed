package triplesix.babyfeed;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/** Modern window APIs with narrowly scoped fallbacks for Android 8–10. */
final class PlatformUi {
    static void applySystemInsets(View view) {
        view.setOnApplyWindowInsetsListener(
                (target, insets) -> {
                    if (Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Insets bars =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        target.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    } else {
                        applyLegacyInsets(target, insets);
                    }
                    return insets;
                });
    }

    @SuppressWarnings("deprecation") // WindowInsets.getInsets is unavailable before Android 11.
    private static void applyLegacyInsets(View target, WindowInsets insets) {
        target.setPadding(
                insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
    }

    static void styleBars(Window window, int background, boolean dark) {
        if (Build.VERSION.SDK_INT < 35) {
            setLegacyBarColors(window, background);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            // PhoneWindow requires its decor to exist even before setContentView is called.
            window.getDecorView();
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int lightBars =
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(dark ? 0 : lightBars, lightBars);
            }
        } else {
            setLegacyBarAppearance(window, dark);
        }
    }

    @SuppressWarnings(
            "deprecation") // Pre-Android 15 windows still support opaque system bar colors.
    private static void setLegacyBarColors(Window window, int background) {
        window.setStatusBarColor(background);
        window.setNavigationBarColor(background);
    }

    @SuppressWarnings("deprecation") // WindowInsetsController is unavailable before Android 11.
    private static void setLegacyBarAppearance(Window window, boolean dark) {
        window.getDecorView()
                .setSystemUiVisibility(
                        dark
                                ? 0
                                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    static void showAlarmWhenLocked(Activity activity) {
        if (Build.VERSION.SDK_INT >= 27) {
            activity.setShowWhenLocked(true);
            activity.setTurnScreenOn(true);
        } else {
            showLegacyAlarm(activity.getWindow());
        }
    }

    @SuppressWarnings("deprecation") // Android 8.0 has no Activity methods for lock-screen alarms.
    private static void showLegacyAlarm(Window window) {
        window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    @SuppressWarnings(
            "deprecation") // Floating dialogs retain resize mode to keep their scrollable form
    // above the IME.
    static void resizeDialogForKeyboard(Window window) {
        window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
}
