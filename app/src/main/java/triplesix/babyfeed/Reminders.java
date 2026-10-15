package triplesix.babyfeed;

import android.app.*;
import android.content.*;
import android.os.Build;

import org.json.JSONObject;

public final class Reminders {
    static boolean isSleeping(Context c) {
        android.content.SharedPreferences prefs = c.getSharedPreferences("settings", 0);
        return SleepHours.contains(
                prefs.getBoolean(SleepHours.ENABLED, false),
                prefs.getInt(SleepHours.START, SleepHours.DEFAULT_START),
                prefs.getInt(SleepHours.END, SleepHours.DEFAULT_END),
                java.time.LocalTime.now());
    }

    static boolean shouldSkip(Context c, long scheduledAt) {
        SharedPreferences prefs = c.getSharedPreferences("settings", 0);
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        java.time.ZonedDateTime scheduled =
                java.time.Instant.ofEpochMilli(scheduledAt).atZone(now.getZone());
        return SleepHours.suppresses(
                prefs.getBoolean(SleepHours.ENABLED, false),
                prefs.getInt(SleepHours.START, SleepHours.DEFAULT_START),
                prefs.getInt(SleepHours.END, SleepHours.DEFAULT_END),
                scheduled,
                now);
    }

    static PendingIntent intent(Context c, int code, String id, String kind, int ml, long at) {
        return PendingIntent.getBroadcast(
                c,
                code,
                new Intent(c, ReminderReceiver.class)
                        .putExtra("id", id)
                        .putExtra("kind", kind)
                        .putExtra("ml", ml)
                        .putExtra("at", at),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static synchronized void restore(Context c) {
        VitaminReminders.restore(c);
        NextFeedWidget.updateAll(c);
        try (Store s = new Store(c)) {
            AlarmManager manager = c.getSystemService(AlarmManager.class);
            manager.cancel(intent(c, 1, "", "", 0, 0));
            manager.cancel(intent(c, 2, "", "", 0, 0));
            JSONObject e = s.latest();
            if (e == null) return;
            String kind = e.optString("kind");
            long time;
            int code;
            if (kind.equals("prepared")) {
                time = e.optLong("preparedAt") + 30 * 60000L;
                code = 2;
            } else if (kind.equals("scheduled") || kind.equals("finished")) {
                time = e.optLong("nextAt");
                code = 1;
            } else return;
            if (time <= 0) return;
            // Delivered reminders remain visible; do not re-fire on each activity resume.
            long snooze = s.prefs().getLong("snooze-" + e.optString("id"), 0);
            if (snooze > 0) time = snooze;
            else if (s.prefs().getString("delivered" + code, "").equals(e.optString("id"))) return;
            if (time <= System.currentTimeMillis() && shouldSkip(c, time)) {
                s.prefs()
                        .edit()
                        .putString("delivered" + code, e.optString("id"))
                        .remove("snooze-" + e.optString("id"))
                        .apply();
                silence(c);
                return;
            }
            PendingIntent pi =
                    intent(
                            c,
                            code,
                            e.optString("id"),
                            code == 1 ? "prepare" : "check",
                            e.optInt("ml"),
                            time);
            time = Math.max(time, System.currentTimeMillis() + 1000);
            if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
                try {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
                    return;
                } catch (SecurityException ignored) {
                    // Permission may be revoked between the check and scheduling.
                }
            }
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
        }
    }

    public static void snooze(Context c, String id) {
        try (Store s = new Store(c)) {
            s.prefs().edit().putLong("snooze-" + id, System.currentTimeMillis() + 600000).apply();
        }
        restore(c);
    }

    public static void silence(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.cancel(1);
        nm.cancel(2);
    }
}
