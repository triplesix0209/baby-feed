package triplesix.babyfeed;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

public class SyncValidationTest {
    @Test
    public void oversizedRemoteRecordDoesNotPoisonFutureSync() throws Exception {
        SyncArchiveTest.Local local = new SyncArchiveTest.Local();
        SyncArchiveTest.Remote remote = new SyncArchiveTest.Remote();
        JSONObject event = SyncArchiveTest.event().put("note", "x".repeat(SyncArchive.MAX_BYTES));
        String file = remote.add("babyfeed-" + event.getString("id") + ".json", event, true);
        assertThrows(IOException.class, () -> SyncArchive.sync(SyncArchiveTest.A, local, remote));
        assertTrue(local.data.isEmpty());
        assertEquals(0, remote.writes);
        assertTrue(remote.files.containsKey(file));
        remote.trash(file);
        SyncArchive.sync(SyncArchiveTest.A, local, remote);
        assertTrue(local.data.isEmpty());
    }

    @Test
    public void invalidLastRecordDoesNotPartiallyImportEarlierRecords() throws Exception {
        SyncArchiveTest.Local local = new SyncArchiveTest.Local();
        SyncArchiveTest.Remote remote = new SyncArchiveTest.Remote();
        JSONObject good = SyncArchiveTest.event();
        JSONObject invalid = SyncArchiveTest.event().put("consumed", 2000);
        remote.add("babyfeed-" + good.getString("id") + ".json", good, true);
        remote.add("babyfeed-" + invalid.getString("id") + ".json", invalid, true);
        assertThrows(IOException.class, () -> SyncArchive.sync(SyncArchiveTest.A, local, remote));
        assertTrue(local.data.isEmpty());
        assertEquals(0, remote.writes);
        assertEquals(2, remote.files.size());
    }
}
