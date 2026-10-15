package triplesix.babyfeed;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "feeding-alarms-v1";

    static PendingIntent action(Context c, String action, String id, int code) {
        Intent i =
                new Intent(c, ReminderReceiver.class)
                        .setAction(action)
                        .setData(Uri.parse("babyfeed://" + action + "/" + id))
                        .putExtra("id", id);
        return PendingIntent.getBroadcast(
                c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public void onReceive(Context c, Intent i) {
        NextFeedWidget.updateAll(c);
        try (Store s = new Store(c)) {
            String id = i.getStringExtra("id");
            if ("dismiss".equals(i.getAction()) || "snooze".equals(i.getAction())) {
                if (s.latest() == null || !s.latest().optString("id").equals(id)) return;
                Reminders.silence(c);
                if ("snooze".equals(i.getAction())
                        && s.latest() != null
                        && s.latest().optString("id").equals(id)) Reminders.snooze(c, id);
                return;
            }
            if (s.latest() == null || !s.latest().optString("id").equals(id)) return;
            boolean check = "check".equals(i.getStringExtra("kind"));
            int code = check ? 2 : 1;
            long originalAt =
                    check
                            ? s.latest().optLong("preparedAt") + 1800000
                            : s.latest().optLong("nextAt");
            long scheduledAt = i.getLongExtra("at", s.prefs().getLong("snooze-" + id, originalAt));
            if (Reminders.shouldSkip(c, scheduledAt)) {
                // Consume this reminder so restore/sync cannot replay it after sleep hours.
                s.prefs().edit().putString("delivered" + code, id).remove("snooze-" + id).apply();
                Reminders.silence(c);
                return;
            }
            if (Build.VERSION.SDK_INT >= 33
                    && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL,
                            I18n.text(c, R.string.feeding_alarms_147),
                            NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(channel);
            Intent alarm =
                    new Intent(c, AlarmActivity.class)
                            .putExtra("id", id)
                            .putExtra("kind", check ? "check" : "prepare")
                            .putExtra("ml", i.getIntExtra("ml", 0));
            PendingIntent open =
                    PendingIntent.getActivity(
                            c,
                            code,
                            alarm,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder =
                    new Notification.Builder(c, CHANNEL)
                            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                            .setContentTitle(
                                    check
                                            ? I18n.text(c, R.string.has_baby_finished_feeding_133)
                                            : I18n.text(
                                                    c, R.string.time_to_prepare_milk_for_baby_148))
                            .setContentText(
                                    check
                                            ? I18n.text(c, R.string.record_how_much_baby_drank_149)
                                            : I18n.text(c, R.string.prepared_161)
                                                    + Amounts.ml(
                                                            i.getIntExtra("ml", 0), I18n.locale(c))
                                                    + I18n.text(c, R.string.milk_34))
                            .setStyle(
                                    new Notification.BigTextStyle()
                                            .bigText(
                                                    check
                                                            ? I18n.text(
                                                                    c,
                                                                    R.string
                                                                            .has_baby_finished_feeding_open_the_app_to_record_it_150)
                                                            : I18n.text(
                                                                            c,
                                                                            R.string
                                                                                    .time_to_prepare_151)
                                                                    + Amounts.ml(
                                                                            i.getIntExtra("ml", 0),
                                                                            I18n.locale(c))
                                                                    + I18n.text(
                                                                            c,
                                                                            R.string
                                                                                    .milk_for_baby_152)))
                            .setCategory(Notification.CATEGORY_ALARM)
                            .setVisibility(Notification.VISIBILITY_PUBLIC)
                            .setContentIntent(open)
                            .setTimeoutAfter(60000)
                            .addAction(
                                    new Notification.Action.Builder(
                                                    null,
                                                    I18n.text(c, R.string.dismiss_153),
                                                    action(c, "dismiss", id, code))
                                            .build())
                            .addAction(
                                    new Notification.Action.Builder(
                                                    null,
                                                    I18n.text(c, R.string.snooze_10_minutes_146),
                                                    action(c, "snooze", id, code))
                                            .build());
            if (Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent())
                builder.setFullScreenIntent(open, true);
            Notification notification = builder.build();
            notification.flags |= Notification.FLAG_INSISTENT;
            nm.notify(code, notification);
            s.prefs().edit().putString("delivered" + code, id).remove("snooze-" + id).apply();
        }
    }
}
