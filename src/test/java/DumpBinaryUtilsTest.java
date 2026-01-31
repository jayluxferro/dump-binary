import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DumpBinaryUtilsTest {

    @Test
    void extensionForMime_knownTypes() {
        assertEquals("png", DumpBinaryUtils.extensionForMime("image/png"));
        assertEquals("jpg", DumpBinaryUtils.extensionForMime("image/jpeg"));
        assertEquals("pdf", DumpBinaryUtils.extensionForMime("application/pdf"));
        assertEquals("json", DumpBinaryUtils.extensionForMime("application/json"));
        assertEquals("woff2", DumpBinaryUtils.extensionForMime("font/woff2"));
    }

    @Test
    void extensionForMime_unknownReturnsBin() {
        assertEquals("bin", DumpBinaryUtils.extensionForMime("application/unknown"));
        assertEquals("bin", DumpBinaryUtils.extensionForMime(null));
    }

    @Test
    void detectExtensionFromMagic_png() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertEquals("png", DumpBinaryUtils.detectExtensionFromMagic(png));
    }

    @Test
    void detectExtensionFromMagic_jpeg() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};
        assertEquals("jpg", DumpBinaryUtils.detectExtensionFromMagic(jpeg));
    }

    @Test
    void detectExtensionFromMagic_pdf() {
        byte[] pdf = {0x25, 0x50, 0x44, 0x46};
        assertEquals("pdf", DumpBinaryUtils.detectExtensionFromMagic(pdf));
    }

    @Test
    void detectExtensionFromMagic_zip() {
        byte[] zip = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00};
        assertEquals("zip", DumpBinaryUtils.detectExtensionFromMagic(zip));
    }

    @Test
    void detectExtensionFromMagic_gzip() {
        byte[] gzip = {(byte) 0x1F, (byte) 0x8B, 0x08, 0x00};
        assertEquals("gz", DumpBinaryUtils.detectExtensionFromMagic(gzip));
    }

    @Test
    void detectExtensionFromMagic_gif() {
        byte[] gif = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
        assertEquals("gif", DumpBinaryUtils.detectExtensionFromMagic(gif));
    }

    @Test
    void detectExtensionFromMagic_unknownReturnsBin() {
        byte[] unknown = {0x00, 0x01, 0x02, 0x03};
        assertEquals("bin", DumpBinaryUtils.detectExtensionFromMagic(unknown));
        assertEquals("bin", DumpBinaryUtils.detectExtensionFromMagic(null));
        assertEquals("bin", DumpBinaryUtils.detectExtensionFromMagic(new byte[2]));
    }

    @Test
    void extractFilenameFromContentDisposition() {
        assertEquals("report.pdf", DumpBinaryUtils.extractFilenameFromContentDisposition("attachment; filename=\"report.pdf\""));
        assertEquals("data.json", DumpBinaryUtils.extractFilenameFromContentDisposition("attachment; filename=data.json"));
        assertEquals("file.bin", DumpBinaryUtils.extractFilenameFromContentDisposition("inline; filename=file.bin; other=value"));
        assertNull(DumpBinaryUtils.extractFilenameFromContentDisposition(null));
        assertNull(DumpBinaryUtils.extractFilenameFromContentDisposition(""));
    }

    @Test
    void extractBoundary() {
        assertEquals("----WebKitFormBoundary", DumpBinaryUtils.extractBoundary("multipart/form-data; boundary=----WebKitFormBoundary"));
        assertEquals("boundary123", DumpBinaryUtils.extractBoundary("multipart/mixed; boundary=\"boundary123\""));
        assertNull(DumpBinaryUtils.extractBoundary(null));
        assertNull(DumpBinaryUtils.extractBoundary("text/plain"));
    }

    @Test
    void tryDecodeBase64_valid() {
        byte[] encoded = "SGVsbG8gV29ybGQ=".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] decoded = DumpBinaryUtils.tryDecodeBase64(encoded);
        assertNotNull(decoded);
        assertEquals("Hello World", new String(decoded));
    }

    @Test
    void tryDecodeBase64_invalid() {
        assertNull(DumpBinaryUtils.tryDecodeBase64("not base64!!!".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertNull(DumpBinaryUtils.tryDecodeBase64(null));
        assertNull(DumpBinaryUtils.tryDecodeBase64(new byte[2]));
    }

    @Test
    void getContentType() {
        assertEquals("application/json", DumpBinaryUtils.getContentType("application/json"));
        assertEquals("text/html", DumpBinaryUtils.getContentType("text/html; charset=utf-8"));
        assertEquals("application/octet-stream", DumpBinaryUtils.getContentType(null));
        assertEquals("application/octet-stream", DumpBinaryUtils.getContentType(""));
        assertEquals("application/octet-stream", DumpBinaryUtils.getContentType("   "));
    }

    @Test
    void brotliDecompressionRoundtrip() throws Exception {
        com.aayushatharva.brotli4j.Brotli4jLoader.ensureAvailability();
        byte[] original = "Hello Brotli".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] compressed = com.aayushatharva.brotli4j.encoder.Encoder.compress(original);
        assertNotNull(compressed);
        assertTrue(compressed.length > 0);
        var result = com.aayushatharva.brotli4j.decoder.Decoder.decompress(compressed);
        assertEquals(com.aayushatharva.brotli4j.decoder.DecoderJNI.Status.DONE, result.getResultStatus());
        assertArrayEquals(original, result.getDecompressedData());
    }

    @Test
    void tryChainedDecode_base64ThenGzip() throws Exception {
        byte[] original = "chained".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gos = new java.util.zip.GZIPOutputStream(baos)) {
            gos.write(original);
        }
        byte[] gzipped = baos.toByteArray();
        byte[] base64Gzip = java.util.Base64.getEncoder().encode(gzipped);
        byte[] decoded = DumpBinaryUtils.tryChainedDecode(base64Gzip);
        assertNotNull(decoded);
        assertArrayEquals(original, decoded);
    }

    @Test
    void customMagicBytes() {
        DumpBinaryUtils.setCustomMagicBytes("DE AD BE EF:custom\n");
        byte[] custom = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF, 0, 0};
        assertEquals("custom", DumpBinaryUtils.detectExtensionFromMagic(custom));
        DumpBinaryUtils.setCustomMagicBytes("");
        assertEquals("bin", DumpBinaryUtils.detectExtensionFromMagic(custom));
    }

    @Test
    void tryPrettyPrint_json() {
        byte[] json = "{\"a\":1,\"b\":2}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String pretty = DumpBinaryUtils.tryPrettyPrint("application/json", json);
        assertNotNull(pretty);
        assertTrue(pretty.contains("\n"));
        assertTrue(pretty.contains("  "));
    }
}
