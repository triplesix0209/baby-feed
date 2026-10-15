package triplesix.babyfeed;

import org.json.JSONObject;

import java.util.Arrays;

/** Shared validation for local writes and untrusted Drive imports. */
final class EventRules {
    static void validate(JSONObject event) {
        if (event.optInt("schema") != 1
                || !event.optString("id").matches(SyncArchive.UUID_PATTERN)
                || event.optLong("created") <= 0)
            throw new IllegalArgumentException("Dữ liệu không hợp lệ");
        String kind = event.optString("kind");
        if (!Arrays.asList(
                        "scheduled",
                        "prepared",
                        "finished",
                        "recorded",
                        "deleted",
                        "cancelled",
                        VitaminRecords.KIND)
                .contains(kind)) throw new IllegalArgumentException("Loại bản ghi không hợp lệ");
        if (VitaminRecords.isVitamin(event)) VitaminRecords.validate(event);
        if (kind.equals("deleted")
                && (event.optJSONArray("feeds") == null
                        || event.optJSONArray("feeds").length() == 0))
            throw new IllegalArgumentException("Chưa chọn cữ cần xóa");
        if (!kind.equals("cancelled")
                && !kind.equals("deleted")
                && !VitaminRecords.isVitamin(event)) FeedRules.consumed(event.optInt("ml"), 0);
        if (kind.equals("prepared") && event.optLong("preparedAt") <= 0)
            throw new IllegalArgumentException("Thiếu giờ pha");
        if ((kind.equals("scheduled") || kind.equals("finished")) && event.optLong("nextAt") <= 0)
            throw new IllegalArgumentException("Thiếu giờ cữ tiếp theo");
        if (kind.equals("finished") || kind.equals("recorded")) {
            int drank = FeedRules.consumed(event.optInt("preparedMl"), event.optInt("left", -1));
            if (drank != event.optInt("consumed", -1)
                    || event.optString("feed").isEmpty()
                    || event.optLong("finishedAt") <= 0
                    || event.optLong("preparedAt") <= 0)
                throw new IllegalArgumentException("Kết quả cữ sữa không hợp lệ");
        }

        int envelope = 160;
        if (SyncArchive.bytes(event) > SyncArchive.MAX_BYTES - envelope) {
            throw new IllegalArgumentException(
                    "Một bản ghi vượt giới hạn 256 KiB; dữ liệu gốc được giữ nguyên.");
        }
    }
}
