package triplesix.babyfeed;

import java.time.LocalTime;

/** Daily local-time interval: start inclusive, end exclusive, including overnight. */
final class SleepHours {
    static boolean suppresses(
            boolean enabled,
            int start,
            int end,
            java.time.ZonedDateTime scheduled,
            java.time.ZonedDateTime now) {
        return contains(enabled, start, end, scheduled.toLocalTime())
                || contains(enabled, start, end, now.toLocalTime());
    }

    static final String ENABLED = "sleepHoursEnabled",
            START = "sleepHoursStart",
            END = "sleepHoursEnd";
    static final int DEFAULT_START = 22 * 60, DEFAULT_END = 6 * 60;

    static boolean contains(boolean enabled, int start, int end, LocalTime now) {
        if (!enabled || start == end) return false;
        int minute = now.getHour() * 60 + now.getMinute();
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }

    private SleepHours() {}
}
