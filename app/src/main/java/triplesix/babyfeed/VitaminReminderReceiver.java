package triplesix.babyfeed;

import android.app.*;
import android.content.*;
import android.os.Build;

import org.json.JSONObject;

import java.time.*;

public class VitaminReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "vitamin-reminders-v1";

    @Override
    public void onReceive(Context c, Intent intent) {
        synchronized (VitaminReminders.class) {
            receive(c, intent);
        }
    }

    private void receive(Context c, Intent intent) {
        try (Store store = new Store(c)) {
            long at = intent.getLongExtra("at", 0);
            if (at <= 0) return;
            ZonedDateTime now = ZonedDateTime.now(),
                    scheduled = Instant.ofEpochMilli(at).atZone(now.getZone());
            SharedPreferences prefs = store.prefs();
            if (prefs.getLong(VitaminReminders.PENDING, 0) != at || now.isBefore(scheduled)) return;
            prefs.edit().remove(VitaminReminders.PENDING).apply();
            JSONObject vitamin = store.vitamin(now.toLocalDate());
            boolean notify =
                    VitaminReminderRules.shouldNotify(
                            prefs.getBoolean(VitaminReminders.ENABLED, false),
                            VitaminReminders.time(prefs),
                            scheduled,
                            now,
                            prefs.getBoolean(SleepHours.ENABLED, false),
                            prefs.getInt(SleepHours.START, SleepHours.DEFAULT_START),
                            prefs.getInt(SleepHours.END, SleepHours.DEFAULT_END),
                            vitamin != null && vitamin.optBoolean("taken"),
                            prefs.getString(VitaminReminders.DELIVERED, ""));
            if (!notify) return;
            // Consume today's reminder before posting so repeated broadcasts cannot replay it.
            prefs.edit()
                    .putString(VitaminReminders.DELIVERED, now.toLocalDate().toString())
                    .apply();
            if (Build.VERSION.SDK_INT >= 33
                    && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            NotificationManager manager = c.getSystemService(NotificationManager.class);
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL,
                            I18n.text(c, R.string.vitamin_reminder_channel),
                            NotificationManager.IMPORTANCE_HIGH);
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
            PendingIntent open =
                    PendingIntent.getActivity(
                            c,
                            3,
                            new Intent(c, MainActivity.class).putExtra("widgetAction", "vitamin"),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            manager.notify(
                    VitaminReminders.NOTIFICATION_ID,
                    new Notification.Builder(c, CHANNEL)
                            .setSmallIcon(R.drawable.ic_vitamin)
                            .setContentTitle(I18n.text(c, R.string.vitamin_reminder_title))
                            .setContentText(I18n.text(c, R.string.vitamin_reminder_message))
                            .setCategory(Notification.CATEGORY_REMINDER)
                            .setAutoCancel(true)
                            .setContentIntent(open)
                            .build());
        } finally {
            VitaminReminders.restore(c);
        }
    }
}
