package triplesix.babyfeed;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public class WidgetSettingsActivity extends Activity {
    int widgetId, refreshMinutes, transparencyPercent;
    FeedUi ui;
    View previewWidget;
    String widgetAppearance;

    protected void attachBaseContext(Context base) {
        super.attachBaseContext(I18n.wrap(base));
    }

    public void onCreate(Bundle state) {
        Appearance.applyTheme(this);
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        widgetId =
                getIntent()
                        .getIntExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                AppWidgetManager.INVALID_APPWIDGET_ID);
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID
                || manager.getAppWidgetInfo(widgetId) == null
                || !manager.getAppWidgetInfo(widgetId)
                        .provider
                        .equals(new ComponentName(this, NextFeedWidget.class))) {
            finish();
            return;
        }
        refreshMinutes = WidgetSettings.refreshMinutes(this, widgetId);
        transparencyPercent = WidgetSettings.transparency(this, widgetId);
        widgetAppearance = WidgetSettings.appearance(this, widgetId);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        ui = new FeedUi(this);
        Appearance.styleWindow(this, ui);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(24));
        content.setBackgroundColor(ui.BG);
        scroll.addView(content);
        setContentView(scroll);
        PlatformUi.applySystemInsets(scroll);
        scroll.requestApplyInsets();
        ui.text(content, getString(R.string.widget_settings), 26, ui.INK, true);
        ui.text(content, getString(R.string.widget_saved_automatically), 13, ui.MUTED, false);
        ui.gap(content, 16);
        FrameLayout preview = new FrameLayout(this);
        preview.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        preview.setBackground(ui.shape(ui.LINE, 26, false));
        View widget = NextFeedWidget.createViews(this, manager, widgetId).apply(this, preview);
        widget.setClickable(false);
        disableClicks(widget);
        preview.addView(widget, new FrameLayout.LayoutParams(-1, -1));
        content.addView(preview, new LinearLayout.LayoutParams(-1, ui.dp(220)));
        previewWidget = widget;
        ui.gap(content, 24);
        LinearLayout appearanceCard = ui.card(content, ui.SURFACE);
        ui.text(appearanceCard, getString(R.string.settings_appearance), 17, ui.INK, true);
        String[] modes = {"light", "dark", "system"};
        badges(
                appearanceCard,
                new String[] {
                    getString(R.string.appearance_light),
                    getString(R.string.appearance_dark),
                    getString(R.string.appearance_system)
                },
                java.util.Arrays.asList(modes).indexOf(widgetAppearance),
                index -> {
                    widgetAppearance = modes[index];
                    updatePreview();
                    saveSettings();
                });
        LinearLayout refreshCard = ui.card(content, ui.SURFACE);
        ui.text(refreshCard, getString(R.string.widget_refresh), 17, ui.INK, true);
        int[] intervals = {1, 5, 10};
        String[] intervalLabels = new String[intervals.length];
        int selectedInterval = 1;
        for (int n = 0; n < intervals.length; n++) {
            intervalLabels[n] =
                    getResources()
                            .getQuantityString(
                                    R.plurals.widget_refresh_minutes, intervals[n], intervals[n]);
            if (intervals[n] == refreshMinutes) selectedInterval = n;
        }
        badges(
                refreshCard,
                intervalLabels,
                selectedInterval,
                index -> {
                    refreshMinutes = intervals[index];
                    saveSettings();
                });
        LinearLayout transparencyCard = ui.card(content, ui.SURFACE);
        TextView transparencyLabel = ui.text(transparencyCard, "", 17, ui.INK, true);
        SeekBar transparency = new SeekBar(this);
        transparency.setId(R.id.widget_transparency_input);
        transparency.setSaveEnabled(false);
        transparency.setMax(10);
        transparency.setKeyProgressIncrement(1);
        transparency.setProgress(transparencyPercent / 10);
        transparency.setTickMark(getDrawable(R.drawable.widget_slider_tick));
        transparency.setTickMarkTintList(android.content.res.ColorStateList.valueOf(ui.MUTED));
        transparency.setContentDescription(getString(R.string.widget_transparency));
        transparencyCard.addView(transparency, new LinearLayout.LayoutParams(-1, ui.dp(48)));
        transparency.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    public void onStartTrackingTouch(SeekBar bar) {}

                    public void onStopTrackingTouch(SeekBar bar) {}

                    public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                        updateTransparency(transparencyLabel, value * 10);
                        if (fromUser) saveSettings();
                    }
                });
        updateTransparency(transparencyLabel, transparencyPercent);
        LinearLayout endpoints = ui.row();
        TextView zero = ui.label("0%", 12, ui.MUTED, false),
                full = ui.label("100%", 12, ui.MUTED, false);
        endpoints.addView(zero, new LinearLayout.LayoutParams(0, -2, 1));
        endpoints.addView(full);
        transparencyCard.addView(endpoints);
        NextFeedWidget.update(this, manager, widgetId);
        NextFeedWidget.scheduleTick(this);
    }

    void saveSettings() {
        if (refreshMinutes == WidgetSettings.refreshMinutes(this, widgetId)
                && transparencyPercent == WidgetSettings.transparency(this, widgetId)
                && widgetAppearance.equals(WidgetSettings.appearance(this, widgetId))) return;
        WidgetSettings.save(this, widgetId, refreshMinutes, transparencyPercent, widgetAppearance);
        NextFeedWidget.update(this, AppWidgetManager.getInstance(this), widgetId);
        NextFeedWidget.scheduleTick(this);
    }

    void updateTransparency(TextView label, int value) {
        transparencyPercent = value;
        label.setText(getString(R.string.widget_transparency_value, value));
        updatePreview();
    }

    void updatePreview() {
        RemoteViews style = new RemoteViews(getPackageName(), R.layout.widget_next_feed);
        String resolved = WidgetSettings.palette(this, widgetAppearance).dark ? "dark" : "light";
        NextFeedWidget.applyAppearance(this, style, resolved, transparencyPercent);
        style.reapply(this, previewWidget);
    }

    void badges(
            LinearLayout parent,
            String[] labels,
            int selected,
            java.util.function.IntConsumer onSelected) {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        int[] checked = {android.R.attr.state_checked};
        android.content.res.ColorStateList textColors =
                new android.content.res.ColorStateList(
                        new int[][] {checked, new int[] {}}, new int[] {ui.ON_ACCENT, ui.INK});
        for (int n = 0; n < labels.length; n++) {
            final int index = n;
            RadioButton badge = new RadioButton(this);
            badge.setId(View.generateViewId());
            badge.setButtonDrawable(null);
            badge.setGravity(Gravity.CENTER);
            badge.setText(labels[n]);
            badge.setTextColor(textColors);
            badge.setTextSize(14);
            badge.setSingleLine(true);
            badge.setAutoSizeTextTypeUniformWithConfiguration(
                    10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            badge.setMinHeight(ui.dp(44));
            badge.setPadding(ui.dp(5), ui.dp(8), ui.dp(5), ui.dp(8));
            android.graphics.drawable.StateListDrawable background =
                    new android.graphics.drawable.StateListDrawable();
            background.addState(checked, ui.shape(ui.ACCENT, 14, false));
            background.addState(new int[] {}, ui.shape(ui.SOFT, 14, false));
            badge.setBackground(background);
            RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(0, -2, 1);
            if (n < labels.length - 1) params.rightMargin = ui.dp(8);
            group.addView(badge, params);
            badge.setChecked(n == selected);
            badge.setOnClickListener(v -> onSelected.accept(index));
        }
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
    }

    void disableClicks(View view) {
        view.setOnClickListener(null);
        view.setClickable(false);
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
                disableClicks(((ViewGroup) view).getChildAt(i));
    }
}
