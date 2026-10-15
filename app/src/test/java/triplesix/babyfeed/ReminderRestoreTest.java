package triplesix.babyfeed;

import static org.junit.Assert.*;

import org.junit.Test;

import java.time.*;

public class ReminderRestoreTest {
    private static final ZonedDateTime NINE =
            ZonedDateTime.of(2026, 10, 15, 9, 0, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"));

    private ZonedDateTime restore(
            ZonedDateTime now, long pending, boolean taken, String delivered) {
        return VitaminReminderRules.restore(
                now, 540, pending, now.getZone().getId(), taken, delivered);
    }

    @Test
    public void resumePreservesDelayedVitaminReminder() {
        assertEquals(
                NINE, restore(NINE.plusMinutes(1), NINE.toInstant().toEpochMilli(), false, ""));
        assertEquals(NINE, restore(NINE.plusHours(2), NINE.toInstant().toEpochMilli(), false, ""));
    }

    @Test
    public void deliveredOrTakenVitaminIsNotReplayed() {
        assertEquals(
                NINE.plusDays(1),
                restore(NINE.plusMinutes(1), NINE.toInstant().toEpochMilli(), true, ""));
        assertEquals(
                NINE.plusDays(1),
                restore(NINE.plusMinutes(1), NINE.toInstant().toEpochMilli(), false, "2026-10-15"));
    }

    @Test
    public void yesterdayOrChangedTimeDoesNotCatchUp() {
        assertEquals(
                NINE.plusDays(1),
                restore(
                        NINE.plusMinutes(1),
                        NINE.minusDays(1).toInstant().toEpochMilli(),
                        false,
                        ""));
        assertEquals(
                NINE.plusDays(1),
                restore(
                        NINE.plusMinutes(1),
                        NINE.minusHours(1).toInstant().toEpochMilli(),
                        false,
                        ""));
    }

    @Test
    public void timezoneChangeInvalidatesPendingWallClock() {
        assertEquals(
                NINE.plusDays(1),
                VitaminReminderRules.restore(
                        NINE.plusMinutes(1),
                        540,
                        NINE.toInstant().toEpochMilli(),
                        "UTC",
                        false,
                        ""));
    }

    @Test
    public void rebootAfterSleepDoesNotReplayMilkReminder() {
        ZonedDateTime scheduled = NINE.minusDays(1).withHour(23);
        assertTrue(SleepHours.suppresses(true, 22 * 60, 6 * 60, scheduled, NINE.withHour(7)));
        assertFalse(SleepHours.suppresses(false, 22 * 60, 6 * 60, scheduled, NINE.withHour(7)));
    }

    @Test
    public void delayedMilkDuringSleepIsSuppressedAndEndBoundaryIsAllowed() {
        assertTrue(
                SleepHours.suppresses(true, 22 * 60, 6 * 60, NINE.withHour(21), NINE.withHour(22)));
        assertFalse(
                SleepHours.suppresses(true, 22 * 60, 6 * 60, NINE.withHour(6), NINE.withHour(7)));
    }
}
