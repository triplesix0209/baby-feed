package triplesix.babyfeed;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared native styling, all dimensions in dp and type in sp. */
final class FeedUi {
    final int BG,
            SURFACE,
            SOFT,
            ACCENT,
            INK,
            MUTED,
            LINE,
            GREEN,
            GREEN_SOFT,
            RED,
            RED_SOFT,
            ON_ACCENT;
    final boolean dark;
    final Context c;

    FeedUi(Context context) {
        c = context;
        dark = Appearance.dark(c);
        BG = dark ? 0xFF11151D : 0xFFF7F9FC;
        SURFACE = dark ? 0xFF1D2430 : Color.WHITE;
        SOFT = dark ? 0xFF243650 : 0xFFE6F0FD;
        ACCENT = dark ? 0xFF9DC2FF : 0xFF2D69CB;
        INK = dark ? 0xFFF0F2F7 : 0xFF313037;
        MUTED = dark ? 0xFFABB5C6 : 0xFF777178;
        LINE = dark ? 0xFF354152 : 0xFFCFDDF2;
        GREEN = dark ? 0xFF86D9AE : 0xFF268359;
        GREEN_SOFT = dark ? 0xFF1C3A30 : 0xFFE3F3E9;
        RED = dark ? 0xFFFFA3A6 : 0xFFBD3F46;
        RED_SOFT = dark ? 0xFF432A32 : 0xFFF7E2E1;
        ON_ACCENT = dark ? 0xFF14223A : Color.WHITE;
    }

    int dp(float n) {
        return Math.round(n * c.getResources().getDisplayMetrics().density);
    }

    GradientDrawable shape(int color, int radius, boolean border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (border) d.setStroke(dp(1), LINE);
        return d;
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setFontFeatureSettings("tnum");
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        t.setLineSpacing(dp(3), 1);
        return t;
    }

    TextView text(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView t = label(value, size, color, bold);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(8);
        parent.addView(t, p);
        return t;
    }

    LinearLayout card(LinearLayout parent, int color) {
        LinearLayout l = column();
        l.setPadding(dp(20), dp(20), dp(20), dp(20));
        l.setBackground(shape(color, 26, false));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(16);
        parent.addView(l, p);
        return l;
    }

    void gap(LinearLayout parent, int h) {
        View v = new View(c);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
    }

    TextView pill(String value) {
        TextView t = label(value, 12, ACCENT, true);
        t.setPadding(dp(12), dp(7), dp(12), dp(7));
        t.setBackground(shape(SOFT, 30, false));
        return t;
    }

    Button button(LinearLayout parent, String value, boolean primary, Runnable action) {
        Button b = new Button(c);
        b.setText(value);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setAllCaps(false);
        b.setTextColor(primary ? ON_ACCENT : ACCENT);
        b.setMinHeight(dp(50));
        b.setMinimumHeight(dp(50));
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable base = shape(primary ? ACCENT : SURFACE, 18, !primary);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), base, null));
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setTranslationZ(0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        parent.addView(b, p);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    void redOutline(Button button) {
        button.setTextColor(RED);
        GradientDrawable background = shape(SURFACE, 18, false);
        background.setStroke(dp(1), RED);
        button.setBackground(
                new RippleDrawable(ColorStateList.valueOf(0x22E5696C), background, null));
    }

    Button iconButton(
            LinearLayout parent, String label, boolean primary, int icon, Runnable action) {
        Button button = button(parent, label, primary, action);
        Drawable pencil = c.getDrawable(icon).mutate();
        pencil.setTint(primary ? ON_ACCENT : ACCENT);
        pencil.setBounds(0, 0, dp(18), dp(18));
        android.text.SpannableString text = new android.text.SpannableString("\uFFFC  " + label);
        text.setSpan(
                new CenteredIconSpan(pencil), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(text);
        button.setContentDescription(label);
        return button;
    }

    static final class CenteredIconSpan extends android.text.style.ImageSpan {
        CenteredIconSpan(Drawable icon) {
            super(icon);
        }

        public int getSize(
                Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
            if (metrics != null) {
                Paint.FontMetricsInt font = paint.getFontMetricsInt();
                metrics.top = font.top;
                metrics.ascent = font.ascent;
                metrics.descent = font.descent;
                metrics.bottom = font.bottom;
                metrics.leading = font.leading;
            }
            return getDrawable().getBounds().width();
        }

        public void draw(
                Canvas canvas,
                CharSequence text,
                int start,
                int end,
                float x,
                int top,
                int baseline,
                int bottom,
                Paint paint) {
            Drawable icon = getDrawable();
            Paint.FontMetricsInt font = paint.getFontMetricsInt();
            float y = baseline + (font.ascent + font.descent - icon.getBounds().height()) / 2f;
            canvas.save();
            canvas.translate(x, y);
            icon.draw(canvas);
            canvas.restore();
        }
    }

    void yellowOutline(Button button) {
        int amber = Color.rgb(139, 88, 12);
        button.setTextColor(amber);
        GradientDrawable background = shape(Color.rgb(255, 250, 235), 18, false);
        background.setStroke(dp(1), Color.rgb(226, 183, 76));
        button.setBackground(
                new RippleDrawable(ColorStateList.valueOf(0x22D9A025), background, null));
    }

    void detail(LinearLayout parent, String name, String value) {
        LinearLayout r = row();
        TextView a = label(name, 15, MUTED, false), b = label(value, 16, INK, true);
        r.addView(a, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(b);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(10);
        parent.addView(r, p);
    }

    void section(LinearLayout parent, String title) {
        TextView label = text(parent, title, 14, MUTED, true);
        label.setPadding(dp(4), dp(18), 0, dp(5));
    }

    GradientDrawable settingsBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[] {SOFT, BG, BG});
    }

    android.app.AlertDialog popup(android.app.Activity activity, View content) {
        content.setBackground(shape(SURFACE, 26, false));
        content.setClipToOutline(true);
        android.app.AlertDialog popup = new android.app.AlertDialog.Builder(activity).create();
        popup.setView(content, 0, 0, 0, 0);
        popup.setCanceledOnTouchOutside(true);
        popup.show();
        Window window = popup.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.getDecorView().setPadding(0, 0, 0, 0);
            window.setLayout(
                    Math.min(c.getResources().getDisplayMetrics().widthPixels - dp(40), dp(480)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return popup;
    }

    ScrollView popupScroll() {
        return new ScrollView(c) {
            protected void onMeasure(int width, int height) {
                int cap = Math.round(getResources().getDisplayMetrics().heightPixels * .68f);
                if (MeasureSpec.getMode(height) != MeasureSpec.UNSPECIFIED)
                    cap = Math.min(cap, MeasureSpec.getSize(height));
                super.onMeasure(width, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
            }
        };
    }

    void popupHeader(LinearLayout parent, String title, int icon, Runnable dismiss) {
        LinearLayout header = row();
        header.setPadding(0, 0, 0, dp(16));
        ImageView symbol = new ImageView(c);
        symbol.setImageResource(icon);
        symbol.setImageTintList(ColorStateList.valueOf(ACCENT));
        symbol.setPadding(dp(9), dp(9), dp(9), dp(9));
        symbol.setBackground(shape(SOFT, 14, false));
        symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconSize.rightMargin = dp(12);
        header.addView(symbol, iconSize);
        header.addView(label(title, 20, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = label("×", 26, MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(I18n.text(c, R.string.close_11));
        close.setBackground(shape(BG, 14, false));
        header.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        close.setOnClickListener(v -> dismiss.run());
        parent.addView(header);
    }

    android.app.AlertDialog choicePopup(
            android.app.Activity activity,
            String title,
            int icon,
            String[] labels,
            int selected,
            java.util.function.IntConsumer choose) {
        LinearLayout content = column();
        content.setPadding(dp(20), dp(20), dp(20), dp(12));
        android.app.AlertDialog[] dialog = {null};
        popupHeader(content, title, icon, () -> dialog[0].dismiss());
        RadioGroup group = new RadioGroup(c);
        content.addView(group);
        for (int n = 0; n < labels.length; n++) {
            final int index = n;
            RadioButton option = new RadioButton(c);
            option.setId(View.generateViewId());
            option.setText(labels[n]);
            option.setTextSize(16);
            option.setTextColor(INK);
            option.setButtonTintList(ColorStateList.valueOf(ACCENT));
            option.setPadding(dp(12), dp(12), dp(12), dp(12));
            option.setMinHeight(dp(56));
            option.setBackgroundColor(Color.TRANSPARENT);
            RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(-1, -2);
            params.bottomMargin = dp(8);
            group.addView(option, params);
            option.setChecked(n == selected);
            option.setOnClickListener(
                    v -> {
                        dialog[0].dismiss();
                        choose.accept(index);
                    });
        }
        dialog[0] = popup(activity, content);
        return dialog[0];
    }

    android.app.AlertDialog confirmationPopup(
            android.app.Activity activity,
            String title,
            int icon,
            String message,
            String confirm,
            Runnable accept) {
        return confirmationPopup(
                activity, title, icon, message, confirm, I18n.text(c, R.string.cancel_19), accept);
    }

    android.app.AlertDialog confirmationPopup(
            android.app.Activity activity,
            String title,
            int icon,
            String message,
            String confirm,
            String cancelLabel,
            Runnable accept) {
        LinearLayout content = column();
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        android.app.AlertDialog[] dialog = {null};
        popupHeader(content, title, icon, () -> dialog[0].dismiss());
        text(content, message, 15, INK, false);
        gap(content, 12);
        LinearLayout actions = row(), cancel = column(), confirmBox = column();
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1);
        first.rightMargin = dp(10);
        actions.addView(cancel, first);
        actions.addView(confirmBox, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(actions);
        button(cancel, cancelLabel, false, () -> dialog[0].dismiss());
        Button confirmButton =
                button(
                        confirmBox,
                        confirm,
                        true,
                        () -> {
                            dialog[0].dismiss();
                            accept.run();
                        });
        confirmButton.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf(0x22FFFFFF), shape(RED, 18, false), null));
        confirmButton.setTextColor(ON_ACCENT);
        dialog[0] = popup(activity, content);
        return dialog[0];
    }

    android.app.AlertDialog formPopup(
            android.app.Activity activity,
            String title,
            int icon,
            LinearLayout form,
            Runnable save) {
        LinearLayout content = column();
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        android.app.AlertDialog[] dialog = {null};
        popupHeader(content, title, icon, () -> dialog[0].dismiss());
        form.setPadding(0, 0, 0, 0);
        ScrollView scroll = popupScroll();
        scroll.setClipToPadding(false);
        scroll.addView(form);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -2, 1));
        TextView error = text(content, "", 13, RED, false);
        error.setGravity(Gravity.CENTER);
        error.setTag("form-error");
        error.setVisibility(View.GONE);
        LinearLayout actions = row(), cancel = column(), saveBox = column();
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1);
        first.rightMargin = dp(10);
        actions.addView(cancel, first);
        actions.addView(saveBox, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(actions);
        button(cancel, I18n.text(c, R.string.close_11), false, () -> dialog[0].dismiss());
        Button saveButton =
                button(
                        saveBox,
                        I18n.text(c, R.string.save_95),
                        true,
                        () -> {
                            try {
                                save.run();
                                dialog[0].dismiss();
                            } catch (Exception failure) {
                                String message =
                                        I18n.error(
                                                c,
                                                failure.getMessage() == null
                                                        ? I18n.text(
                                                                c,
                                                                R.string
                                                                        .please_check_your_entries_96)
                                                        : failure.getMessage());
                                error.setText(message);
                                error.setVisibility(View.VISIBLE);
                                error.announceForAccessibility(message);
                            }
                        });
        saveButton.setTag("form-save");
        dialog[0] = popup(activity, content);
        if (dialog[0].getWindow() != null)
            PlatformUi.resizeDialogForKeyboard(dialog[0].getWindow());
        return dialog[0];
    }

    LinearLayout settingRow(
            LinearLayout parent, String title, String value, int icon, Runnable action) {
        LinearLayout entry = row();
        entry.setPadding(dp(16), dp(15), dp(14), dp(15));
        entry.setMinimumHeight(dp(68));
        entry.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf(dark ? 0x229DC2FF : 0x182D69CB),
                        shape(SURFACE, 22, false),
                        null));
        ImageView symbol = new ImageView(c);
        symbol.setImageResource(icon);
        symbol.setImageTintList(ColorStateList.valueOf(ACCENT));
        symbol.setPadding(dp(9), dp(9), dp(9), dp(9));
        symbol.setBackground(shape(SURFACE, 20, true));
        symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconSize.rightMargin = dp(13);
        entry.addView(symbol, iconSize);
        TextView name = label(title, 16, INK, false);
        entry.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (value != null && !value.isEmpty()) {
            TextView detail = label(value, 13, MUTED, false);
            detail.setTag("setting-value");
            detail.setMaxWidth(dp(115));
            detail.setGravity(Gravity.END);
            detail.setPadding(dp(8), 0, dp(8), 0);
            entry.addView(detail);
        }
        if (action != null) {
            TextView arrow = label("›", 25, MUTED, false);
            arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            entry.addView(arrow);
            entry.setOnClickListener(v -> action.run());
            entry.setFocusable(true);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        parent.addView(entry, params);
        return entry;
    }

    void divider(LinearLayout l) {
        View line = new View(c);
        line.setBackgroundColor(LINE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1));
        p.topMargin = dp(16);
        p.bottomMargin = dp(16);
        l.addView(line, p);
    }

    void stat(
            LinearLayout row, String name, String value, boolean last, int color, int background) {
        stat(
                row,
                name,
                value,
                last,
                color,
                background,
                I18n.text(
                        c, value.endsWith(" ml") ? R.string.milk_per_day : R.string.feeds_per_day));
    }

    void stat(
            LinearLayout row,
            String name,
            String value,
            boolean last,
            int color,
            int background,
            String caption) {
        LinearLayout box = column();
        box.setPadding(dp(11), dp(17), dp(11), dp(17));
        box.setBackground(shape(background, 22, false));
        TextView heading = text(box, name, 12, color, true);
        heading.setGravity(Gravity.CENTER);
        heading.setMaxLines(1);
        heading.setAutoSizeTextTypeUniformWithConfiguration(
                9, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        boolean volume = value.endsWith(" ml");
        TextView number =
                text(box, volume ? value.substring(0, value.length() - 3) : value, 23, color, true);
        number.setGravity(Gravity.CENTER);
        number.setMaxLines(1);
        number.setTag("stat-value");
        text(box, caption, 11, MUTED, false).setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        if (!last) p.rightMargin = dp(8);
        row.addView(box, p);
    }

    void fitStats(LinearLayout row) {
        row.getViewTreeObserver()
                .addOnGlobalLayoutListener(
                        new ViewTreeObserver.OnGlobalLayoutListener() {
                            public void onGlobalLayout() {
                                if (row.getWidth() == 0) return;
                                row.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                if (row.getChildCount() == 0) return;
                                for (int i = 0; i < row.getChildCount(); i++)
                                    if (row.getChildAt(i).findViewWithTag("stat-value") == null)
                                        return;
                                int size = 23;
                                boolean fits = false;
                                for (; size >= 14; size--) {
                                    fits = true;
                                    for (int i = 0; i < row.getChildCount(); i++) {
                                        TextView number =
                                                row.getChildAt(i).findViewWithTag("stat-value");
                                        Paint paint = new Paint(number.getPaint());
                                        paint.setTextSize(
                                                android.util.TypedValue.applyDimension(
                                                        android.util.TypedValue.COMPLEX_UNIT_SP,
                                                        size,
                                                        c.getResources().getDisplayMetrics()));
                                        if (paint.measureText(number.getText().toString())
                                                > number.getWidth()) {
                                            fits = false;
                                            break;
                                        }
                                    }
                                    if (fits) break;
                                }
                                if (!fits) {
                                    row.setOrientation(LinearLayout.VERTICAL);
                                    size = 23;
                                    for (int i = 0; i < row.getChildCount(); i++) {
                                        LinearLayout.LayoutParams p =
                                                new LinearLayout.LayoutParams(-1, -2);
                                        p.bottomMargin = dp(8);
                                        row.getChildAt(i).setLayoutParams(p);
                                    }
                                }
                                for (int i = 0; i < row.getChildCount(); i++) {
                                    TextView number =
                                            row.getChildAt(i).findViewWithTag("stat-value");
                                    number.setTextSize(size);
                                }
                            }
                        });
    }

    TextView amount(LinearLayout parent, String value, int size, int color) {
        TextView t = text(parent, value, size, color, true);
        t.setMaxLines(1);
        t.setAutoSizeTextTypeUniformWithConfiguration(
                16, size, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        return t;
    }

    static final class Bottle extends View {
        final Paint p = new Paint(3);
        final FeedUi ui;

        Bottle(Context c) {
            super(c);
            ui = new FeedUi(c);
            setContentDescription(I18n.text(c, R.string.bottle_165));
        }

        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth(), getHeight()) / 100f;
            canvas.save();
            canvas.translate((getWidth() - 100 * scale) / 2, (getHeight() - 100 * scale) / 2);
            canvas.scale(scale, scale);
            p.setColor(ui.SOFT);
            canvas.drawCircle(50, 50, 49, p);
            p.setColor(ui.ACCENT);
            canvas.drawRoundRect(42, 16, 58, 29, 5, 5, p);
            canvas.drawRoundRect(34, 29, 66, 37, 4, 4, p);
            canvas.drawRoundRect(32, 41, 68, 84, 10, 10, p);
            p.setColor(ui.SURFACE);
            canvas.drawRoundRect(37, 49, 63, 77, 5, 5, p);
            p.setColor(ui.ACCENT);
            canvas.drawRect(38, 56, 48, 59, p);
            canvas.drawRect(38, 65, 45, 68, p);
            canvas.restore();
        }
    }
}
