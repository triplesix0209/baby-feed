package triplesix.babyfeed;

import org.json.*;

import java.io.IOException;
import java.util.*;

/** Each installation owns one archive. Events remain immutable and merge by UUID. */
final class SyncArchive {
    interface Local {
        List<JSONObject> all();

        void insert(JSONObject event) throws Exception;

        default void importEvents(Collection<JSONObject> events) throws Exception {
            for (JSONObject event : events) {
                insert(event);
            }
        }

        default void stateChanged() {}

        void uploaded(String id) throws Exception;

        default void prune(Set<String> ids) throws Exception {}
    }

    interface Remote {
        List<JSONObject> list() throws Exception;

        JSONObject read(String id) throws Exception;

        String write(String id, String name, JSONObject body) throws Exception;

        void trash(String id) throws Exception;
    }

    static final String UUID_PATTERN =
            "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";

    static String name(String device) {
        return "babyfeed-device-" + device + ".json";
    }

    static final int MAX_BYTES = 256 * 1024, MAX_EVENTS = 512;
    static final Comparator<JSONObject> ORDER =
            Comparator.comparingLong((JSONObject e) -> e.optLong("created"))
                    .thenComparing(e -> e.optString("id"));

    static JSONObject archive(String device, Collection<JSONObject> entries) throws Exception {
        return new JSONObject()
                .put("app", "BabyFeed")
                .put("schema", 1)
                .put("device", device)
                .put("events", new JSONArray(entries));
    }

    static int bytes(JSONObject value) {
        return value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    static Map<String, JSONObject> compact(Collection<JSONObject> input) throws Exception {
        List<JSONObject> sorted = new ArrayList<>(input);
        sorted.sort(ORDER);
        Map<String, JSONObject> history = new LinkedHashMap<>(),
                vitamins = new LinkedHashMap<>(),
                result = new LinkedHashMap<>();
        Set<String> deleted = new HashSet<>();
        JSONObject latest = null;
        for (JSONObject event : sorted) {
            String kind = event.getString("kind");
            if (VitaminRecords.isVitamin(event)) {
                vitamins.put(event.getString("day"), event);
            } else if (kind.equals("deleted")) {
                JSONArray feeds = event.getJSONArray("feeds");
                for (int i = 0; i < feeds.length(); i++) deleted.add(feeds.getString(i));
                result.put(event.getString("id"), event);
            } else {
                if (!kind.equals("recorded")) latest = event;
                if (kind.equals("finished") || kind.equals("recorded"))
                    history.put(event.getString("feed"), event);
            }
        }
        for (Map.Entry<String, JSONObject> entry : history.entrySet())
            if (!deleted.contains(entry.getKey()))
                result.put(entry.getValue().getString("id"), entry.getValue());
        for (JSONObject vitamin : vitamins.values()) result.put(vitamin.getString("id"), vitamin);
        if (latest != null) result.put(latest.getString("id"), latest);
        return result;
    }

    static Map<String, JSONObject> shards(String device, Collection<JSONObject> input)
            throws Exception {
        Map<String, List<JSONObject>> years = new TreeMap<>();
        for (JSONObject e : input) {
            String year =
                    String.valueOf(
                            java.time.Instant.ofEpochMilli(e.getLong("created"))
                                    .atZone(java.time.ZoneOffset.UTC)
                                    .getYear());
            years.computeIfAbsent(year, k -> new ArrayList<>()).add(e);
        }
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<JSONObject>> year : years.entrySet()) {
            year.getValue().sort(ORDER);
            List<JSONObject> page = new ArrayList<>();
            int part = 1;
            int size = bytes(archive(device, Collections.emptyList()));
            for (JSONObject e : year.getValue()) {
                int added = bytes(e) + (page.isEmpty() ? 0 : 1);
                if (!page.isEmpty() && (page.size() >= MAX_EVENTS || size + added > MAX_BYTES)) {
                    result.put(
                            "babyfeed-v1-" + device + "-" + year.getKey() + "-" + part++ + ".json",
                            archive(device, page));
                    page = new ArrayList<>();
                    size = bytes(archive(device, Collections.emptyList()));
                    added = bytes(e);
                }
                if (size + added > MAX_BYTES)
                    throw new IOException(
                            "Một bản ghi vượt giới hạn 256 KiB; dữ liệu gốc được giữ nguyên.");
                page.add(e);
                size += added;
            }
            if (!page.isEmpty())
                result.put(
                        "babyfeed-v1-" + device + "-" + year.getKey() + "-" + part + ".json",
                        archive(device, page));
        }
        return result;
    }

    static Map<String, JSONObject> events(JSONObject archive) throws Exception {
        if (!"BabyFeed".equals(archive.optString("app"))
                || (archive.optInt("schema") != 1
                        && archive.optInt("schema") != 2
                        && archive.optInt("schema") != 3))
            throw new IOException("Định dạng đồng bộ chưa được hỗ trợ. Hãy cập nhật BabyFeed.");
        Map<String, JSONObject> result = new LinkedHashMap<>();
        JSONArray entries = archive.getJSONArray("events");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject event = entries.getJSONObject(i);
            String id = event.getString("id");
            if (!id.matches(UUID_PATTERN) || result.containsKey(id))
                throw new IOException("Mã bản ghi đồng bộ không hợp lệ hoặc bị trùng.");
            result.put(id, event);
        }
        return result;
    }

    static boolean same(JSONObject a, JSONObject b) throws Exception {
        if (a.length() != b.length()) return false;
        Iterator<String> keys = a.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!b.has(key)) return false;
            Object x = a.get(key), y = b.get(key);
            if (x instanceof JSONObject && y instanceof JSONObject) {
                if (!same((JSONObject) x, (JSONObject) y)) return false;
            } else if (x instanceof JSONArray && y instanceof JSONArray) {
                JSONArray left = (JSONArray) x, right = (JSONArray) y;
                if (left.length() != right.length()) return false;
                for (int i = 0; i < left.length(); i++) {
                    Object l = left.get(i), r = right.get(i);
                    if (l instanceof JSONObject && r instanceof JSONObject) {
                        if (!same((JSONObject) l, (JSONObject) r)) return false;
                    } else if (!l.toString().equals(r.toString())) return false;
                }
            } else if (!x.toString().equals(y.toString())) return false;
        }
        return true;
    }

    static void merge(Map<String, JSONObject> target, JSONObject event) throws Exception {
        String id = event.getString("id");
        JSONObject previous = target.get(id);
        if (previous != null && !same(previous, event))
            throw new IOException(
                    "Hai bản ghi cùng mã có nội dung khác nhau; giữ nguyên dữ liệu Drive.");
        target.put(id, event);
    }

    // Returns the number of old files that this account cannot move to Trash.
    static int sync(String device, Local local, Remote remote) throws Exception {
        Map<String, JSONObject> merged = new LinkedHashMap<>();
        for (JSONObject event : local.all()) merge(merged, event);
        List<JSONObject> legacy = new ArrayList<>();
        Map<String, JSONObject> ownFiles = new LinkedHashMap<>(), ownBodies = new HashMap<>();
        for (JSONObject file : remote.list()) {
            String fileName = file.getString("name"), id = file.getString("id");
            if (fileName.matches("babyfeed-" + UUID_PATTERN + "\\.json")) {
                JSONObject event = remote.read(id);
                if (!fileName.equals("babyfeed-" + event.getString("id") + ".json"))
                    throw new IOException("Tên và nội dung bản ghi không khớp.");
                merge(merged, event);
                legacy.add(file);
            } else if (fileName.matches("babyfeed-device-" + UUID_PATTERN + "\\.json")
                    || fileName.matches("babyfeed-v1-" + UUID_PATTERN + "-[0-9]+-[0-9]+\\.json")
                    || fileName.matches("babyfeed-v3-" + UUID_PATTERN + "-[0-9]+-[0-9]+\\.json")) {
                JSONObject archive = remote.read(id);
                String writer = archive.getString("device");
                boolean current =
                        fileName.matches("babyfeed-v1-" + UUID_PATTERN + "-[0-9]+-[0-9]+\\.json");
                boolean v3 =
                        fileName.matches("babyfeed-v3-" + UUID_PATTERN + "-[0-9]+-[0-9]+\\.json");
                if (!(current || v3
                        ? fileName.startsWith("babyfeed-v" + (current ? 1 : 3) + "-" + writer + "-")
                                && archive.optInt("schema") == (current ? 1 : 3)
                        : fileName.equals(name(writer))))
                    throw new IOException("Tên và thiết bị trong dữ liệu không khớp.");
                Map<String, JSONObject> entries = events(archive);
                for (JSONObject event : entries.values()) merge(merged, event);
                if (!current) legacy.add(file);
                else if (writer.equals(device)) {
                    JSONObject duplicate = ownFiles.put(fileName, file);
                    if (duplicate != null) legacy.add(duplicate);
                    ownBodies.put(fileName, archive);
                }
            }
        }
        // Validate the entire batch before changing the local database or Drive.
        for (JSONObject event : merged.values()) {
            try {
                EventRules.validate(event);
            } catch (IllegalArgumentException error) {
                throw new IOException(error.getMessage(), error);
            }
        }
        Map<String, JSONObject> retained = compact(merged.values());
        Map<String, JSONObject> pages = shards(device, retained.values());
        local.importEvents(merged.values());
        // Reconcile alarms immediately; later network operations may fail or be cancelled.
        local.stateChanged();
        for (Map.Entry<String, JSONObject> page : pages.entrySet()) {
            String fileName = page.getKey();
            JSONObject body = page.getValue(), old = ownBodies.get(fileName);
            if (old != null && same(old, body)) continue;
            String id =
                    remote.write(
                            ownFiles.containsKey(fileName)
                                    ? ownFiles.get(fileName).getString("id")
                                    : null,
                            fileName,
                            body);
            if (!same(body, remote.read(id)))
                throw new IOException("Bản lưu Drive chưa khớp dữ liệu.");
        }
        for (Map.Entry<String, JSONObject> file : ownFiles.entrySet())
            if (!pages.containsKey(file.getKey())) legacy.add(file.getValue());
        for (String id : merged.keySet()) local.uploaded(id);
        Set<String> obsolete = new HashSet<>(merged.keySet());
        obsolete.removeAll(retained.keySet());
        local.prune(obsolete);
        int remaining = 0;
        for (JSONObject file : legacy) {
            if (file.optJSONObject("capabilities") != null
                    && file.getJSONObject("capabilities").optBoolean("canTrash"))
                remote.trash(file.getString("id"));
            else remaining++;
        }
        return remaining;
    }
}
