import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burp.api.montoya.ui.contextmenu.WebSocketContextMenuEvent;
import burp.api.montoya.ui.contextmenu.WebSocketEditorEvent;
import burp.api.montoya.ui.contextmenu.WebSocketMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import com.aayushatharva.brotli4j.decoder.Decoder;
import com.aayushatharva.brotli4j.decoder.DirectDecompress;
import com.github.luben.zstd.Zstd;

public class DumpBinaryContextMenu implements ContextMenuItemsProvider {

    private final MontoyaApi montoyaApi;
    private final DumpBinarySettings settings;

    public DumpBinaryContextMenu(MontoyaApi montoyaApi, DumpBinarySettings settings) {
        this.montoyaApi = montoyaApi;
        this.settings = settings;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        MessageEditorHttpRequestResponse.SelectionContext selectionContext = null;
        List<HttpRequestResponse> selected = event.selectedRequestResponses();
        HttpRequestResponse pair = null;

        MessageEditorHttpRequestResponse editor = null;
        if (event.messageEditorRequestResponse().isPresent()) {
            editor = event.messageEditorRequestResponse().get();
            pair = editor.requestResponse();
            selectionContext = editor.selectionContext();
        } else if (selected != null && !selected.isEmpty()) {
            pair = selected.get(0);
        }

        if (pair == null) {
            return List.of();
        }

        HttpRequest request = pair.request();
        HttpResponse response = pair.hasResponse() ? pair.response() : null;
        boolean hasRequestBody = request.body().length() > 0;
        boolean hasResponseBody = response != null && response.body().length() > 0;

        if (!hasRequestBody && !hasResponseBody) {
            return List.of();
        }

        List<Component> items = new ArrayList<>();
        JMenu dumpMenu = new JMenu("Dump Binary");

        // Context-aware: when in message editor, show only relevant option
        boolean showRequest = hasRequestBody && (selectionContext == null || selectionContext == MessageEditorHttpRequestResponse.SelectionContext.REQUEST);
        boolean showResponse = hasResponseBody && (selectionContext == null || selectionContext == MessageEditorHttpRequestResponse.SelectionContext.RESPONSE);

        if (showRequest) {
            addDumpItems(dumpMenu, pair, request.body(), request.headerValue("Content-Type"), "request", event, editor);
        }
        if (showResponse) {
            addDumpItems(dumpMenu, pair, response.body(), response.headerValue("Content-Type"), "response", event, editor);
        }

        // Save selection only (when user has selected text in message editor)
        if (editor != null && editor.selectionOffsets().isPresent()) {
            dumpMenu.addSeparator();
            var range = editor.selectionOffsets().get();
            byte[] fullMessage = selectionContext == MessageEditorHttpRequestResponse.SelectionContext.REQUEST
                    ? pair.request().toByteArray().getBytes()
                    : pair.response().toByteArray().getBytes();
            int start = range.startIndexInclusive();
            int end = Math.min(range.endIndexExclusive(), fullMessage.length);
            if (start < end) {
                byte[] selectedBytes = new byte[end - start];
                System.arraycopy(fullMessage, start, selectedBytes, 0, selectedBytes.length);
                String selLabel = selectionContext == MessageEditorHttpRequestResponse.SelectionContext.REQUEST ? "request" : "response";
                String selExt = DumpBinaryUtils.detectExtensionFromMagic(selectedBytes);
                String selName = "selection-" + selLabel + "-" + System.currentTimeMillis() + "." + selExt;
                JMenuItem dumpSelection = new JMenuItem("Dump selection (" + selectedBytes.length + " bytes)");
                dumpSelection.addActionListener(e -> dumpBody(selectedBytes, "application/octet-stream", selName, e, false));
                dumpMenu.add(dumpSelection);
            }
        }

        // Batch dump when multiple selected
        if (selected != null && selected.size() > 1) {
            dumpMenu.addSeparator();
            JMenuItem dumpAll = new JMenuItem("Dump all selected (" + selected.size() + " items)");
            dumpAll.addActionListener(e -> batchDump(selected, event));
            dumpMenu.add(dumpAll);
            if (selected.size() == 2) {
                JMenuItem diffSelected = new JMenuItem("Binary diff (compare 2 selected)");
                diffSelected.addActionListener(e -> showBinaryDiff(selected.get(0), selected.get(1), event));
                dumpMenu.add(diffSelected);
            }
        }

        if (dumpMenu.getItemCount() > 0) {
            montoyaApi.userInterface().applyThemeToComponent(dumpMenu);
            items.add(dumpMenu);
        }
        return items;
    }

    @Override
    public List<Component> provideMenuItems(WebSocketContextMenuEvent event) {
        List<Component> items = new ArrayList<>();
        JMenu dumpMenu = new JMenu("Dump Binary");

        List<WebSocketMessage> selected = event.selectedWebSocketMessages();
        WebSocketMessage message = null;
        WebSocketEditorEvent editorEvent = null;

        if (event.messageEditorWebSocket().isPresent()) {
            editorEvent = event.messageEditorWebSocket().get();
            message = editorEvent.webSocketMessage();
        } else if (selected != null && !selected.isEmpty()) {
            message = selected.get(0);
        }

        if (message != null && message.payload().length() > 0) {
            addWebSocketDumpItems(dumpMenu, message, event, editorEvent);
        }

        // Dump selection (when text selected in WebSocket editor)
        if (editorEvent != null && editorEvent.selectionOffsets().isPresent() && message != null) {
            var range = editorEvent.selectionOffsets().get();
            byte[] contents = editorEvent.getContents().getBytes();
            int start = range.startIndexInclusive();
            int end = Math.min(range.endIndexExclusive(), contents.length);
            if (start < end) {
                dumpMenu.addSeparator();
                byte[] selectedBytes = new byte[end - start];
                System.arraycopy(contents, start, selectedBytes, 0, selectedBytes.length);
                String dirLabel = message.direction().name().toLowerCase().contains("client") ? "client" : "server";
                String selExt = DumpBinaryUtils.detectExtensionFromMagic(selectedBytes);
                String selName = "ws-selection-" + dirLabel + "-" + System.currentTimeMillis() + "." + selExt;
                JMenuItem dumpSelection = new JMenuItem("Dump selection (" + selectedBytes.length + " bytes)");
                dumpSelection.addActionListener(e -> dumpBody(selectedBytes, "application/octet-stream", selName, e, false));
                dumpMenu.add(dumpSelection);
            }
        }

        // Batch dump when multiple WebSocket messages selected
        if (selected != null && selected.size() > 1) {
            dumpMenu.addSeparator();
            JMenuItem dumpAll = new JMenuItem("Dump all selected (" + selected.size() + " messages)");
            dumpAll.addActionListener(e -> batchDumpWebSocket(selected, event));
            dumpMenu.add(dumpAll);
            if (selected.size() == 2) {
                JMenuItem diffSelected = new JMenuItem("Binary diff (compare 2 selected)");
                diffSelected.addActionListener(e -> showBinaryDiffWebSocket(selected.get(0), selected.get(1), event));
                dumpMenu.add(diffSelected);
            }
        }

        if (dumpMenu.getItemCount() > 0) {
            montoyaApi.userInterface().applyThemeToComponent(dumpMenu);
            items.add(dumpMenu);
        }
        return items;
    }

    private void addWebSocketDumpItems(JMenu menu, WebSocketMessage message, WebSocketContextMenuEvent event, WebSocketEditorEvent editor) {
        DumpBinaryUtils.setCustomMagicBytes(settings.getCustomMagicBytes());
        byte[] rawBytes = message.payload().getBytes();
        String ext = DumpBinaryUtils.detectExtensionFromMagic(rawBytes);
        String suggestedName = buildWebSocketFilename(message, ext);
        byte[] bytesToDump = rawBytes;

        final byte[] finalBytesToDump = bytesToDump;
        final String finalSuggestedName = suggestedName;
        Component parent = getParentWindow(event);

        JMenuItem dumpPayload = new JMenuItem("Dump WebSocket payload");
        dumpPayload.addActionListener(e -> dumpBody(finalBytesToDump, "application/octet-stream", finalSuggestedName, parent, false));
        menu.add(dumpPayload);

        JMenu copyMenu = new JMenu("Copy to clipboard");
        JMenuItem copyBase64 = new JMenuItem("Copy as base64");
        copyBase64.addActionListener(e -> copyToClipboard(finalBytesToDump, "base64"));
        JMenuItem copyHex = new JMenuItem("Copy as hex");
        copyHex.addActionListener(e -> copyToClipboard(finalBytesToDump, "hex"));
        copyMenu.add(copyBase64);
        copyMenu.add(copyHex);
        montoyaApi.userInterface().applyThemeToComponent(copyMenu);
        menu.add(copyMenu);

        byte[] decodedBase64 = DumpBinaryUtils.tryDecodeBase64(rawBytes);
        if (decodedBase64 != null && decodedBase64.length > 0) {
            String b64Ext = DumpBinaryUtils.detectExtensionFromMagic(decodedBase64);
            String b64Name = suggestedName.replace("." + ext, "-decoded." + b64Ext);
            JMenuItem dumpDecoded = new JMenuItem("Dump decoded base64");
            dumpDecoded.addActionListener(e -> dumpBody(decodedBase64, "application/octet-stream", b64Name, parent, false));
            menu.add(dumpDecoded);
        }

        byte[] chainedDecoded = DumpBinaryUtils.tryChainedDecode(rawBytes);
        if (chainedDecoded != null && chainedDecoded.length > 0 && !java.util.Arrays.equals(chainedDecoded, rawBytes)) {
            String chainExt = DumpBinaryUtils.detectExtensionFromMagic(chainedDecoded);
            String chainName = suggestedName.replace("." + ext, "-chained." + chainExt);
            JMenuItem dumpChained = new JMenuItem("Dump chained decode (base64/gzip)");
            dumpChained.addActionListener(e -> dumpBody(chainedDecoded, "application/octet-stream", chainName, parent, false));
            menu.add(dumpChained);
        }

        String pretty = DumpBinaryUtils.tryPrettyPrint("application/json", bytesToDump);
        if (pretty != null) {
            String prettyName = suggestedName.replace("." + ext, "-pretty.json");
            JMenuItem dumpPretty = new JMenuItem("Dump pretty-printed");
            dumpPretty.addActionListener(e -> dumpBody(pretty.getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json", prettyName, parent, false));
            menu.add(dumpPretty);
        }

        byte[] msgpackJson = tryDecodeMessagePack(bytesToDump);
        if (msgpackJson != null) {
            String mpName = suggestedName.replace("." + ext, "-msgpack.json");
            JMenuItem dumpMsgpack = new JMenuItem("Dump MessagePack as JSON");
            dumpMsgpack.addActionListener(e -> dumpBody(msgpackJson, "application/json", mpName, parent, false));
            menu.add(dumpMsgpack);
        }
        byte[] cborJson = tryDecodeCbor(bytesToDump);
        if (cborJson != null) {
            String cbName = suggestedName.replace("." + ext, "-cbor.json");
            JMenuItem dumpCbor = new JMenuItem("Dump CBOR as JSON");
            dumpCbor.addActionListener(e -> dumpBody(cborJson, "application/json", cbName, parent, false));
            menu.add(dumpCbor);
        }
    }

    private String buildWebSocketFilename(WebSocketMessage message, String ext) {
        try {
            String host = sanitizeFilename(message.upgradeRequest().httpService().host());
            String dirLabel = message.direction().name().toLowerCase().contains("client") ? "client" : "server";
            return String.format("%s_ws_%s_%d.%s", host, dirLabel, System.currentTimeMillis(), ext);
        } catch (Exception e) {
            return "websocket-" + System.currentTimeMillis() + "." + ext;
        }
    }

    private Component getParentWindow(WebSocketContextMenuEvent event) {
        if (event != null && event.inputEvent() != null && event.inputEvent().getSource() instanceof Component src) {
            Window w = SwingUtilities.getWindowAncestor(src);
            if (w instanceof Frame f) return f;
            if (w != null) return w;
        }
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    }

    private void batchDumpWebSocket(List<WebSocketMessage> selected, WebSocketContextMenuEvent event) {
        Component parent = getParentWindow(event);
        String defaultDir = settings.getDefaultSaveDir();
        java.io.File dir = (defaultDir != null && !defaultDir.isBlank()) ? new java.io.File(defaultDir) : null;

        if (dir == null || !dir.isDirectory()) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select directory for WebSocket batch dump");
            if (parent != null) chooser.setCurrentDirectory(new java.io.File(System.getProperty("user.home")));
            int result = chooser.showSaveDialog(parent);
            if (result != JFileChooser.APPROVE_OPTION) return;
            dir = chooser.getSelectedFile();
        }

        java.nio.file.Path basePath = dir.toPath();
        int saved = 0;
        for (int i = 0; i < selected.size(); i++) {
            WebSocketMessage msg = selected.get(i);
            if (msg.payload().length() > 0) {
                byte[] payload = msg.payload().getBytes();
                String ext = DumpBinaryUtils.detectExtensionFromMagic(payload);
                String host = sanitizeFilename(msg.upgradeRequest().httpService().host());
                String dirLabel = msg.direction().name().toLowerCase().contains("client") ? "client" : "server";
                java.nio.file.Path path = basePath.resolve(String.format("%s-ws-%d-%s.%s", host, i + 1, dirLabel, ext));
                try {
                    Files.write(path, payload);
                    saved++;
                } catch (IOException ex) {
                    montoyaApi.logging().logToError("Dump Binary: " + ex.getMessage());
                }
            }
        }
        montoyaApi.logging().logToOutput("Dump Binary: Saved " + saved + " WebSocket payloads to " + basePath);
        if (settings.isShowSuccessToast()) {
            final int savedCount = saved;
            final java.nio.file.Path basePathFinal = basePath;
            final Component parentFinal = parent;
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parentFinal, "Saved " + savedCount + " WebSocket payloads to " + basePathFinal, "Dump Binary", JOptionPane.INFORMATION_MESSAGE));
        }
    }

    private void showBinaryDiffWebSocket(WebSocketMessage a, WebSocketMessage b, WebSocketContextMenuEvent event) {
        byte[] bodyA = a.payload().getBytes();
        byte[] bodyB = b.payload().getBytes();
        String diff = computeBinaryDiff(bodyA, bodyB);
        Component parent = getParentWindow(event);
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "WebSocket Binary Diff", java.awt.Dialog.ModalityType.MODELESS);
        JTextArea area = new JTextArea(diff, 24, 80);
        area.setEditable(false);
        area.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        dialog.add(new JScrollPane(area));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private void addDumpItems(JMenu menu, HttpRequestResponse pair, ByteArray body, String contentTypeHeader,
                             String label, ContextMenuEvent event, MessageEditorHttpRequestResponse editor) {
        DumpBinaryUtils.setCustomMagicBytes(settings.getCustomMagicBytes());
        String contentType = DumpBinaryUtils.getContentType(contentTypeHeader);
        String ext = DumpBinaryUtils.extensionForMime(contentType);
        byte[] rawBytes = body.getBytes();

        // Check for content encoding (response only typically)
        String contentEncoding = getContentEncoding(pair, label);
        byte[] bytesToDump = rawBytes;
        if ("gzip".equalsIgnoreCase(contentEncoding) && rawBytes.length >= 2 && (rawBytes[0] & 0xFF) == 0x1F && (rawBytes[1] & 0xFF) == 0x8B) {
            try {
                bytesToDump = decompressGzip(rawBytes);
                ext = DumpBinaryUtils.detectExtensionFromMagic(bytesToDump);
            } catch (IOException ex) {
                montoyaApi.logging().logToError("Dump Binary: Failed to decompress gzip - " + ex.getMessage());
            }
        } else if ("deflate".equalsIgnoreCase(contentEncoding) && rawBytes.length > 0) {
            try {
                bytesToDump = decompressDeflate(rawBytes);
                ext = DumpBinaryUtils.detectExtensionFromMagic(bytesToDump);
            } catch (IOException ex) {
                montoyaApi.logging().logToError("Dump Binary: Failed to decompress deflate - " + ex.getMessage());
            }
        } else if ("br".equalsIgnoreCase(contentEncoding) && rawBytes.length > 0) {
            try {
                bytesToDump = decompressBrotli(rawBytes);
                ext = DumpBinaryUtils.detectExtensionFromMagic(bytesToDump);
            } catch (IOException ex) {
                montoyaApi.logging().logToError("Dump Binary: Failed to decompress brotli - " + ex.getMessage());
            }
        } else if ("zstd".equalsIgnoreCase(contentEncoding) && rawBytes.length > 0) {
            try {
                bytesToDump = decompressZstd(rawBytes);
                ext = DumpBinaryUtils.detectExtensionFromMagic(bytesToDump);
            } catch (IOException ex) {
                montoyaApi.logging().logToError("Dump Binary: Failed to decompress zstd - " + ex.getMessage());
            }
        } else if ("application/octet-stream".equals(contentType) || contentType == null) {
            ext = DumpBinaryUtils.detectExtensionFromMagic(rawBytes);
        }

        String contentDisposition = "request".equals(label) ? pair.request().headerValue("Content-Disposition")
                : (pair.hasResponse() ? pair.response().headerValue("Content-Disposition") : null);
        String suggestedName = buildSuggestedFilename(pair, label, ext, contentDisposition);
        final byte[] finalBytesToDump = bytesToDump;
        final String finalContentType = contentType;
        final String finalSuggestedName = suggestedName;

        JMenuItem dumpBodyItem = new JMenuItem("Dump " + label + " body");
        dumpBodyItem.addActionListener(e -> dumpBody(finalBytesToDump, finalContentType, finalSuggestedName, e, false));

        JMenuItem dumpFull = new JMenuItem("Dump full " + label + " message");
        byte[] fullMessage = "request".equals(label) ? pair.request().toByteArray().getBytes() : pair.response().toByteArray().getBytes();
        String fullSuggestedName = suggestedName.replace("." + ext, "-raw.txt");
        dumpFull.addActionListener(e -> dumpBody(fullMessage, "text/plain", fullSuggestedName, e, false));

        menu.add(dumpBodyItem);
        menu.add(dumpFull);

        JMenu copyMenu = new JMenu("Copy to clipboard");
        JMenuItem copyBase64 = new JMenuItem("Copy as base64");
        copyBase64.addActionListener(e -> copyToClipboard(finalBytesToDump, "base64"));
        JMenuItem copyHex = new JMenuItem("Copy as hex");
        copyHex.addActionListener(e -> copyToClipboard(finalBytesToDump, "hex"));
        copyMenu.add(copyBase64);
        copyMenu.add(copyHex);
        montoyaApi.userInterface().applyThemeToComponent(copyMenu);
        menu.add(copyMenu);

        // Base64 decode option (when body decodes successfully)
        byte[] decodedBase64 = DumpBinaryUtils.tryDecodeBase64(rawBytes);
        if (decodedBase64 != null && decodedBase64.length > 0) {
            String b64Ext = DumpBinaryUtils.detectExtensionFromMagic(decodedBase64);
            String b64Name = suggestedName.replace("." + ext, "-decoded." + b64Ext);
            JMenuItem dumpDecoded = new JMenuItem("Dump decoded base64");
            dumpDecoded.addActionListener(e -> dumpBody(decodedBase64, "application/octet-stream", b64Name, e, false));
            menu.add(dumpDecoded);
        }

        // Chained decode (base64->gzip or gzip->base64) - only when not already decoded by Content-Encoding
        if (bytesToDump == rawBytes) {
            byte[] chainedDecoded = DumpBinaryUtils.tryChainedDecode(rawBytes);
            if (chainedDecoded != null && chainedDecoded.length > 0 && !java.util.Arrays.equals(chainedDecoded, rawBytes)) {
                String chainExt = DumpBinaryUtils.detectExtensionFromMagic(chainedDecoded);
                String chainName = suggestedName.replace("." + ext, "-chained." + chainExt);
                JMenuItem dumpChained = new JMenuItem("Dump chained decode (base64/gzip)");
                dumpChained.addActionListener(e -> dumpBody(chainedDecoded, "application/octet-stream", chainName, e, false));
                menu.add(dumpChained);
            }
        }

        // JSON/XML pretty-print
        String pretty = DumpBinaryUtils.tryPrettyPrint(contentType, bytesToDump);
        if (pretty != null) {
            String prettyName = suggestedName.replace("." + ext, "-pretty." + ext);
            JMenuItem dumpPretty = new JMenuItem("Dump pretty-printed");
            dumpPretty.addActionListener(e -> dumpBody(pretty.getBytes(java.nio.charset.StandardCharsets.UTF_8), contentType, prettyName, e, false));
            menu.add(dumpPretty);
        }

        // MessagePack/CBOR as JSON
        byte[] msgpackJson = tryDecodeMessagePack(bytesToDump);
        if (msgpackJson != null) {
            String mpName = suggestedName.replace("." + ext, "-msgpack.json");
            JMenuItem dumpMsgpack = new JMenuItem("Dump MessagePack as JSON");
            dumpMsgpack.addActionListener(e -> dumpBody(msgpackJson, "application/json", mpName, e, false));
            menu.add(dumpMsgpack);
        }
        byte[] cborJson = tryDecodeCbor(bytesToDump);
        if (cborJson != null) {
            String cbName = suggestedName.replace("." + ext, "-cbor.json");
            JMenuItem dumpCbor = new JMenuItem("Dump CBOR as JSON");
            dumpCbor.addActionListener(e -> dumpBody(cborJson, "application/json", cbName, e, false));
            menu.add(dumpCbor);
        }

        // Multipart extraction when applicable
        if (contentTypeHeader != null && contentTypeHeader.toLowerCase().startsWith("multipart/")) {
            String boundary = DumpBinaryUtils.extractBoundary(contentTypeHeader);
            if (boundary != null) {
                JMenuItem extractMultipart = new JMenuItem("Extract multipart parts");
                extractMultipart.addActionListener(e -> extractMultipartParts(rawBytes, boundary, pair, label, event));
                menu.add(extractMultipart);
            }
        }
    }

    private void extractMultipartParts(byte[] body, String boundary, HttpRequestResponse pair, String label, ContextMenuEvent event) {
        Component parent = getParentWindow(event);
        JFileChooser chooser = new JFileChooser(settings.getDefaultSaveDir());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select directory for multipart parts");
        int result = chooser.showSaveDialog(parent);
        if (result != JFileChooser.APPROVE_OPTION) return;

        java.nio.file.Path basePath = chooser.getSelectedFile().toPath();
        String host = sanitizeFilename(pair.httpService().host());
        byte[] boundaryBytes = ("\r\n--" + boundary).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] startBoundary = ("--" + boundary).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] endBoundary = ("--" + boundary + "--").getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        List<byte[]> parts = new ArrayList<>();
        int pos = indexOf(body, startBoundary, 0);
        if (pos < 0) return;
        pos += startBoundary.length;
        if (pos < body.length && body[pos] == '\r') pos++;
        if (pos < body.length && body[pos] == '\n') pos++;

        while (pos < body.length) {
            if (pos + endBoundary.length <= body.length && startsWith(body, endBoundary, pos)) break;
            int headerEnd = indexOf(body, new byte[]{'\r', '\n', '\r', '\n'}, pos);
            if (headerEnd < 0) break;
            int dataStart = headerEnd + 4;
            int nextBoundary = indexOf(body, boundaryBytes, dataStart);
            if (nextBoundary < 0) nextBoundary = indexOf(body, endBoundary, dataStart);
            if (nextBoundary < 0) nextBoundary = body.length;
            int dataEnd = nextBoundary;
            while (dataEnd > dataStart && (body[dataEnd - 1] == '\n' || body[dataEnd - 1] == '\r')) dataEnd--;
            byte[] partData = new byte[dataEnd - dataStart];
            System.arraycopy(body, dataStart, partData, 0, partData.length);
            parts.add(partData);
            if (nextBoundary + endBoundary.length <= body.length && startsWith(body, endBoundary, nextBoundary)) break;
            pos = nextBoundary + boundaryBytes.length;
            if (pos < body.length && body[pos] == '\r') pos++;
            if (pos < body.length && body[pos] == '\n') pos++;
        }

        int saved = 0;
        for (int i = 0; i < parts.size(); i++) {
            byte[] part = parts.get(i);
            String ext = DumpBinaryUtils.detectExtensionFromMagic(part);
            java.nio.file.Path path = basePath.resolve(String.format("%s-%s-part-%d.%s", host, label, i + 1, ext));
            try {
                Files.write(path, part);
                saved++;
            } catch (IOException ex) {
                montoyaApi.logging().logToError("Dump Binary: " + ex.getMessage());
            }
        }
        montoyaApi.logging().logToOutput("Dump Binary: Extracted " + saved + " multipart parts to " + basePath);
        if (settings.isShowSuccessToast()) {
            final int savedCount = saved;
            final java.nio.file.Path basePathFinal = basePath;
            final Component parentFinal = parent;
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parentFinal, "Extracted " + savedCount + " parts to " + basePathFinal, "Dump Binary", JOptionPane.INFORMATION_MESSAGE));
        }
    }

    private int indexOf(byte[] haystack, byte[] needle, int from) {
        for (int i = from; i <= haystack.length - needle.length; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) { match = false; break; }
            }
            if (match) return i;
        }
        return -1;
    }

    private boolean startsWith(byte[] data, byte[] prefix, int offset) {
        if (offset + prefix.length > data.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) return false;
        }
        return true;
    }

    private String getContentEncoding(HttpRequestResponse pair, String label) {
        if ("response".equals(label) && pair.hasResponse()) {
            String ce = pair.response().headerValue("Content-Encoding");
            return ce != null ? ce.trim() : null;
        }
        return null;
    }

    private void batchDump(List<HttpRequestResponse> selected, ContextMenuEvent event) {
        Component parent = getParentWindow(event);
        String defaultDir = settings.getDefaultSaveDir();
        java.io.File dir = (defaultDir != null && !defaultDir.isBlank()) ? new java.io.File(defaultDir) : null;

        if (dir == null || !dir.isDirectory()) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select directory for batch dump");
            if (parent != null) chooser.setCurrentDirectory(new java.io.File(System.getProperty("user.home")));
            int result = chooser.showSaveDialog(parent);
            if (result != JFileChooser.APPROVE_OPTION) return;
            dir = chooser.getSelectedFile();
        }

        java.nio.file.Path basePath = dir.toPath();
        int saved = 0;
        for (int i = 0; i < selected.size(); i++) {
            HttpRequestResponse pair = selected.get(i);
            HttpRequest req = pair.request();
            HttpResponse res = pair.hasResponse() ? pair.response() : null;

            String host = sanitizeFilename(req.httpService().host());
            if (req.body().length() > 0) {
                String ext = DumpBinaryUtils.extensionForMime(DumpBinaryUtils.getContentType(req.headerValue("Content-Type")));
                java.nio.file.Path path = basePath.resolve(String.format("%s-%d-request.%s", host, i + 1, ext));
                try {
                    Files.write(path, req.body().getBytes());
                    saved++;
                } catch (IOException ex) {
                    montoyaApi.logging().logToError("Dump Binary: " + ex.getMessage());
                }
            }
            if (res != null && res.body().length() > 0) {
                String ext = DumpBinaryUtils.extensionForMime(DumpBinaryUtils.getContentType(res.headerValue("Content-Type")));
                java.nio.file.Path path = basePath.resolve(String.format("%s-%d-response.%s", host, i + 1, ext));
                try {
                    Files.write(path, res.body().getBytes());
                    saved++;
                } catch (IOException ex) {
                    montoyaApi.logging().logToError("Dump Binary: " + ex.getMessage());
                }
            }
        }
        montoyaApi.logging().logToOutput("Dump Binary: Saved " + saved + " files to " + basePath);
        if (settings.isShowSuccessToast()) {
            final int savedCount = saved;
            final java.nio.file.Path basePathFinal = basePath;
            final Component parentFinal = parent;
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parentFinal, "Saved " + savedCount + " files to " + basePathFinal, "Dump Binary", JOptionPane.INFORMATION_MESSAGE));
        }
    }

    private String buildSuggestedFilename(HttpRequestResponse pair, String label, String ext, String contentDisposition) {
        String fromHeader = DumpBinaryUtils.extractFilenameFromContentDisposition(contentDisposition);
        if (fromHeader != null && !fromHeader.isBlank()) {
            String sanitized = sanitizeFilename(fromHeader);
            if (!sanitized.isBlank()) {
                if (!sanitized.contains(".")) sanitized += "." + ext;
                return sanitized;
            }
        }
        try {
            String host = pair.httpService().host();
            String path = pair.request().pathWithoutQuery();
            String lastSegment = path;
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                lastSegment = path.substring(lastSlash + 1);
            }
            lastSegment = sanitizeFilename(lastSegment);
            if (lastSegment.length() > 30) lastSegment = lastSegment.substring(0, 30);
            host = sanitizeFilename(host);
            return String.format("%s_%s_%s.%s", host, lastSegment, label, ext);
        } catch (Exception e) {
            return "dump-" + label + "-" + System.currentTimeMillis() + "." + ext;
        }
    }

    private java.io.File resolveUniqueFile(java.io.File dir, String baseName, String ext) {
        java.io.File file = new java.io.File(dir, baseName + "." + ext);
        if (!file.exists()) return file;
        for (int i = 2; ; i++) {
            file = new java.io.File(dir, baseName + "-" + i + "." + ext);
            if (!file.exists()) return file;
        }
    }

    private String sanitizeFilename(String s) {
        if (s == null || s.isEmpty()) return "unknown";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private byte[] decompressGzip(byte[] compressed) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = gis.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private byte[] decompressDeflate(byte[] compressed) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        try {
            // Try zlib format first (with header)
            try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
                int n;
                while ((n = iis.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } catch (IOException e) {
            out.reset();
            // Try raw deflate (no header)
            Inflater inflater = new Inflater(true);
            try {
                inflater.setInput(compressed);
                int n;
                while ((n = inflater.inflate(buf)) > 0) out.write(buf, 0, n);
                if (!inflater.finished()) throw new IOException("Incomplete deflate stream");
                return out.toByteArray();
            } catch (DataFormatException ex) {
                throw new IOException("Invalid deflate data: " + ex.getMessage(), ex);
            } finally {
                inflater.end();
            }
        }
    }

    private byte[] decompressBrotli(byte[] compressed) throws IOException {
        DirectDecompress result = Decoder.decompress(compressed);
        if (result.getResultStatus() != com.aayushatharva.brotli4j.decoder.DecoderJNI.Status.DONE) {
            throw new IOException("Brotli decompression failed: " + result.getResultStatus());
        }
        return result.getDecompressedData();
    }

    private byte[] decompressZstd(byte[] compressed) throws IOException {
        long size = Zstd.decompressedSize(compressed);
        if (size <= 0 || size > 512 * 1024 * 1024) size = 4 * compressed.length;
        byte[] out = new byte[(int) size];
        long n = Zstd.decompress(out, compressed);
        if (n < 0) throw new IOException("Zstd decompression failed: " + n);
        byte[] result = new byte[(int) n];
        System.arraycopy(out, 0, result, 0, (int) n);
        return result;
    }

    private byte[] tryDecodeMessagePack(byte[] bytes) {
        if (bytes == null || bytes.length < 2) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper(new org.msgpack.jackson.dataformat.MessagePackFactory());
            om.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            Object obj = om.readValue(bytes, Object.class);
            com.fasterxml.jackson.databind.ObjectMapper jsonOm = new com.fasterxml.jackson.databind.ObjectMapper();
            jsonOm.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            return jsonOm.writeValueAsString(obj).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return null;
    }

    private byte[] tryDecodeCbor(byte[] bytes) {
        if (bytes == null || bytes.length < 1) return null;
        try {
            com.fasterxml.jackson.dataformat.cbor.CBORFactory f = new com.fasterxml.jackson.dataformat.cbor.CBORFactory();
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper(f);
            om.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            Object obj = om.readValue(bytes, Object.class);
            com.fasterxml.jackson.databind.ObjectMapper jsonOm = new com.fasterxml.jackson.databind.ObjectMapper();
            jsonOm.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            return jsonOm.writeValueAsString(obj).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return null;
    }

    private void showBinaryDiff(HttpRequestResponse a, HttpRequestResponse b, ContextMenuEvent event) {
        byte[] bodyA = a.hasResponse() ? a.response().body().getBytes() : a.request().body().getBytes();
        byte[] bodyB = b.hasResponse() ? b.response().body().getBytes() : b.request().body().getBytes();
        String diff = computeBinaryDiff(bodyA, bodyB);
        Component parent = getParentWindow(event);
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Binary Diff", java.awt.Dialog.ModalityType.MODELESS);
        JTextArea area = new JTextArea(diff, 24, 80);
        area.setEditable(false);
        area.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        dialog.add(new JScrollPane(area));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private String computeBinaryDiff(byte[] a, byte[] b) {
        StringBuilder sb = new StringBuilder();
        int maxLen = Math.max(a.length, b.length);
        sb.append("Length A: ").append(a.length).append(", B: ").append(b.length).append("\n");
        int firstDiff = -1;
        int diffCount = 0;
        for (int i = 0; i < maxLen; i++) {
            byte va = i < a.length ? a[i] : 0;
            byte vb = i < b.length ? b[i] : 0;
            if (va != vb) {
                if (firstDiff < 0) firstDiff = i;
                diffCount++;
            }
        }
        sb.append("First diff at offset: ").append(firstDiff >= 0 ? firstDiff : "none (identical)").append("\n");
        sb.append("Differing bytes: ").append(diffCount).append("\n\n");
        for (int i = 0; i < maxLen; i += 16) {
            sb.append(String.format("%08x  ", i));
            for (int j = 0; j < 16; j++) {
                int idx = i + j;
                if (idx >= maxLen) break;
                byte va = idx < a.length ? a[idx] : 0;
                byte vb = idx < b.length ? b[idx] : 0;
                boolean same = va == vb;
                boolean aOnly = idx >= b.length;
                boolean bOnly = idx >= a.length;
                if (aOnly) sb.append(String.format("%02x ", va & 0xFF)).append("(A only) ");
                else if (bOnly) sb.append("    ").append(String.format("%02x ", vb & 0xFF)).append("(B only) ");
                else sb.append(same ? String.format("%02x ", va & 0xFF) : "** ");
            }
            sb.append("\n");
            if (i >= 8192) { sb.append("... (truncated)\n"); break; }
        }
        return sb.toString();
    }

    private void copyToClipboard(byte[] bytes, String format) {
        String text = "base64".equals(format)
                ? Base64.getEncoder().encodeToString(bytes)
                : bytesToHex(bytes);
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            montoyaApi.logging().logToOutput("Dump Binary: Copied " + bytes.length + " bytes as " + format + " to clipboard");
        } catch (Exception ex) {
            montoyaApi.logging().logToError("Dump Binary: Failed to copy - " + ex.getMessage());
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    void performDumpFromHotKey(HttpRequestResponse pair, String label, Component parent) {
        DumpBinaryUtils.setCustomMagicBytes(settings.getCustomMagicBytes());
        HttpRequest request = pair.request();
        HttpResponse response = pair.hasResponse() ? pair.response() : null;
        ByteArray body = "request".equals(label) ? request.body() : response.body();
        String contentTypeHeader = "request".equals(label) ? request.headerValue("Content-Type") : response.headerValue("Content-Type");
        String contentDisposition = "request".equals(label) ? request.headerValue("Content-Disposition") : response.headerValue("Content-Disposition");

        String contentType = DumpBinaryUtils.getContentType(contentTypeHeader);
        String ext = DumpBinaryUtils.extensionForMime(contentType);
        byte[] rawBytes = body.getBytes();
        byte[] bytesToDump = rawBytes;

        String contentEncoding = getContentEncoding(pair, label);
        if ("gzip".equalsIgnoreCase(contentEncoding) && rawBytes.length >= 2 && (rawBytes[0] & 0xFF) == 0x1F && (rawBytes[1] & 0xFF) == 0x8B) {
            try {
                bytesToDump = decompressGzip(rawBytes);
                ext = DumpBinaryUtils.detectExtensionFromMagic(bytesToDump);
            } catch (IOException ex) {
                montoyaApi.logging().logToError("Dump Binary: Failed to decompress gzip - " + ex.getMessage());
            }
        } else if ("deflate".equalsIgnoreCase(contentEncoding) && rawBytes.length > 0) {
            try {
                bytesToDump = decompressDeflate(rawBytes);
                ext = DumpBinaryUtils.detectExtensionFromMagic(bytesToDump);
            } catch (IOException ex) {
                montoyaApi.logging().logToError("Dump Binary: Failed to decompress deflate - " + ex.getMessage());
            }
        } else if ("br".equalsIgnoreCase(contentEncoding) && rawBytes.length > 0) {
            try {
                bytesToDump = decompressBrotli(rawBytes);
                ext = DumpBinaryUtils.detectExtensionFromMagic(bytesToDump);
            } catch (IOException ex) {
                montoyaApi.logging().logToError("Dump Binary: Failed to decompress brotli - " + ex.getMessage());
            }
        } else if ("zstd".equalsIgnoreCase(contentEncoding) && rawBytes.length > 0) {
            try {
                bytesToDump = decompressZstd(rawBytes);
                ext = DumpBinaryUtils.detectExtensionFromMagic(bytesToDump);
            } catch (IOException ex) {
                montoyaApi.logging().logToError("Dump Binary: Failed to decompress zstd - " + ex.getMessage());
            }
        } else if ("application/octet-stream".equals(contentType) || contentType == null) {
            ext = DumpBinaryUtils.detectExtensionFromMagic(rawBytes);
        }

        String suggestedName = buildSuggestedFilename(pair, label, ext, contentDisposition);
        dumpBody(bytesToDump, contentType, suggestedName, parent, false);
    }

    private Component getParentWindow(ContextMenuEvent event) {
        if (event != null && event.inputEvent() != null && event.inputEvent().getSource() instanceof Component src) {
            Window w = SwingUtilities.getWindowAncestor(src);
            if (w instanceof Frame f) return f;
            if (w != null) return w;
        }
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    }

    private void dumpBody(byte[] bytes, String contentType, String suggestedName, java.awt.event.ActionEvent actionEvent, boolean isFullMessage) {
        Component parent = actionEvent.getSource() instanceof Component c ? SwingUtilities.getWindowAncestor(c) : null;
        if (parent == null) parent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        dumpBody(bytes, contentType, suggestedName, parent, isFullMessage);
    }

    private void dumpBody(byte[] bytes, String contentType, String suggestedName, Component parent, boolean isFullMessage) {

        String ext = suggestedName.contains(".") ? suggestedName.substring(suggestedName.lastIndexOf('.') + 1) : DumpBinaryUtils.extensionForMime(contentType);
        String baseName = suggestedName.contains(".") ? suggestedName.substring(0, suggestedName.lastIndexOf('.')) : suggestedName;

        java.io.File selectedFile = null;
        if (settings.isUseDefaultDir() && settings.getDefaultSaveDir() != null) {
            java.io.File dir = new java.io.File(settings.getDefaultSaveDir());
            if (dir.isDirectory()) {
                selectedFile = resolveUniqueFile(dir, baseName, ext);
            }
        }

        if (selectedFile == null) {
            JFileChooser chooser = new JFileChooser(settings.getDefaultSaveDir());
            chooser.setSelectedFile(new java.io.File(suggestedName));
            chooser.setDialogTitle("Save body");
            int result = chooser.showSaveDialog(parent);
            if (result != JFileChooser.APPROVE_OPTION) return;
            selectedFile = chooser.getSelectedFile();
        }

        if (selectedFile == null) return;

        // Overwrite confirmation
        if (settings.isConfirmOverwrite() && selectedFile.exists()) {
            int confirm = JOptionPane.showConfirmDialog(parent, "File exists. Overwrite?", "Dump Binary", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
        }

        java.nio.file.Path path = selectedFile.toPath();
        final Component dialogParent = parent;
        final int bytesLength = bytes.length;
        final java.nio.file.Path pathFinal = path;

        // Background file I/O (SwingWorker)
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Files.write(pathFinal, bytes);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    montoyaApi.logging().logToOutput("Dump Binary: Saved " + bytesLength + " bytes to " + pathFinal);
                    if (settings.isShowSuccessToast()) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dialogParent, "Saved " + bytesLength + " bytes to\n" + pathFinal, "Dump Binary", JOptionPane.INFORMATION_MESSAGE));
                    }
                    if (settings.isOpenAfterSave()) {
                        SwingUtilities.invokeLater(() -> {
                            try {
                                java.awt.Desktop.getDesktop().open(pathFinal.toFile());
                            } catch (IOException ex) {
                                montoyaApi.logging().logToError("Dump Binary: Failed to open file - " + ex.getMessage());
                            }
                        });
                    }
                } catch (Exception ex) {
                    montoyaApi.logging().logToError("Dump Binary: Failed to save - " + ex.getMessage());
                    final String errorMsg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dialogParent, "Failed to save: " + errorMsg, "Dump Binary", JOptionPane.ERROR_MESSAGE));
                }
            }
        }.execute();
    }
}
