package triplesix.babyfeed;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class BoundedInputTest {
    @Test
    public void acceptsExactlyTheLimitAndPreservesUtf8() throws Exception {
        byte[] bytes = "Sữa cho bé".getBytes(StandardCharsets.UTF_8);
        assertEquals(
                "Sữa cho bé", BoundedInput.read(new ByteArrayInputStream(bytes), bytes.length));
    }

    @Test
    public void rejectsOversizedBodyWithoutContentLengthHeader() {
        assertThrows(
                IOException.class,
                () -> BoundedInput.read(new ByteArrayInputStream(new byte[9000]), 8192));
    }

    @Test
    public void emptyBodyIsAllowed() throws Exception {
        assertEquals("", BoundedInput.read(new ByteArrayInputStream(new byte[0]), 0));
    }
}
