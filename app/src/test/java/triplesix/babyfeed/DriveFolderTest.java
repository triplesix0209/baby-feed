package triplesix.babyfeed;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

public class DriveFolderTest {
    JSONObject folder(boolean trashed, boolean writable) throws Exception {
        return new JSONObject()
                .put("mimeType", "application/vnd.google-apps.folder")
                .put("trashed", trashed)
                .put("capabilities", new JSONObject().put("canAddChildren", writable));
    }

    @Test
    public void activeFolderIsAccepted() throws Exception {
        DriveSync.validateFolder(folder(false, true));
    }

    @Test
    public void trashIsRejectedEvenIfStillWritable() throws Exception {
        DriveSync.FolderUnavailable error =
                assertThrows(
                        DriveSync.FolderUnavailable.class,
                        () -> DriveSync.validateFolder(folder(true, true)));
        assertTrue(error.getMessage().contains("thùng rác"));
    }

    @Test
    public void trashTakesPrecedenceOverPermissions() throws Exception {
        DriveSync.FolderUnavailable error =
                assertThrows(
                        DriveSync.FolderUnavailable.class,
                        () -> DriveSync.validateFolder(folder(true, false)));
        assertTrue(error.getMessage().contains("thùng rác"));
    }

    @Test
    public void revokedWriteAccessIsRejected() throws Exception {
        assertThrows(
                DriveSync.FolderUnavailable.class,
                () -> DriveSync.validateFolder(folder(false, false)));
    }

    @Test
    public void nonFolderIsRejected() throws Exception {
        assertThrows(
                DriveSync.FolderUnavailable.class,
                () ->
                        DriveSync.validateFolder(
                                folder(false, true).put("mimeType", "application/json")));
    }
}
