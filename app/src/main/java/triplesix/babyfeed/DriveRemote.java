package triplesix.babyfeed;

import static triplesix.babyfeed.DriveSync.encode;
import static triplesix.babyfeed.DriveSync.http;

import android.content.Context;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Folder-scoped archive transport and versioned local cache. */
final class DriveRemote implements SyncArchive.Remote {
    private final String token;
    private final String folder;
    private final SyncSession.Ticket session;

    DriveRemote(Context context, String token, String folder, SyncSession.Ticket session) {
        this.token = token;
        this.folder = folder;
        this.session = session;
        cacheDir = new File(context.getNoBackupFilesDir(), "drive-cache");
    }

    final Map<String, String> versions = new HashMap<>();
    final File cacheDir;

    File cacheFile(String id) throws Exception {
        String version = versions.get(id);
        if (version == null) return null;
        byte[] digest =
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(
                                (folder + ":" + id + ":" + version)
                                        .getBytes(StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder();
        for (byte b : digest) key.append(String.format(Locale.ROOT, "%02x", b & 255));
        return new File(cacheDir, key + ".json");
    }

    public List<JSONObject> list() throws Exception {
        List<JSONObject> files = new ArrayList<>();
        String page = "";
        do {
            String q =
                    "'"
                            + folder
                            + "' in parents and trashed = false and"
                            + " mimeType = 'application/json'";
            JSONObject response =
                    new JSONObject(
                            http(
                                    token,
                                    "GET",
                                    "https://www.googleapis.com/drive/v3/files?q="
                                            + encode(q)
                                            + "&pageSize=1000&fields=nextPageToken,files(id,name,version,capabilities(canTrash))"
                                            + (page.isEmpty() ? "" : "&pageToken=" + encode(page)),
                                    null,
                                    null));
            JSONArray entries = response.getJSONArray("files");
            for (int i = 0; i < entries.length(); i++) files.add(entries.getJSONObject(i));
            page = response.optString("nextPageToken");
        } while (!page.isEmpty());
        Set<String> valid = new HashSet<>();
        for (JSONObject file : files)
            if (file.has("version")) {
                String id = file.getString("id");
                versions.put(id, file.getString("version"));
                valid.add(cacheFile(id).getName());
            }
        File[] cached = cacheDir.listFiles();
        if (cached != null)
            for (File file : cached)
                if (!valid.contains(file.getName()))
                    java.nio.file.Files.deleteIfExists(file.toPath());
        return files;
    }

    public JSONObject read(String id) throws Exception {
        session.check();
        File cached = cacheFile(id);
        if (cached != null && cached.exists()) {
            try {
                try (InputStream input = new FileInputStream(cached)) {
                    return new JSONObject(
                            BoundedInput.read(input, BoundedInput.MAX_RESPONSE_BYTES));
                }
            } catch (IOException | JSONException ignored) {
                java.nio.file.Files.deleteIfExists(cached.toPath());
            }
        }
        JSONObject body =
                new JSONObject(
                        http(
                                token,
                                "GET",
                                "https://www.googleapis.com/drive/v3/files/" + id + "?alt=media",
                                null,
                                null));
        if (cached != null) {
            java.nio.file.Files.createDirectories(cacheDir.toPath());
            File temp = new File(cacheDir, "download.tmp");
            java.nio.file.Files.write(
                    temp.toPath(), body.toString().getBytes(StandardCharsets.UTF_8));
            java.nio.file.Files.move(
                    temp.toPath(),
                    cached.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return body;
    }

    public String write(String id, String name, JSONObject archive) throws Exception {
        if (id != null) {
            File cached = cacheFile(id);
            if (cached != null) java.nio.file.Files.deleteIfExists(cached.toPath());
            versions.remove(id);
            http(
                    token,
                    "PATCH",
                    "https://www.googleapis.com/upload/drive/v3/files/" + id + "?uploadType=media",
                    "application/json",
                    archive.toString());
            return id;
        }
        String boundary = "babyfeed" + UUID.randomUUID();
        String metadata =
                new JSONObject()
                        .put("name", name)
                        .put("mimeType", "application/json")
                        .put("parents", new JSONArray().put(folder))
                        .toString();
        String body =
                "--"
                        + boundary
                        + "\r\n"
                        + "Content-Type: application/json;"
                        + " charset=UTF-8\r\n\r\n"
                        + metadata
                        + "\r\n--"
                        + boundary
                        + "\r\nContent-Type: application/json\r\n\r\n"
                        + archive
                        + "\r\n--"
                        + boundary
                        + "--\r\n";
        return new JSONObject(
                        http(
                                token,
                                "POST",
                                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
                                "multipart/related; boundary=" + boundary,
                                body))
                .getString("id");
    }

    public void trash(String id) throws Exception {
        http(
                token,
                "PATCH",
                "https://www.googleapis.com/drive/v3/files/" + id,
                "application/json",
                "{\"trashed\":true}");
    }
}
