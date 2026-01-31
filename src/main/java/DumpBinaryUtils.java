import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DumpBinaryUtils {

    private static final Map<String, String> MIME_TO_EXT = new HashMap<>();
    private static final List<CustomMagic> CUSTOM_MAGIC = new ArrayList<>();

    public static void setCustomMagicBytes(String config) {
        CUSTOM_MAGIC.clear();
        if (config == null || config.isBlank()) return;
        for (String line : config.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colon = line.indexOf(':');
            if (colon > 0) {
                String hex = line.substring(0, colon).trim().replaceAll("\\s+", "");
                String ext = line.substring(colon + 1).trim();
                if (!hex.isEmpty() && !ext.isEmpty()) {
                    try {
                        byte[] sig = hexToBytes(hex);
                        if (sig != null && sig.length > 0) {
                            CUSTOM_MAGIC.add(new CustomMagic(sig, ext));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) return null;
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            int idx = i * 2;
            int v = Integer.parseInt(hex.substring(idx, idx + 2), 16);
            b[i] = (byte) v;
        }
        return b;
    }

    private record CustomMagic(byte[] sig, String ext) {}

    static {
        MIME_TO_EXT.put("image/png", "png");
        MIME_TO_EXT.put("image/jpeg", "jpg");
        MIME_TO_EXT.put("image/jpg", "jpg");
        MIME_TO_EXT.put("image/gif", "gif");
        MIME_TO_EXT.put("image/bmp", "bmp");
        MIME_TO_EXT.put("image/tiff", "tiff");
        MIME_TO_EXT.put("image/svg+xml", "svg");
        MIME_TO_EXT.put("image/webp", "webp");
        MIME_TO_EXT.put("image/x-icon", "ico");
        MIME_TO_EXT.put("application/pdf", "pdf");
        MIME_TO_EXT.put("application/zip", "zip");
        MIME_TO_EXT.put("application/x-tar", "tar");
        MIME_TO_EXT.put("application/gzip", "gz");
        MIME_TO_EXT.put("application/x-gzip", "gz");
        MIME_TO_EXT.put("application/json", "json");
        MIME_TO_EXT.put("application/xml", "xml");
        MIME_TO_EXT.put("text/xml", "xml");
        MIME_TO_EXT.put("application/octet-stream", "bin");
        MIME_TO_EXT.put("font/woff", "woff");
        MIME_TO_EXT.put("font/woff2", "woff2");
        MIME_TO_EXT.put("application/javascript", "js");
        MIME_TO_EXT.put("text/javascript", "js");
        MIME_TO_EXT.put("text/css", "css");
        MIME_TO_EXT.put("application/wasm", "wasm");
        MIME_TO_EXT.put("text/html", "html");
        MIME_TO_EXT.put("text/plain", "txt");
    }

    private static final byte[] PNG_SIG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF_SIG = {0x47, 0x49, 0x46};
    private static final byte[] PDF_SIG = {0x25, 0x50, 0x44, 0x46};
    private static final byte[] ZIP_SIG = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] GZIP_SIG = {0x1F, (byte) 0x8B};

    private DumpBinaryUtils() {}

    public static String extensionForMime(String mimeType) {
        return MIME_TO_EXT.getOrDefault(mimeType, "bin");
    }

    public static String detectExtensionFromMagic(byte[] bytes) {
        if (bytes == null || bytes.length < 2) return "bin";
        for (CustomMagic cm : CUSTOM_MAGIC) {
            if (startsWith(bytes, cm.sig)) return cm.ext;
        }
        if (bytes.length < 4) return "bin";
        if (startsWith(bytes, PNG_SIG)) return "png";
        if (startsWith(bytes, JPEG_SIG)) return "jpg";
        if (startsWith(bytes, GIF_SIG)) return "gif";
        if (startsWith(bytes, PDF_SIG)) return "pdf";
        if (startsWith(bytes, ZIP_SIG)) return "zip";
        if (startsWith(bytes, GZIP_SIG)) return "gz";
        if (bytes.length >= 4 && (bytes[0] & 0xFF) == 0x04 && (bytes[1] & 0xFF) == 0x22
                && (bytes[2] & 0xFF) == 0x4D && (bytes[3] & 0xFF) == 0x18) return "lz4";
        return "bin";
    }

    public static String extractFilenameFromContentDisposition(String header) {
        if (header == null || header.isBlank()) return null;
        Pattern p = Pattern.compile("filename\\s*=\\s*[\"']?([^\"'\\s;]+)[\"']?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(header);
        return m.find() ? m.group(1).trim() : null;
    }

    public static String extractBoundary(String contentTypeHeader) {
        if (contentTypeHeader == null || contentTypeHeader.isBlank()) return null;
        Pattern p = Pattern.compile("boundary\\s*=\\s*[\"']?([^\"'\\s;]+)[\"']?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(contentTypeHeader);
        return m.find() ? m.group(1).trim() : null;
    }

    public static byte[] tryDecodeBase64(byte[] raw) {
        if (raw == null || raw.length < 4) return null;
        try {
            String s = new String(raw, java.nio.charset.StandardCharsets.US_ASCII);
            if (!s.matches("^[A-Za-z0-9+/=\\s\\r\\n]+$")) return null;
            return Base64.getDecoder().decode(s.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static byte[] tryDecodeBase64UrlSafe(byte[] raw) {
        if (raw == null || raw.length < 4) return null;
        try {
            String s = new String(raw, java.nio.charset.StandardCharsets.US_ASCII);
            if (!s.matches("^[A-Za-z0-9_-]+$")) return null;
            return Base64.getUrlDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static byte[] tryDecodeHex(byte[] raw) {
        if (raw == null || raw.length < 2) return null;
        try {
            String s = new String(raw, java.nio.charset.StandardCharsets.US_ASCII).replaceAll("\\s", "");
            if (!s.matches("^[0-9a-fA-F]+$") || s.length() % 2 != 0) return null;
            byte[] out = new byte[s.length() / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public static String md5Hex(byte[] bytes) {
        if (bytes == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b & 0xFF));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static String sha256Hex(byte[] bytes) {
        if (bytes == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b & 0xFF));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static String getContentType(String contentTypeHeader) {
        if (contentTypeHeader == null || contentTypeHeader.isBlank()) {
            return "application/octet-stream";
        }
        int semicolon = contentTypeHeader.indexOf(';');
        String mime = semicolon >= 0 ? contentTypeHeader.substring(0, semicolon).trim() : contentTypeHeader.trim();
        return mime.toLowerCase();
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    public static byte[] tryChainedDecode(byte[] raw) {
        if (raw == null || raw.length < 4) return null;
        byte[] b64 = tryDecodeBase64(raw);
        if (b64 != null && b64.length >= 2 && (b64[0] & 0xFF) == 0x1F && (b64[1] & 0xFF) == 0x8B) {
            try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(b64);
                 java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(bais);
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = gis.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            } catch (Exception ignored) {}
        }
        if (raw.length >= 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B) {
            try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(raw);
                 java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(bais);
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = gis.read(buf)) > 0) out.write(buf, 0, n);
                byte[] decompressed = out.toByteArray();
                byte[] b64Out = tryDecodeBase64(decompressed);
                return b64Out != null ? b64Out : decompressed;
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static String tryPrettyPrint(String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        String ct = contentType != null ? contentType.toLowerCase() : "";
        if (!ct.contains("json") && !ct.contains("xml")) return null;
        try {
            String s = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (ct.contains("json")) {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                om.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
                Object obj = om.readValue(s, Object.class);
                return om.writeValueAsString(obj);
            }
            if (ct.contains("xml")) {
                javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
                org.w3c.dom.Document doc = db.parse(new java.io.ByteArrayInputStream(bytes));
                javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
                javax.xml.transform.Transformer t = tf.newTransformer();
                t.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
                t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                java.io.StringWriter sw = new java.io.StringWriter();
                t.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(sw));
                return sw.toString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Minimum length for extracted strings (filters noise). */
    private static final int MIN_STRING_LENGTH = 4;

    /** Printable ASCII range (space to tilde). */
    private static final int PRINTABLE_MIN = 0x20;
    private static final int PRINTABLE_MAX = 0x7E;

    /**
     * Extract printable strings from binary data.
     * Returns contiguous sequences of printable ASCII (space to tilde) of at least
     * {@value #MIN_STRING_LENGTH} characters. Useful for finding URLs, paths, tokens.
     *
     * @param bytes raw binary data
     * @return list of extracted strings, never null
     */
    public static List<String> extractStrings(byte[] bytes) {
        List<String> result = new ArrayList<>();
        if (bytes == null || bytes.length == 0) return result;

        StringBuilder current = new StringBuilder();
        for (byte b : bytes) {
            int v = b & 0xFF;
            if (v >= PRINTABLE_MIN && v <= PRINTABLE_MAX) {
                current.append((char) v);
            } else {
                if (current.length() >= MIN_STRING_LENGTH) {
                    result.add(current.toString());
                }
                current.setLength(0);
            }
        }
        if (current.length() >= MIN_STRING_LENGTH) {
            result.add(current.toString());
        }
        return result;
    }
}
