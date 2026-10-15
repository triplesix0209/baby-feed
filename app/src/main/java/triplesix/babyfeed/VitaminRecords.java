package triplesix.babyfeed;

import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Collection;

/** One shared daily status; the most recent change wins, including undo. */
final class VitaminRecords {
    static final String KIND = "vitamin";

    static boolean isVitamin(JSONObject event) {
        return KIND.equals(event.optString("kind"));
    }

    static void validate(JSONObject event) {
        try {
            String day = event.getString("day");
            if (!LocalDate.parse(day).toString().equals(day)
                    || !(event.get("taken") instanceof Boolean)
                    || (event.getBoolean("taken")
                            ? event.getLong("takenAt") <= 0
                            : event.getLong("takenAt") != 0)) throw new IllegalArgumentException();
        } catch (Exception error) {
            throw new IllegalArgumentException("Dữ liệu không hợp lệ", error);
        }
    }

    static JSONObject latest(Collection<JSONObject> events, LocalDate day) {
        JSONObject result = null;
        for (JSONObject event : events)
            if (isVitamin(event)
                    && day.toString().equals(event.optString("day"))
                    && (result == null || SyncArchive.ORDER.compare(event, result) > 0))
                result = event;
        return result;
    }
}
