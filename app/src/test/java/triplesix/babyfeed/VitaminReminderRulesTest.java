package triplesix.babyfeed;

import static org.junit.Assert.*;

import org.junit.Test;

import java.time.*;

public class VitaminReminderRulesTest {
    static final ZonedDateTime AT =
            ZonedDateTime.of(2026, 10, 15, 9, 0, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"));

    static boolean notifyAt(
            boolean enabled,
            int minute,
            ZonedDateTime scheduled,
            ZonedDateTime now,
            boolean sleep,
            int start,
            int end,
            boolean taken,
            String delivered) {
        return VitaminReminderRules.shouldNotify(
                enabled, minute, scheduled, now, sleep, start, end, taken, delivered);
    }

    @Test
    public void nextUsesLocalTimeAndNeverCatchesUp() {
        assertEquals(AT, VitaminReminderRules.next(AT.minusMinutes(1), 540));
        assertEquals(AT.plusDays(1), VitaminReminderRules.next(AT, 540));
        assertEquals(AT.plusDays(1), VitaminReminderRules.next(AT.plusHours(3), 540));
        assertEquals(
                AT.toLocalDate().plusDays(1).atStartOfDay(AT.getZone()),
                VitaminReminderRules.next(AT, 0));
    }

    @Test
    public void nextPreservesWallClockAcrossDaylightSavingChange() {
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 28, 10, 0, 0, 0, ZoneId.of("Europe/Berlin"));
        ZonedDateTime next = VitaminReminderRules.next(now, 540);
        assertEquals(9, next.getHour());
        assertEquals(ZoneOffset.ofHours(2), next.getOffset());
    }

    @Test
    public void enabledReminderFiresOnlyOncePerDayAndSkipsTaken() {
        assertTrue(notifyAt(true, 540, AT, AT, false, 0, 0, false, ""));
        assertFalse(notifyAt(false, 540, AT, AT, false, 0, 0, false, ""));
        assertFalse(notifyAt(true, 540, AT, AT, false, 0, 0, true, ""));
        assertFalse(notifyAt(true, 540, AT, AT, false, 0, 0, false, "2026-10-15"));
        assertFalse(notifyAt(true, 600, AT, AT, false, 0, 0, false, ""));
        assertFalse(notifyAt(true, 540, AT, AT.minusMinutes(1), false, 0, 0, false, ""));
        assertFalse(notifyAt(true, 540, AT, AT.plusDays(1), false, 0, 0, false, ""));
        assertTrue(
                notifyAt(
                        true,
                        540,
                        AT.plusDays(1),
                        AT.plusDays(1),
                        false,
                        0,
                        0,
                        false,
                        "2026-10-15"));
    }

    @Test
    public void scheduledSleepHoursAreSkippedEvenWhenDeliveredAfterSleep() {
        assertFalse(notifyAt(true, 540, AT, AT.plusHours(2), true, 22 * 60, 10 * 60, false, ""));
        assertFalse(notifyAt(true, 540, AT, AT, true, 540, 600, false, ""));
        assertTrue(notifyAt(true, 540, AT, AT, true, 22 * 60, 540, false, ""));
        assertTrue(notifyAt(true, 540, AT, AT, false, 22 * 60, 10 * 60, false, ""));
    }

    @Test
    public void suppressesNotificationsDeliveredDuringSleep() {
        assertFalse(notifyAt(true, 540, AT, AT.plusHours(1), true, 600, 660, false, ""));
    }
}
