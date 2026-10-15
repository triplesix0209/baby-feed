package triplesix.babyfeed;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.util.*;

public final class Store extends SQLiteOpenHelper {
    final Context context;

    public Store(Context c) {
        super(c, "feeds.db", null, 1);
        context = c.getApplicationContext();
    }

    public SharedPreferences prefs() {
        return context.getSharedPreferences("settings", 0);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE events(id TEXT PRIMARY KEY, body TEXT NOT NULL, uploaded INTEGER NOT"
                        + " NULL DEFAULT 0)");
    }

    public void onUpgrade(SQLiteDatabase db, int old, int version) {}

    public synchronized void insert(JSONObject event, boolean uploaded) {
        EventRules.validate(event);
        android.content.ContentValues v = new android.content.ContentValues();
        v.put("id", event.optString("id"));
        v.put("body", event.toString());
        v.put("uploaded", uploaded ? 1 : 0);
        getWritableDatabase()
                .insertWithOnConflict("events", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** The transaction serializes the check with imports through other Store instances. */
    public synchronized boolean insertIfCurrent(JSONObject event, String expectedId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            JSONObject current = latest();
            String currentId = current == null ? "" : current.optString("id");
            if (!currentId.equals(expectedId)) {
                return false;
            }
            if (current != null) {
                event.put(
                        "created",
                        Math.max(
                                event.getLong("created"),
                                Math.addExact(current.getLong("created"), 1)));
            }
            insert(event, false);
            db.setTransactionSuccessful();
            return true;
        } catch (org.json.JSONException error) {
            throw new IllegalStateException(error);
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void importEvents(Collection<JSONObject> events) {
        for (JSONObject event : events) {
            EventRules.validate(event);
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (JSONObject event : events) {
                insert(event, true);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public JSONObject event(String kind) {
        try {
            return new JSONObject()
                    .put("schema", 1)
                    .put("id", UUID.randomUUID().toString())
                    .put("created", System.currentTimeMillis())
                    .put("kind", kind);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public List<JSONObject> all(boolean pending) {
        List<JSONObject> result = new ArrayList<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT body FROM events" + (pending ? " WHERE uploaded=0" : ""),
                                null)) {
            while (c.moveToNext()) {
                try {
                    result.add(new JSONObject(c.getString(0)));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        result.sort(
                Comparator.comparingLong((JSONObject e) -> e.optLong("created"))
                        .thenComparing(e -> e.optString("id")));
        return result;
    }

    public JSONObject latest() {
        List<JSONObject> events = all(false);
        for (int i = events.size() - 1; i >= 0; i--)
            if (!Arrays.asList("recorded", "deleted", VitaminRecords.KIND)
                    .contains(events.get(i).optString("kind"))) return events.get(i);
        return null;
    }

    public JSONObject vitamin(java.time.LocalDate day) {
        return VitaminRecords.latest(all(false), day);
    }

    public synchronized void deleteRecords(Set<String> feeds, Set<String> vitaminDays) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (!feeds.isEmpty())
                insert(event("deleted").put("feeds", new org.json.JSONArray(feeds)), false);
            for (String day : vitaminDays) {
                JSONObject previous = vitamin(java.time.LocalDate.parse(day)),
                        removed = event(VitaminRecords.KIND);
                if (previous != null)
                    removed.put(
                            "created",
                            Math.max(
                                    removed.optLong("created"),
                                    Math.addExact(previous.optLong("created"), 1)));
                insert(removed.put("day", day).put("taken", false).put("takenAt", 0), false);
            }
            db.setTransactionSuccessful();
        } catch (org.json.JSONException error) {
            throw new IllegalStateException(error);
        } finally {
            db.endTransaction();
        }
    }

    public Set<String> deletedFeeds() {
        Set<String> ids = new HashSet<>();
        for (JSONObject e : all(false))
            if (e.optString("kind").equals("deleted")) {
                org.json.JSONArray a = e.optJSONArray("feeds");
                if (a != null) for (int i = 0; i < a.length(); i++) ids.add(a.optString(i));
            }
        return ids;
    }

    public void uploaded(String id) {
        getWritableDatabase().execSQL("UPDATE events SET uploaded=1 WHERE id=?", new Object[] {id});
    }

    // Delete only the captured obsolete IDs; concurrent UI inserts are never swept up.
    public void prune(Set<String> ids) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String id : ids) db.delete("events", "id=?", new String[] {id});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public boolean completed(String feed) {
        for (JSONObject e : all(false))
            if (e.optString("kind").equals("finished") && e.optString("feed").equals(feed))
                return true;
        return false;
    }

    public List<JSONObject> feeds(java.time.LocalDate day) {
        List<JSONObject> events = all(false), result = new ArrayList<>();
        Collections.reverse(events);
        Set<String> seen = new HashSet<>(deletedFeeds());
        long start = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                end =
                        day.plusDays(1)
                                .atStartOfDay(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli();
        for (JSONObject e : events)
            if ((e.optString("kind").equals("finished") || e.optString("kind").equals("recorded"))
                    && seen.add(e.optString("feed"))
                    && e.optLong("finishedAt") >= start
                    && e.optLong("finishedAt") < end) result.add(e);
        result.sort(Comparator.comparingLong((JSONObject e) -> e.optLong("preparedAt")).reversed());
        return result;
    }
}
