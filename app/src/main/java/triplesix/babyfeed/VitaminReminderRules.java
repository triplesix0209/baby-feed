package triplesix.babyfeed;

import java.time.*;

final class VitaminReminderRules {
    static ZonedDateTime restore(
            ZonedDateTime now,
            int minute,
            long pendingAt,
            String pendingZone,
            boolean taken,
            String deliveredDay) {
        if (pendingAt > 0 && pendingZone.equals(now.getZone().getId())) {
            ZonedDateTime pending = Instant.ofEpochMilli(pendingAt).atZone(now.getZone());
            if (pending.toLocalDate().equals(now.toLocalDate())
                    && pending.getHour() * 60 + pending.getMinute() == minute
                    && !taken
                    && !deliveredDay.equals(now.toLocalDate().toString())) {
                return pending;
            }
        }
        return next(now, minute);
    }

    static ZonedDateTime next(ZonedDateTime now, int minute) {
        ZonedDateTime next =
                now.toLocalDate()
                        .atTime(LocalTime.of(minute / 60, minute % 60))
                        .atZone(now.getZone());
        if (!next.isAfter(now))
            next =
                    now.toLocalDate()
                            .plusDays(1)
                            .atTime(LocalTime.of(minute / 60, minute % 60))
                            .atZone(now.getZone());
        return next;
    }

    static boolean shouldNotify(
            boolean enabled,
            int minute,
            ZonedDateTime scheduled,
            ZonedDateTime now,
            boolean sleepEnabled,
            int sleepStart,
            int sleepEnd,
            boolean taken,
            String deliveredDay) {
        return enabled
                && scheduled.toLocalDate().equals(now.toLocalDate())
                && !now.isBefore(scheduled)
                && scheduled.getHour() * 60 + scheduled.getMinute() == minute
                && !scheduled.toLocalDate().toString().equals(deliveredDay)
                && !taken
                && !SleepHours.contains(sleepEnabled, sleepStart, sleepEnd, scheduled.toLocalTime())
                && !SleepHours.contains(sleepEnabled, sleepStart, sleepEnd, now.toLocalTime());
    }
}
