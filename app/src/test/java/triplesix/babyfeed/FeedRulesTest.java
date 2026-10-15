package triplesix.babyfeed;

import static org.junit.Assert.*;

import org.junit.Test;

public class FeedRulesTest {
    @Test
    public void backfillUsesConsumedAmountWithoutSubtractingDiscardedAgain() {
        int prepared = FeedRules.preparedFromConsumed(100, 20);
        assertEquals(120, prepared);
        assertEquals(100, FeedRules.consumed(prepared, 20));
        assertEquals(20, FeedRules.preparedFromConsumed(0, 20));
        assertThrows(IllegalArgumentException.class, () -> FeedRules.preparedFromConsumed(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> FeedRules.preparedFromConsumed(100, -1));
        assertThrows(IllegalArgumentException.class, () -> FeedRules.preparedFromConsumed(0, 0));
        assertThrows(IllegalArgumentException.class, () -> FeedRules.preparedFromConsumed(2000, 1));
    }

    @Test
    public void countdownRoundsUp() {
        assertEquals("(3p tới)", FeedRules.countdown(121000));
        assertEquals("(1h 30p tới)", FeedRules.countdown(5400000));
        assertEquals("(đã tới giờ)", FeedRules.countdown(-1));
    }

    @Test
    public void separateHourMinuteInputs() {
        assertEquals(90, FeedRules.interval(1, 30));
        assertEquals(0, FeedRules.interval(0, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidMinutePart() {
        FeedRules.interval(1, 60);
    }

    @Test
    public void parsesHoursAndMinutes() {
        assertEquals(90, FeedRules.duration("1h30p"));
        assertEquals(120, FeedRules.duration("2h"));
        assertEquals(45, FeedRules.duration("45p"));
        assertEquals(90, FeedRules.duration(" 1H 30P "));
    }

    @Test
    public void formatsSavedMinutes() {
        assertEquals("3h", FeedRules.durationLabel(180));
        assertEquals("1h30p", FeedRules.durationLabel(90));
        assertEquals("45p", FeedRules.durationLabel(45));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBareMinutes() {
        FeedRules.duration("180");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedDuration() {
        FeedRules.duration("1h90p");
    }

    @Test
    public void allowsContinuousFeeds() {
        assertEquals(0, FeedRules.duration("0p"));
        assertEquals(1, FeedRules.duration("1p"));
        assertEquals(10, FeedRules.duration("10p"));
        assertEquals(1000, FeedRules.next(1000, 0));
        assertEquals("0p", FeedRules.durationLabel(0));
    }

    @Test
    public void recordsPartialBottle() {
        assertEquals(95, FeedRules.consumed(120, 25));
    }

    @Test
    public void recordsUntouchedBottle() {
        assertEquals(0, FeedRules.consumed(120, 120));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsImpossibleLeftover() {
        FeedRules.consumed(120, 121);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeLeftover() {
        FeedRules.consumed(120, -1);
    }

    @Test
    public void nextFeedStartsAtCompletion() {
        assertEquals(10801000L, FeedRules.next(1000, 180));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeInterval() {
        FeedRules.next(1000, -1);
    }
}
