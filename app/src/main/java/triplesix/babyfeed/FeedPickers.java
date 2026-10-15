package triplesix.babyfeed;

import android.app.*;
import android.content.Context;
import android.view.*;
import android.widget.Button;

/** Native calendar/clock behavior with the same palette and rounded surface as the app. */
final class FeedPickers {
    static int theme(Context c) {
        return Appearance.dark(c) ? R.style.FeedPicker_Dark : R.style.FeedPicker;
    }

    static final class DateDialog extends DatePickerDialog {
        DateDialog(Context c, OnDateSetListener selected, int year, int month, int day) {
            super(c, theme(c), selected, year, month, day);
        }

        public void show() {
            super.show();
            style(this);
        }
    }

    static final class TimeDialog extends TimePickerDialog {
        TimeDialog(Context c, OnTimeSetListener selected, int hour, int minute, boolean is24Hour) {
            super(c, theme(c), selected, hour, minute, is24Hour);
        }

        public void show() {
            super.show();
            style(this);
        }
    }

    static void style(AlertDialog dialog) {
        FeedUi ui = new FeedUi(dialog.getContext());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(ui.shape(ui.SURFACE, 26, false));
            window.getDecorView().setPadding(0, 0, 0, 0);
            window.getDecorView().setClipToOutline(true);
            window.setLayout(
                    Math.min(
                            ui.c.getResources().getDisplayMetrics().widthPixels - ui.dp(32),
                            ui.dp(360)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        if (window != null) styleText(window.getDecorView(), ui);
        styleButton(
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
                I18n.text(ui.c, R.string.cancel_19),
                false,
                ui);
        styleButton(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                I18n.text(ui.c, R.string.confirm_20),
                true,
                ui);
    }

    static void styleText(View view, FeedUi ui) {
        if (view instanceof android.widget.TextView)
            ((android.widget.TextView) view)
                    .setTextColor(
                            new android.content.res.ColorStateList(
                                    new int[][] {
                                        new int[] {-android.R.attr.state_enabled},
                                        new int[] {android.R.attr.state_activated},
                                        new int[] {}
                                    },
                                    new int[] {ui.MUTED, ui.ACCENT, ui.INK}));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleText(group.getChildAt(i), ui);
        }
    }

    static void styleButton(Button button, String text, boolean primary, FeedUi ui) {
        if (button == null) return;
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(primary ? ui.ON_ACCENT : ui.ACCENT);
        button.setBackground(ui.shape(primary ? ui.ACCENT : ui.SURFACE, 16, !primary));
        button.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
        button.setMinHeight(ui.dp(48));
        button.setMinimumHeight(ui.dp(48));
        ViewGroup.LayoutParams params = button.getLayoutParams();
        params.height = ui.dp(48);
        if (primary && params instanceof ViewGroup.MarginLayoutParams)
            ((ViewGroup.MarginLayoutParams) params).setMarginStart(ui.dp(12));
        button.setLayoutParams(params);
    }
}
