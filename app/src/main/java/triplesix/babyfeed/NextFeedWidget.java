package triplesix.babyfeed;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class NextFeedWidget extends AppWidgetProvider {
    public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        updateAll(c);
    }

    public void onAppWidgetOptionsChanged(
            Context c, AppWidgetManager manager, int id, Bundle options) {
        updateAll(c);
    }

    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if ("triplesix.babyfeed.WIDGET_TICK".equals(i.getAction())) refreshDue(c);
        else if (Intent.ACTION_TIME_CHANGED.equals(i.getAction())
                || Intent.ACTION_TIMEZONE_CHANGED.equals(i.getAction())) updateAll(c);
    }

    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) WidgetSettings.remove(c, id);
        scheduleTick(c);
    }

    public void onDisabled(Context c) {
        c.getSystemService(AlarmManager.class).cancel(tick(c));
    }

    static PendingIntent tick(Context c) {
        return PendingIntent.getBroadcast(
                c,
                80,
                new Intent(c, NextFeedWidget.class).setAction("triplesix.babyfeed.WIDGET_TICK"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static int[] ids(Context c) {
        return AppWidgetManager.getInstance(c)
                .getAppWidgetIds(new ComponentName(c, NextFeedWidget.class));
    }

    static void updateAll(Context c) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        for (int id : ids(c)) update(c, manager, id);
        scheduleTick(c);
    }

    static void refreshDue(Context c) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        long now = System.currentTimeMillis();
        for (int id : ids(c)) if (WidgetSettings.nextRefresh(c, id) <= now) update(c, manager, id);
        scheduleTick(c);
    }

    static void scheduleTick(Context c) {
        AlarmManager alarm = c.getSystemService(AlarmManager.class);
        alarm.cancel(tick(c));
        int[] ids = ids(c);
        if (ids.length == 0) return;
        try (Store store = new Store(c)) {
            JSONObject e = store.latest();
            if (e == null || e.optString("kind").equals("cancelled")) return;
            long at =
                    e.optString("kind").equals("prepared")
                            ? e.optLong("preparedAt") + 1800000
                            : e.optLong("nextAt");
            at = store.prefs().getLong("snooze-" + e.optString("id"), at);
            long now = System.currentTimeMillis(), next = Long.MAX_VALUE;
            for (int id : ids) next = Math.min(next, WidgetSettings.nextRefresh(c, id));
            // Also refresh at the due time even if the chosen interval is longer.
            if (at > now && at < next) {
                for (int id : ids)
                    WidgetSettings.setNextRefresh(
                            c, id, Math.min(at, WidgetSettings.nextRefresh(c, id)));
                next = at;
            }
            long trigger = SystemClock.elapsedRealtime() + Math.max(1000, next - now);
            // Refresh while the device is awake; feeding reminders handle waking it.
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
                try {
                    alarm.setExact(AlarmManager.ELAPSED_REALTIME, trigger, tick(c));
                    return;
                } catch (SecurityException ignored) {
                    /* Permission may have been revoked. */
                }
            }
            alarm.set(AlarmManager.ELAPSED_REALTIME, trigger, tick(c));
        }
    }

    static PendingIntent open(Context c, int widget, String action, String event) {
        Intent i =
                new Intent(c, MainActivity.class)
                        .setData(Uri.parse("babyfeed://widget/" + widget + "/" + action))
                        .putExtra("widgetAction", action)
                        .putExtra("widgetEvent", event)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                c, widget, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void update(Context c, AppWidgetManager manager, int id) {
        manager.updateAppWidget(id, createViews(c, manager, id));
        WidgetSettings.setNextRefresh(
                c, id, System.currentTimeMillis() + WidgetSettings.refreshMinutes(c, id) * 60000L);
    }

    static RemoteViews createViews(Context c, AppWidgetManager manager, int id) {
        try (Store store = new Store(c)) {
            JSONObject e = store.latest();
            boolean empty = e == null || e.optString("kind").equals("cancelled");
            boolean drinking = !empty && e.optString("kind").equals("prepared");
            RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget_next_feed);
            applyAppearance(
                    c, views, WidgetSettings.appearance(c, id), WidgetSettings.transparency(c, id));
            long at =
                    empty ? 0 : drinking ? e.optLong("preparedAt") + 1800000 : e.optLong("nextAt");
            if (!empty) at = store.prefs().getLong("snooze-" + e.optString("id"), at);
            long remaining = at - System.currentTimeMillis();
            views.setTextViewText(
                    R.id.widget_title,
                    drinking
                            ? I18n.text(c, R.string.baby_is_feeding_140)
                            : I18n.text(c, R.string.next_feed_141));
            views.setTextViewText(
                    R.id.widget_time,
                    empty
                            ? I18n.text(c, R.string.no_scheduled_feed_142)
                            : new SimpleDateFormat("HH:mm", I18n.locale(c)).format(new Date(at)));
            views.setTextViewText(
                    R.id.widget_date,
                    empty
                            ? "Baby Feed"
                            : new SimpleDateFormat("dd/MM/yyyy", I18n.locale(c))
                                            .format(new Date(at))
                                    + (drinking ? I18n.text(c, R.string.check_again_143) : ""));
            views.setViewVisibility(R.id.widget_amount, empty ? View.GONE : View.VISIBLE);
            views.setTextViewText(
                    R.id.widget_amount,
                    empty
                            ? I18n.text(c, R.string.schedule_the_first_feed_144)
                            : Amounts.ml(e.optInt("ml"), I18n.locale(c)));
            views.setViewVisibility(R.id.widget_countdown, empty ? View.GONE : View.VISIBLE);
            views.setTextViewText(R.id.widget_countdown, I18n.countdown(c, remaining));
            views.setTextViewText(
                    R.id.widget_action,
                    empty
                            ? I18n.text(c, R.string.schedule_145)
                            : drinking
                                    ? I18n.text(c, R.string.baby_has_finished_33)
                                    : I18n.text(c, R.string.milk_prepared_35));
            String event = empty ? "" : e.optString("id");
            views.setOnClickPendingIntent(R.id.widget_root, open(c, id, "open", event));
            views.setOnClickPendingIntent(
                    R.id.widget_action,
                    open(c, id, empty ? "schedule" : drinking ? "finish" : "prepare", event));
            int height =
                    manager.getAppWidgetOptions(id)
                            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 360);
            views.setViewVisibility(R.id.widget_extra, !empty ? View.VISIBLE : View.GONE);
            int padding = dp(c, height < 180 ? 6 : 8);
            views.setViewPadding(R.id.widget_root, padding, padding, padding, padding);
            views.setViewVisibility(R.id.widget_date, height < 180 ? View.GONE : View.VISIBLE);
            views.setTextViewText(
                    R.id.widget_edit,
                    empty
                            ? I18n.text(c, R.string.schedule_145)
                            : drinking
                                    ? I18n.text(c, R.string.snooze_10_minutes_146)
                                    : I18n.text(c, R.string.widget_change));
            views.setOnClickPendingIntent(
                    R.id.widget_edit,
                    open(c, id, empty ? "schedule" : drinking ? "snooze" : "edit", event));
            views.setViewVisibility(R.id.widget_cancel, empty ? View.GONE : View.VISIBLE);
            views.setOnClickPendingIntent(R.id.widget_cancel, open(c, id, "cancel", event));
            views.setTextViewText(R.id.widget_cancel, I18n.text(c, R.string.cancel_schedule_14));
            return views;
        }
    }

    static void applyAppearance(Context c, RemoteViews views, String mode, int transparency) {
        FeedUi palette = WidgetSettings.palette(c, mode),
                light = WidgetSettings.palette(c, "light"),
                dark = WidgetSettings.palette(c, "dark");
        boolean system = "system".equals(mode);
        views.setImageViewResource(
                R.id.widget_background,
                palette.dark ? R.drawable.widget_background_dark : R.drawable.widget_background);
        views.setInt(
                R.id.widget_background,
                "setImageAlpha",
                WidgetSettings.backgroundAlpha(transparency));
        views.setImageViewResource(
                R.id.widget_bottle,
                palette.dark ? R.drawable.ic_widget_bottle_dark : R.drawable.ic_widget_bottle);
        for (int id :
                new int[] {
                    R.id.widget_title, R.id.widget_amount, R.id.widget_countdown, R.id.widget_edit
                }) widgetTextColor(views, id, palette.ACCENT, light.ACCENT, dark.ACCENT, system);
        widgetTextColor(views, R.id.widget_time, palette.INK, light.INK, dark.INK, system);
        widgetTextColor(views, R.id.widget_date, palette.MUTED, light.MUTED, dark.MUTED, system);
        widgetTextColor(views, R.id.widget_cancel, palette.RED, light.RED, dark.RED, system);
        widgetTextColor(
                views,
                R.id.widget_action,
                palette.ON_ACCENT,
                light.ON_ACCENT,
                dark.ON_ACCENT,
                system);
        views.setInt(
                R.id.widget_action,
                "setBackgroundResource",
                palette.dark ? R.drawable.widget_button_dark : R.drawable.widget_button);
        if (system && Build.VERSION.SDK_INT >= 31) {
            views.setIcon(
                    R.id.widget_background,
                    "setImageIcon",
                    android.graphics.drawable.Icon.createWithResource(
                            c, R.drawable.widget_background),
                    android.graphics.drawable.Icon.createWithResource(
                            c, R.drawable.widget_background_dark));
            views.setIcon(
                    R.id.widget_bottle,
                    "setImageIcon",
                    android.graphics.drawable.Icon.createWithResource(
                            c, R.drawable.ic_widget_bottle),
                    android.graphics.drawable.Icon.createWithResource(
                            c, R.drawable.ic_widget_bottle_dark));
            views.setInt(
                    R.id.widget_background,
                    "setImageAlpha",
                    WidgetSettings.backgroundAlpha(transparency));
            views.setInt(
                    R.id.widget_action, "setBackgroundResource", R.drawable.widget_button_system);
        }
    }

    static void widgetTextColor(
            RemoteViews views, int id, int current, int light, int dark, boolean system) {
        if (system && Build.VERSION.SDK_INT >= 31)
            views.setColorInt(id, "setTextColor", light, dark);
        else views.setTextColor(id, current);
    }

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
