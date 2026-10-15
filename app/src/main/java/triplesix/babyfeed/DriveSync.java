package triplesix.babyfeed;

import android.content.Context;

import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;

import org.json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class DriveSync {
    private static final SyncSession SESSIONS = new SyncSession();
    private static final ThreadLocal<SyncSession.Ticket> HTTP_SESSION = new ThreadLocal<>();

    static SyncSession.Ticket session() {
        return SESSIONS.capture();
    }

    static <T> T withSession(SyncSession.Ticket session, SyncSession.Action<T> action)
            throws Exception {
        session.check();
        SyncSession.Ticket previous = HTTP_SESSION.get();
        HTTP_SESSION.set(session);
        try {
            return action.run();
        } finally {
            if (previous == null) HTTP_SESSION.remove();
            else HTTP_SESSION.set(previous);
        }
    }

    static void disconnect(Context context) {
        SESSIONS.invalidate(
                () -> {
                    context.getSharedPreferences("settings", 0)
                            .edit()
                            .remove("folder")
                            .remove("folderName")
                            .remove("customFolder")
                            .remove("lastSync")
                            .putBoolean("linked", false)
                            .putString("syncStatus", "Chưa liên kết")
                            .apply();
                });
        SyncWorker.cancel(context);
    }

    static void selectFolder(Context context, JSONObject folder, boolean custom) {
        SESSIONS.invalidate(
                () -> {
                    context.getSharedPreferences("settings", 0)
                            .edit()
                            .putString("folder", folder.optString("id"))
                            .putString("folderName", folder.optString("name"))
                            .putBoolean("customFolder", custom)
                            .putBoolean("linked", true)
                            .remove("lastSync")
                            .apply();
                });
        SyncWorker.schedule(context);
    }

    // A shared folder entered by ID requires the Drive scope. Never scan outside it.
    public static final String SCOPE = "https://www.googleapis.com/auth/drive";

    static final class HttpError extends IOException {
        final int status;

        HttpError(int status) {
            super("Drive HTTP " + status + ". Kiểm tra tài khoản và quyền thư mục.");
            this.status = status;
        }
    }

    static final class FolderUnavailable extends IOException {
        FolderUnavailable(String message) {
            super(message);
        }
    }

    static void validateFolder(JSONObject info) throws Exception {
        if (info.getBoolean("trashed"))
            throw new FolderUnavailable(
                    "Thư mục đồng bộ đã nằm trong thùng rác. Khôi phục trên Drive hoặc chọn thư mục"
                            + " khác.");
        if (!"application/vnd.google-apps.folder".equals(info.optString("mimeType")))
            throw new FolderUnavailable("Không tìm thấy thư mục đồng bộ. Hãy chọn thư mục khác.");
        if (!info.getJSONObject("capabilities").optBoolean("canAddChildren"))
            throw new FolderUnavailable(
                    "Bạn không còn quyền ghi vào thư mục đồng bộ. Hãy xin lại quyền chỉnh sửa hoặc"
                            + " chọn thư mục khác.");
    }

    static JSONObject folderInfo(String token, String id) throws Exception {
        try {
            JSONObject info =
                    new JSONObject(
                            http(
                                    token,
                                    "GET",
                                    "https://www.googleapis.com/drive/v3/files/"
                                            + id
                                            + "?fields=id,name,mimeType,trashed,capabilities(canRename,canAddChildren)",
                                    null,
                                    null));
            validateFolder(info);
            return info;
        } catch (HttpError e) {
            if (e.status == 404)
                throw new FolderUnavailable(
                        "Thư mục đồng bộ đã bị xóa hoặc bạn không còn quyền truy cập. Hãy chọn thư"
                                + " mục khác.");
            throw e;
        }
    }

    public static AuthorizationRequest request() {
        return AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(SCOPE)))
                .build();
    }

    static String http(String token, String method, String url, String type, String body)
            throws Exception {
        SyncSession.Ticket session = HTTP_SESSION.get();
        if (session != null) session.check();
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        Runnable cancel = c::disconnect;
        if (session != null) session.attach(cancel);
        c.setConnectTimeout(20000);
        c.setReadTimeout(20000);
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (session != null) session.check();
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", type);
                try (OutputStream out = c.getOutputStream()) {
                    if (session != null) session.check();
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = c.getResponseCode();
            if (session != null) session.check();
            if (status < 200 || status >= 300) throw new HttpError(status);
            if (c.getContentLengthLong() > BoundedInput.MAX_RESPONSE_BYTES) {
                throw new IOException("Dữ liệu Drive vượt giới hạn tải xuống.");
            }
            try (InputStream in = c.getInputStream()) {
                String result = BoundedInput.read(in, BoundedInput.MAX_RESPONSE_BYTES);
                if (session != null) session.check();
                return result;
            }
        } finally {
            if (session != null) session.detach(cancel);
            c.disconnect();
        }
    }

    static String encode(String v) throws Exception {
        return URLEncoder.encode(v, "UTF-8");
    }

    public static String createFolder(String token) throws Exception {
        return new JSONObject(
                        http(
                                token,
                                "POST",
                                "https://www.googleapis.com/drive/v3/files",
                                "application/json",
                                new JSONObject()
                                        .put("name", "Baby Feed")
                                        .put("mimeType", "application/vnd.google-apps.folder")
                                        .toString()))
                .getString("id");
    }

    public static JSONObject folder(String token, String current) throws Exception {
        return folder(token, current, false);
    }

    public static JSONObject folder(String token, String current, boolean custom) throws Exception {
        String id = current;
        if (id.isEmpty()) {
            String query =
                    "trashed = false and mimeType = 'application/vnd.google-apps.folder' and (name"
                            + " = 'Baby Feed' or name = 'BabyFeed')";
            JSONArray found =
                    new JSONObject(
                                    http(
                                            token,
                                            "GET",
                                            "https://www.googleapis.com/drive/v3/files?q="
                                                    + encode(query)
                                                    + "&fields=files(id,name)&pageSize=2",
                                            null,
                                            null))
                            .getJSONArray("files");
            if (found.length() > 1)
                throw new IOException(
                        "Có nhiều thư mục Baby Feed trên Drive. Vui lòng chọn thư mục lưu trữ trong"
                                + " Info.");
            id = found.length() == 1 ? found.getJSONObject(0).getString("id") : createFolder(token);
        }
        if (!id.matches("[A-Za-z0-9_-]+")) throw new IOException("Thư mục không hợp lệ.");
        JSONObject info = folderInfo(token, id);
        if (!custom
                && (info.optString("name").equals("BabyFeed")
                        || info.optString("name").equals("BabyFeed - Gia đình"))
                && info.getJSONObject("capabilities").optBoolean("canRename")) {
            http(
                    token,
                    "PATCH",
                    "https://www.googleapis.com/drive/v3/files/" + id,
                    "application/json",
                    new JSONObject().put("name", "Baby Feed").toString());
            info.put("name", "Baby Feed");
        }
        return info;
    }

    public static List<JSONObject> folders(String token) throws Exception {
        List<JSONObject> all = new ArrayList<>();
        String page = "";
        do {
            String query = "trashed = false and mimeType = 'application/vnd.google-apps.folder'";
            JSONObject result =
                    new JSONObject(
                            http(
                                    token,
                                    "GET",
                                    "https://www.googleapis.com/drive/v3/files?q="
                                            + encode(query)
                                            + "&pageSize=1000&fields=nextPageToken,files(id,name,parents,shared,capabilities(canAddChildren))"
                                            + (page.isEmpty() ? "" : "&pageToken=" + encode(page)),
                                    null,
                                    null));
            JSONArray files = result.getJSONArray("files");
            for (int i = 0; i < files.length(); i++) all.add(files.getJSONObject(i));
            page = result.optString("nextPageToken");
        } while (!page.isEmpty());
        Map<String, JSONObject> byId = new HashMap<>();
        for (JSONObject f : all) byId.put(f.getString("id"), f);
        for (JSONObject f : all) {
            String path = f.getString("name");
            JSONObject parent = f;
            Set<String> seen = new HashSet<>();
            seen.add(f.getString("id"));
            while (parent.optJSONArray("parents") != null
                    && parent.optJSONArray("parents").length() > 0) {
                String id = parent.getJSONArray("parents").getString(0);
                parent = byId.get(id);
                if (parent == null || !seen.add(id)) break;
                path = parent.getString("name") + " / " + path;
            }
            f.put("path", path);
        }
        all.sort(Comparator.comparing(f -> f.optString("path"), String.CASE_INSENSITIVE_ORDER));
        return all;
    }

    public static void sync(Context c, String token, SyncSession.Ticket session) throws Exception {
        withSession(
                session,
                () -> {
                    syncSession(c, token, session);
                    return null;
                });
    }

    private static synchronized void syncSession(
            Context c, String token, SyncSession.Ticket session) throws Exception {
        session.check();
        try (Store s = new Store(c)) {
            String folder =
                    session.commit(
                            () -> {
                                if (!s.prefs().getBoolean("linked", false))
                                    throw new SyncSession.Cancelled();
                                return s.prefs().getString("folder", "");
                            });
            if (!folder.matches("[A-Za-z0-9_-]+"))
                throw new IOException("Chưa chọn thư mục gia đình.");
            folderInfo(token, folder);
            // Keep this identity out of backups so restored phones never share a writer.
            File identity = new File(c.getNoBackupFilesDir(), "drive-device-id");
            String device;
            if (identity.exists())
                device =
                        new String(
                                java.nio.file.Files.readAllBytes(identity.toPath()),
                                StandardCharsets.UTF_8);
            else {
                device = UUID.randomUUID().toString();
                java.nio.file.Files.write(
                        identity.toPath(), device.getBytes(StandardCharsets.UTF_8));
            }
            if (!device.matches(SyncArchive.UUID_PATTERN))
                throw new IOException("Mã thiết bị đồng bộ không hợp lệ.");
            int remaining =
                    SyncArchive.sync(
                            device,
                            new SyncArchive.Local() {
                                public List<JSONObject> all() {
                                    return s.all(false);
                                }

                                public void insert(JSONObject event) throws Exception {
                                    session.commit(
                                            () -> {
                                                s.insert(event, true);
                                                return null;
                                            });
                                }

                                public void importEvents(Collection<JSONObject> events)
                                        throws Exception {
                                    session.commit(
                                            () -> {
                                                s.importEvents(events);
                                                return null;
                                            });
                                }

                                public void stateChanged() {
                                    Reminders.restore(c);
                                }

                                public void uploaded(String id) throws Exception {
                                    session.commit(
                                            () -> {
                                                s.uploaded(id);
                                                return null;
                                            });
                                }

                                public void prune(Set<String> ids) throws Exception {
                                    session.commit(
                                            () -> {
                                                s.prune(ids);
                                                return null;
                                            });
                                }
                            },
                            new DriveRemote(c, token, folder, session));
            // A folder can be removed while the network operations above are running.
            folderInfo(token, folder);
            session.commit(
                    () -> {
                        s.prefs()
                                .edit()
                                .putLong("lastSync", System.currentTimeMillis())
                                .putString(
                                        "syncStatus",
                                        remaining == 0
                                                ? "Đã đồng bộ"
                                                : "Đã đồng bộ; còn "
                                                        + remaining
                                                        + " file cũ cần chủ sở hữu dọn")
                                .apply();
                        return null;
                    });
        }
    }

    public static String token(Context c) throws Exception {
        AuthorizationResult result =
                Tasks.await(
                        Identity.getAuthorizationClient(c).authorize(request()),
                        30,
                        TimeUnit.SECONDS);
        if (result.hasResolution() || result.getAccessToken() == null)
            throw new IOException("Mở app và liên kết lại Google Drive.");
        return result.getAccessToken();
    }
}
