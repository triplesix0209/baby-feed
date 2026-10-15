package triplesix.babyfeed;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

import java.time.LocalDate;
import java.util.*;

public class VitaminRecordsTest {
    static JSONObject status(String day, boolean taken, long created) throws Exception {
        return new JSONObject()
                .put("schema", 1)
                .put("id", UUID.randomUUID().toString())
                .put("created", created)
                .put("kind", VitaminRecords.KIND)
                .put("day", day)
                .put("taken", taken)
                .put("takenAt", taken ? 1000 : 0);
    }

    @Test
    public void dailyStatusUsesLatestChangeAndKeepsUndo() throws Exception {
        LocalDate day = LocalDate.of(2026, 10, 15);
        JSONObject first = status(day.toString(), true, 1),
                undo = status(day.toString(), false, 2),
                other = status("2026-10-14", true, 3);
        List<JSONObject> events = Arrays.asList(other, undo, first);
        assertSame(undo, VitaminRecords.latest(events, day));
        assertSame(other, VitaminRecords.latest(events, day.minusDays(1)));
        assertNull(VitaminRecords.latest(events, day.plusDays(1)));
        JSONObject again = status(day.toString(), true, 4);
        assertSame(again, VitaminRecords.latest(Arrays.asList(again, undo, first), day));
    }

    @Test
    public void tiesResolveByIdRegardlessOfInputOrder() throws Exception {
        JSONObject first = status("2026-10-15", true, 1).put("id", "a"),
                second = status("2026-10-15", false, 1).put("id", "b");
        assertSame(
                second,
                VitaminRecords.latest(Arrays.asList(second, first), LocalDate.of(2026, 10, 15)));
    }

    @Test
    public void rejectsInvalidDailyRecords() throws Exception {
        JSONObject valid = status("2026-10-15", true, 1);
        VitaminRecords.validate(valid);
        VitaminRecords.validate(status("2026-10-15", false, 1));
        for (JSONObject invalid :
                Arrays.asList(
                        new JSONObject(valid.toString()).put("day", "2026-02-30"),
                        new JSONObject(valid.toString()).put("taken", "true"),
                        new JSONObject(valid.toString()).put("takenAt", 0),
                        status("2026-10-15", false, 1).put("takenAt", 1))) {
            assertThrows(IllegalArgumentException.class, () -> VitaminRecords.validate(invalid));
        }
    }

    @Test
    public void syncPreservesScheduleAndUndoAcrossOfflineMerge() throws Exception {
        SyncArchiveTest.Local a = new SyncArchiveTest.Local(), b = new SyncArchiveTest.Local();
        SyncArchiveTest.Remote remote = new SyncArchiveTest.Remote();
        JSONObject schedule = SyncArchiveTest.event().put("kind", "scheduled").put("created", 1);
        JSONObject taken = status("2026-10-15", true, 2),
                undo = status("2026-10-15", false, 3),
                yesterday = status("2026-10-14", true, 4);
        a.insert(schedule);
        a.insert(taken);
        b.insert(taken);
        SyncArchive.sync(SyncArchiveTest.A, a, remote);
        a.insert(undo);
        a.insert(yesterday);
        SyncArchive.sync(SyncArchiveTest.A, a, remote);
        SyncArchive.sync(SyncArchiveTest.B, b, remote);
        SyncArchive.sync(SyncArchiveTest.A, a, remote);
        assertEquals(a.data.keySet(), b.data.keySet());
        assertEquals(3, a.data.size());
        assertTrue(a.data.containsKey(schedule.getString("id")));
        assertFalse(a.data.containsKey(taken.getString("id")));
        assertFalse(VitaminRecords.latest(b.all(), LocalDate.of(2026, 10, 15)).getBoolean("taken"));
    }
}
