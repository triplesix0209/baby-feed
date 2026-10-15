package triplesix.babyfeed;

import static org.junit.Assert.*;

import org.junit.Test;

import java.time.LocalTime;

public class SleepHoursTest {
    @Test
    public void disabledNeverSuppressesReminders() {
        assertFalse(SleepHours.contains(false, 1320, 360, LocalTime.of(23, 0)));
    }

    @Test
    public void overnightIncludesStartAndExcludesEnd() {
        assertFalse(SleepHours.contains(true, 1320, 360, LocalTime.of(21, 59, 59)));
        assertTrue(SleepHours.contains(true, 1320, 360, LocalTime.of(22, 0)));
        assertTrue(SleepHours.contains(true, 1320, 360, LocalTime.of(23, 59, 59)));
        assertTrue(SleepHours.contains(true, 1320, 360, LocalTime.MIDNIGHT));
        assertTrue(SleepHours.contains(true, 1320, 360, LocalTime.of(5, 59, 59)));
        assertFalse(SleepHours.contains(true, 1320, 360, LocalTime.of(6, 0)));
        assertFalse(SleepHours.contains(true, 1320, 360, LocalTime.NOON));
    }

    @Test
    public void daytimeNapHonorsMinuteBoundaries() {
        assertFalse(SleepHours.contains(true, 735, 825, LocalTime.of(12, 14, 59)));
        assertTrue(SleepHours.contains(true, 735, 825, LocalTime.of(12, 15)));
        assertTrue(SleepHours.contains(true, 735, 825, LocalTime.of(13, 44, 59)));
        assertFalse(SleepHours.contains(true, 735, 825, LocalTime.of(13, 45)));
        assertFalse(SleepHours.contains(true, 735, 825, LocalTime.MIDNIGHT));
    }

    @Test
    public void midnightCanBeEitherBoundary() {
        assertTrue(SleepHours.contains(true, 0, 360, LocalTime.MIDNIGHT));
        assertFalse(SleepHours.contains(true, 0, 360, LocalTime.of(23, 59)));
        assertTrue(SleepHours.contains(true, 1320, 0, LocalTime.of(23, 59)));
        assertFalse(SleepHours.contains(true, 1320, 0, LocalTime.MIDNIGHT));
    }

    @Test
    public void equalTimesDoNotSilenceTheWholeDay() {
        assertFalse(SleepHours.contains(true, 360, 360, LocalTime.of(6, 0)));
        assertFalse(SleepHours.contains(true, 360, 360, LocalTime.NOON));
    }
}
