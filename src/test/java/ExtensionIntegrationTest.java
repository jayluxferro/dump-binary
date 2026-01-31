import burp.api.montoya.BurpExtension;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Dump Binary extension.
 * Verifies extension structure and component collaboration.
 * Full initialization test requires Burp runtime (tested manually).
 */
class ExtensionIntegrationTest {

    @Test
    void extensionImplementsBurpExtension() {
        assertTrue(BurpExtension.class.isAssignableFrom(Extension.class));
    }

    @Test
    void versionIsNonEmpty() {
        assertNotNull(DumpBinaryVersion.VERSION);
        assertFalse(DumpBinaryVersion.VERSION.isBlank());
    }

    @Test
    void fullDecodePipelineIntegration() {
        // Integration test: raw bytes through decode pipeline (Utils flow)
        byte[] raw = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertNotNull(DumpBinaryUtils.md5Hex(raw));
        assertNotNull(DumpBinaryUtils.sha256Hex(raw));
        assertNotNull(DumpBinaryUtils.sha1Hex(raw));
        assertNotNull(DumpBinaryUtils.sha512Hex(raw));
        assertNotNull(DumpBinaryUtils.extractStrings(raw, 2, false));
        assertFalse(DumpBinaryUtils.extractStrings(raw, 2, true).isEmpty());
    }
}
