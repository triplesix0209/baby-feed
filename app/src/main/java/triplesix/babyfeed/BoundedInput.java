package triplesix.babyfeed;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class BoundedInput {
    // Legacy archives may contain years of records; current v1 pages are limited to 256 KiB.
    static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    static String read(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) {
                throw new SyncSession.Cancelled();
            }
            if (count > limit - output.size()) {
                throw new IOException("Dữ liệu Drive vượt giới hạn tải xuống.");
            }
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
