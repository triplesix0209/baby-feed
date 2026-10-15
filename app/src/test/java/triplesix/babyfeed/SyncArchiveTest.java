package triplesix.babyfeed;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

public class SyncArchiveTest {
    static final String A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    static JSONObject event() throws Exception {
        String id = UUID.randomUUID().toString();
        return new JSONObject()
                .put("id", id)
                .put("feed", id)
                .put("schema", 1)
                .put("kind", "recorded")
                .put("ml", 120)
                .put("preparedMl", 120)
                .put("left", 20)
                .put("consumed", 100)
                .put("preparedAt", 1)
                .put("finishedAt", 1)
                .put("nextAt", 2)
                .put("created", 1);
    }

    static class Local implements SyncArchive.Local {
        int stateChanges;
        Map<String, JSONObject> data = new LinkedHashMap<>();
        Set<String> uploaded = new HashSet<>();

        public List<JSONObject> all() {
            return new ArrayList<>(data.values());
        }

        public void insert(JSONObject e) {
            data.put(e.optString("id"), e);
        }

        public void stateChanged() {
            stateChanges++;
        }

        public void uploaded(String id) {
            uploaded.add(id);
        }

        public void prune(Set<String> ids) {
            for (String id : ids) data.remove(id);
        }
    }

    static class Remote implements SyncArchive.Remote {
        Map<String, JSONObject> files = new LinkedHashMap<>(), bodies = new HashMap<>();
        int writes = 0, failAt = 0;
        boolean failWrite = false, corruptRead = false;

        String add(String name, JSONObject body, boolean canTrash) throws Exception {
            String id = UUID.randomUUID().toString();
            files.put(
                    id,
                    new JSONObject()
                            .put("id", id)
                            .put("name", name)
                            .put("capabilities", new JSONObject().put("canTrash", canTrash)));
            bodies.put(id, body);
            return id;
        }

        public List<JSONObject> list() {
            return new ArrayList<>(files.values());
        }

        public JSONObject read(String id) throws Exception {
            JSONObject body = new JSONObject(bodies.get(id).toString());
            if (corruptRead && writes > 0 && body.optInt("schema") >= 2)
                body.put("events", new JSONArray());
            return body;
        }

        public String write(String id, String name, JSONObject body) throws Exception {
            writes++;
            if (failWrite || writes == failAt) throw new IOException("offline");
            if (id == null) return add(name, body, true);
            bodies.put(id, body);
            return id;
        }

        public void trash(String id) {
            files.remove(id);
            bodies.remove(id);
        }
    }

    @Test
    public void migratesAndDoesNotUploadAgain() throws Exception {
        Local local = new Local();
        Remote remote = new Remote();
        for (int i = 0; i < 50; i++) {
            JSONObject e = event();
            remote.add("babyfeed-" + e.getString("id") + ".json", e, true);
        }
        SyncArchive.sync(A, local, remote);
        assertEquals(50, local.data.size());
        assertEquals(1, remote.files.size());
        assertEquals(1, remote.writes);
        SyncArchive.sync(A, local, remote);
        assertEquals(1, remote.writes);
    }

    @Test
    public void twoDevicesConvergeAndKeepOfflineEvents() throws Exception {
        Local a = new Local(), b = new Local();
        Remote remote = new Remote();
        a.insert(event());
        b.insert(event());
        SyncArchive.sync(A, a, remote);
        SyncArchive.sync(B, b, remote);
        SyncArchive.sync(A, a, remote);
        a.insert(event());
        b.insert(event());
        SyncArchive.sync(A, a, remote);
        SyncArchive.sync(B, b, remote);
        SyncArchive.sync(A, a, remote);
        assertEquals(4, a.data.size());
        assertEquals(a.data.keySet(), b.data.keySet());
        assertEquals(2, remote.files.size());
    }

    @Test
    public void uploadFailureLeavesLegacyAndRetries() throws Exception {
        Local local = new Local();
        Remote remote = new Remote();
        JSONObject e = event();
        String old = remote.add("babyfeed-" + e.getString("id") + ".json", e, true);
        remote.failWrite = true;
        assertThrows(IOException.class, () -> SyncArchive.sync(A, local, remote));
        assertTrue(remote.files.containsKey(old));
        assertTrue(local.uploaded.isEmpty());
        assertEquals(1, local.stateChanges);
        remote.failWrite = false;
        SyncArchive.sync(A, local, remote);
        assertFalse(remote.files.containsKey(old));
    }

    @Test
    public void failedVerificationNeverTrashesSource() throws Exception {
        Local local = new Local();
        Remote remote = new Remote();
        JSONObject e = event();
        String old = remote.add("babyfeed-" + e.getString("id") + ".json", e, true);
        remote.corruptRead = true;
        assertThrows(IOException.class, () -> SyncArchive.sync(A, local, remote));
        assertTrue(remote.files.containsKey(old));
        assertTrue(local.uploaded.isEmpty());
    }

    @Test
    public void preservesUnrelatedFilesAndReportsCleanupPermission() throws Exception {
        Local local = new Local();
        Remote remote = new Remote();
        JSONObject e = event();
        String old = remote.add("babyfeed-" + e.getString("id") + ".json", e, false);
        String other = remote.add("personal.json", new JSONObject(), true);
        assertEquals(1, SyncArchive.sync(A, local, remote));
        assertTrue(remote.files.containsKey(old));
        assertTrue(remote.files.containsKey(other));
    }

    @Test
    public void conflictingUuidStopsBeforeUploadOrCleanup() throws Exception {
        Local local = new Local();
        Remote remote = new Remote();
        JSONObject e = event();
        local.insert(e);
        JSONObject conflict = new JSONObject(e.toString()).put("created", 2);
        remote.add("babyfeed-" + e.getString("id") + ".json", conflict, true);
        assertThrows(IOException.class, () -> SyncArchive.sync(A, local, remote));
        assertEquals(0, remote.writes);
        assertEquals(1, remote.files.size());
    }

    @Test
    public void newFolderGetsAllEventsEvenWhenPreviouslyUploaded() throws Exception {
        Local local = new Local();
        local.insert(event());
        SyncArchive.sync(A, local, new Remote());
        Remote next = new Remote();
        SyncArchive.sync(A, local, next);
        assertEquals(1, next.files.size());
        assertEquals(1, next.writes);
    }

    @Test
    public void compactionPreservesHistoryLatestScheduleAndDeletionAcrossOfflineMerge()
            throws Exception {
        Local a = new Local(), b = new Local();
        Remote remote = new Remote();
        JSONObject record = event();
        a.insert(record);
        b.insert(record);
        for (int i = 1; i <= 100; i++) a.insert(event().put("kind", "scheduled").put("created", i));
        JSONObject latest = event().put("kind", "cancelled").put("created", 101);
        a.insert(latest);
        a.insert(
                event().put("kind", "deleted")
                        .put("created", 102)
                        .put("feeds", new JSONArray().put(record.getString("feed"))));
        SyncArchive.sync(A, a, remote);
        assertEquals(2, a.data.size());
        assertTrue(a.data.containsKey(latest.getString("id")));
        SyncArchive.sync(B, b, remote);
        SyncArchive.sync(A, a, remote);
        assertEquals(a.data.keySet(), b.data.keySet());
        assertFalse(b.data.containsKey(record.getString("id")));
    }

    @Test
    public void longHistoryHasBoundedFilesAndNoRewriteWhenUnchanged() throws Exception {
        Local local = new Local();
        Remote remote = new Remote();
        for (int i = 0; i < 10000; i++)
            local.insert(
                    event().put(
                                    "created",
                                    java.time.Instant.parse("2020-01-01T00:00:00Z").toEpochMilli()
                                            + i * 4 * 3600000L));
        SyncArchive.sync(A, local, remote);
        assertEquals(10000, local.data.size());
        assertTrue(remote.files.size() < 30);
        for (JSONObject body : remote.bodies.values()) {
            assertTrue(SyncArchive.bytes(body) <= SyncArchive.MAX_BYTES);
            assertTrue(body.getJSONArray("events").length() <= SyncArchive.MAX_EVENTS);
        }
        int writes = remote.writes;
        SyncArchive.sync(A, local, remote);
        assertEquals(writes, remote.writes);
        local.insert(
                event().put(
                                "created",
                                java.time.Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()));
        SyncArchive.sync(A, local, remote);
        assertEquals(writes + 1, remote.writes);
    }

    @Test
    public void byteLimitCountsUtf8AndOversizedEventIsNotDropped() throws Exception {
        List<JSONObject> entries = new ArrayList<>();
        String text = String.join("", Collections.nCopies(4000, "ữa"));
        for (int i = 0; i < 40; i++) entries.add(event().put("note", text));
        Map<String, JSONObject> shards = SyncArchive.shards(A, entries);
        assertTrue(shards.size() > 1);
        for (JSONObject body : shards.values())
            assertTrue(SyncArchive.bytes(body) <= SyncArchive.MAX_BYTES);
        Local local = new Local();
        Remote remote = new Remote();
        local.insert(event().put("note", String.join("", Collections.nCopies(100000, "ữa"))));
        assertThrows(IOException.class, () -> SyncArchive.sync(A, local, remote));
        assertEquals(1, local.data.size());
        assertEquals(0, remote.writes);
    }

    @Test
    public void migrationOfV2IsVerifiedBeforeRemovalAndPruning() throws Exception {
        Local local = new Local();
        Remote remote = new Remote();
        JSONObject first = event().put("kind", "scheduled"),
                latest = event().put("kind", "cancelled").put("created", 2);
        String old =
                remote.add(
                        SyncArchive.name(A),
                        new JSONObject()
                                .put("app", "BabyFeed")
                                .put("schema", 2)
                                .put("device", A)
                                .put("events", new JSONArray().put(first).put(latest)),
                        true);
        remote.failWrite = true;
        assertThrows(IOException.class, () -> SyncArchive.sync(A, local, remote));
        assertTrue(remote.files.containsKey(old));
        assertEquals(2, local.data.size());
        remote.failWrite = false;
        SyncArchive.sync(A, local, remote);
        assertFalse(remote.files.containsKey(old));
        assertEquals(1, local.data.size());
        assertTrue(local.data.containsKey(latest.getString("id")));
    }

    @Test
    public void interruptedMultiFileMigrationKeepsSourceAndCanResume() throws Exception {
        Local local = new Local();
        Remote remote = new Remote();
        JSONArray entries = new JSONArray();
        for (int i = 0; i < 600; i++) entries.put(event());
        String old =
                remote.add(
                        SyncArchive.name(A),
                        new JSONObject()
                                .put("app", "BabyFeed")
                                .put("schema", 2)
                                .put("device", A)
                                .put("events", entries),
                        true);
        remote.failAt = 2;
        assertThrows(IOException.class, () -> SyncArchive.sync(A, local, remote));
        assertTrue(remote.files.containsKey(old));
        assertEquals(600, local.data.size());
        assertTrue(local.uploaded.isEmpty());
        remote.failAt = 0;
        SyncArchive.sync(A, local, remote);
        assertFalse(remote.files.containsKey(old));
        assertEquals(600, local.data.size());
        assertEquals(2, remote.files.size());
    }
}
