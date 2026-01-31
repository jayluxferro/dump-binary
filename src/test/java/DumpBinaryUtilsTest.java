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
    void detectExtensionFromMagic_lz4() {
        byte[] lz4 = {0x04, 0x22, 0x4D, 0x18, 0x00, 0x00};
        assertEquals("lz4", DumpBinaryUtils.detectExtensionFromMagic(lz4));
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

    @Test
    void tryDecodeBase64UrlSafe() {
        byte[] encoded = "SGVsbG8".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] decoded = DumpBinaryUtils.tryDecodeBase64UrlSafe(encoded);
        assertNotNull(decoded);
        assertEquals("Hello", new String(decoded));
    }

    @Test
    void tryDecodeHex() {
        byte[] hex = "48656c6c6f".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] decoded = DumpBinaryUtils.tryDecodeHex(hex);
        assertNotNull(decoded);
        assertEquals("Hello", new String(decoded));
    }

    @Test
    void extractStrings() {
        byte[] binary = new byte[]{
            'a', 'b', 'c', 'd', 0, 'h', 't', 't', 'p', 's', ':', '/', '/', 'e', 'x', 'a', 'm', 'p', 'l', 'e', '.', 'c', 'o', 'm', '/', 'p', 'a', 't', 'h', 0, 1, 2,
            't', 'o', 'k', 'e', 'n', '1', '2', '3', 0
        };
        var strings = DumpBinaryUtils.extractStrings(binary);
        assertEquals(3, strings.size());
        assertTrue(strings.contains("abcd"));
        assertTrue(strings.contains("https://example.com/path"));
        assertTrue(strings.contains("token123"));
    }

    @Test
    void extractStrings_minLengthFiltersShort() {
        byte[] binary = new byte[]{'a', 'b', 0, 'c', 'd', 0, 'e', 'f', 0};
        var strings = DumpBinaryUtils.extractStrings(binary);
        assertTrue(strings.isEmpty()); // ab, cd, ef are all < 4 chars
    }

    @Test
    void extractStrings_emptyOrNull() {
        assertTrue(DumpBinaryUtils.extractStrings(null).isEmpty());
        assertTrue(DumpBinaryUtils.extractStrings(new byte[0]).isEmpty());
    }

    @Test
    void extractStrings_exactlyMinLength() {
        byte[] binary = new byte[]{'a', 'b', 'c', 'd'};
        var strings = DumpBinaryUtils.extractStrings(binary);
        assertEquals(1, strings.size());
        assertEquals("abcd", strings.get(0));
    }

    @Test
    void extractStrings_trailingStringWithoutDelimiter() {
        byte[] binary = new byte[]{0, 0, 'p', 'a', 't', 'h', '/', 'f', 'i', 'l', 'e'};
        var strings = DumpBinaryUtils.extractStrings(binary);
        assertEquals(1, strings.size());
        assertEquals("path/file", strings.get(0));
    }

    @Test
    void extractStrings_filtersNonPrintable() {
        byte[] binary = new byte[]{
            (byte) 0x09, (byte) 0x0A, (byte) 0x0D,  // tab, newline, carriage return
            'u', 'r', 'l', '1', '2', '3', '4',
            (byte) 0x7F, (byte) 0x80, (byte) 0xFF   // DEL and high bytes
        };
        var strings = DumpBinaryUtils.extractStrings(binary);
        assertEquals(1, strings.size());
        assertEquals("url1234", strings.get(0));
    }

    @Test
    void extractStrings_preservesOrder() {
        byte[] binary = new byte[]{
            'f', 'i', 'r', 's', 't', 0, 's', 'e', 'c', 'o', 'n', 'd', 0, 't', 'h', 'i', 'r', 'd'
        };
        var strings = DumpBinaryUtils.extractStrings(binary);
        assertEquals(3, strings.size());
        assertEquals("first", strings.get(0));
        assertEquals("second", strings.get(1));
        assertEquals("third", strings.get(2));
    }

    @Test
    void tryPrettyPrint_xml() {
        byte[] xml = "<root><a>1</a><b>2</b></root>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String pretty = DumpBinaryUtils.tryPrettyPrint("application/xml", xml);
        assertNotNull(pretty);
        assertTrue(pretty.contains("\n"));
        assertTrue(pretty.contains("<root>"));
        assertTrue(pretty.contains("<a>"));
    }

    @Test
    void tryPrettyPrint_nonJsonXmlReturnsNull() {
        byte[] data = "plain text".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertNull(DumpBinaryUtils.tryPrettyPrint("text/plain", data));
        assertNull(DumpBinaryUtils.tryPrettyPrint("application/octet-stream", data));
        assertNull(DumpBinaryUtils.tryPrettyPrint(null, data));
    }

    @Test
    void lz4FrameRoundtrip() throws Exception {
        byte[] original = "Hello LZ4".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (net.jpountz.lz4.LZ4FrameOutputStream los = new net.jpountz.lz4.LZ4FrameOutputStream(baos)) {
            los.write(original);
        }
        byte[] compressed = baos.toByteArray();
        assertTrue(compressed.length > 0);
        assertTrue(compressed[0] == 0x04 && compressed[1] == 0x22 && compressed[2] == 0x4D && compressed[3] == 0x18);
        try (net.jpountz.lz4.LZ4FrameInputStream lis = new net.jpountz.lz4.LZ4FrameInputStream(new java.io.ByteArrayInputStream(compressed));
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = lis.read(buf)) > 0) out.write(buf, 0, n);
            assertArrayEquals(original, out.toByteArray());
        }
    }

    @Test
    void snappyRoundtrip() throws Exception {
        byte[] original = "Hello Snappy".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] compressed = org.xerial.snappy.Snappy.compress(original);
        assertNotNull(compressed);
        assertTrue(compressed.length > 0);
        byte[] decompressed = org.xerial.snappy.Snappy.uncompress(compressed);
        assertArrayEquals(original, decompressed);
    }

    @Test
    void md5AndSha256Hex() {
        byte[] data = "test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String md5 = DumpBinaryUtils.md5Hex(data);
        String sha256 = DumpBinaryUtils.sha256Hex(data);
        assertNotNull(md5);
        assertNotNull(sha256);
        assertEquals(32, md5.length());
        assertEquals(64, sha256.length());
        assertTrue(md5.matches("[0-9a-f]+"));
        assertTrue(sha256.matches("[0-9a-f]+"));
    }
}
