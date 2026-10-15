package triplesix.babyfeed;

import android.app.*;
import android.content.*;
import android.os.Build;

import org.json.JSONObject;

import java.time.*;

final class VitaminReminders {
    static final String ENABLED = "vitaminReminderEnabled",
            TIME = "vitaminReminderTime",
            DELIVERED = "vitaminReminderDelivered";
    static final int DEFAULT_TIME = 9 * 60, NOTIFICATION_ID = 3;
    static final String PENDING = "vitaminReminderPending", PENDING_ZONE = "vitaminReminderZone";

    static int time(SharedPreferences prefs) {
        int value = prefs.getInt(TIME, DEFAULT_TIME);
        return value >= 0 && value < 1440 ? value : DEFAULT_TIME;
    }

    static PendingIntent intent(Context c, long at) {
        return PendingIntent.getBroadcast(
                c,
                3,
                new Intent(c, VitaminReminderReceiver.class).putExtra("at", at),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static synchronized void restore(Context c) {
        try (Store store = new Store(c)) {
            SharedPreferences prefs = store.prefs();
            AlarmManager alarms = c.getSystemService(AlarmManager.class);
            alarms.cancel(intent(c, 0));
            JSONObject vitamin = store.vitamin(LocalDate.now());
            if (!prefs.getBoolean(ENABLED, false)
                    || Reminders.isSleeping(c)
                    || (vitamin != null && vitamin.optBoolean("taken"))) cancelNotification(c);
            if (!prefs.getBoolean(ENABLED, false)) {
                prefs.edit().remove(PENDING).remove(PENDING_ZONE).apply();
                return;
            }
            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime scheduled =
                    VitaminReminderRules.restore(
                            now,
                            time(prefs),
                            prefs.getLong(PENDING, 0),
                            prefs.getString(PENDING_ZONE, ""),
                            vitamin != null && vitamin.optBoolean("taken"),
                            prefs.getString(DELIVERED, ""));
            if (!scheduled.isAfter(now)
                    && SleepHours.suppresses(
                            prefs.getBoolean(SleepHours.ENABLED, false),
                            prefs.getInt(SleepHours.START, SleepHours.DEFAULT_START),
                            prefs.getInt(SleepHours.END, SleepHours.DEFAULT_END),
                            scheduled,
                            now)) {
                prefs.edit().putString(DELIVERED, now.toLocalDate().toString()).apply();
                scheduled = VitaminReminderRules.next(now, time(prefs));
            }
            long next = scheduled.toInstant().toEpochMilli();
            prefs.edit()
                    .putLong(PENDING, next)
                    .putString(PENDING_ZONE, now.getZone().getId())
                    .apply();
            PendingIntent pending = intent(c, next);
            long trigger = Math.max(System.currentTimeMillis() + 1000, next);
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                try {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
                    return;
                } catch (SecurityException ignored) {
                }
            }
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
        }
    }

    static void cancelNotification(Context c) {
        c.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
    }
}
