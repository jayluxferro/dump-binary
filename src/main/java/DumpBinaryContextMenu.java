import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DumpBinaryContextMenu implements ContextMenuItemsProvider {

    private final MontoyaApi montoyaApi;

    private static final Map<String, String> MIME_TO_EXT = new HashMap<>();

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
    }

    public DumpBinaryContextMenu(MontoyaApi montoyaApi) {
        this.montoyaApi = montoyaApi;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        HttpRequestResponse pair = null;

        // Right-click inside message editor (Repeater, request/response view)
        if (event.messageEditorRequestResponse().isPresent()) {
            MessageEditorHttpRequestResponse editor = event.messageEditorRequestResponse().get();
            pair = editor.requestResponse();
        }
        // Right-click on selected row (Proxy history, Site map, etc.)
        else {
            List<HttpRequestResponse> selected = event.selectedRequestResponses();
            if (selected != null && !selected.isEmpty()) {
                pair = selected.get(0);
            }
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

        if (hasRequestBody) {
            JMenuItem dumpRequest = new JMenuItem("Dump request body");
            dumpRequest.addActionListener(e -> dumpBody(
                    request.body(),
                    getContentType(request.headerValue("Content-Type")),
                    "request"
            ));
            dumpMenu.add(dumpRequest);
        }

        if (hasResponseBody) {
            JMenuItem dumpResponse = new JMenuItem("Dump response body");
            dumpResponse.addActionListener(e -> dumpBody(
                    response.body(),
                    getContentType(response.headerValue("Content-Type")),
                    "response"
            ));
            dumpMenu.add(dumpResponse);
        }

        montoyaApi.userInterface().applyThemeToComponent(dumpMenu);
        items.add(dumpMenu);
        return items;
    }

    private String getContentType(String contentTypeHeader) {
        if (contentTypeHeader == null || contentTypeHeader.isBlank()) {
            return "application/octet-stream";
        }
        int semicolon = contentTypeHeader.indexOf(';');
        String mime = semicolon >= 0 ? contentTypeHeader.substring(0, semicolon).trim() : contentTypeHeader.trim();
        return mime.toLowerCase();
    }

    private String extensionForMime(String mimeType) {
        return MIME_TO_EXT.getOrDefault(mimeType, "bin");
    }

    private void dumpBody(ByteArray body, String contentType, String label) {
        String ext = extensionForMime(contentType);
        String suggestedName = "dump-" + label + "-" + System.currentTimeMillis() + "." + ext;

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(suggestedName));
        chooser.setDialogTitle("Save " + label + " body");

        Component parent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        int result = chooser.showSaveDialog(parent);

        if (result == JFileChooser.APPROVE_OPTION) {
            java.nio.file.Path path = chooser.getSelectedFile().toPath();
            byte[] bytes = body.getBytes();

            try {
                Files.write(path, bytes);
                montoyaApi.logging().logToOutput("Dump Binary: Saved " + bytes.length + " bytes to " + path);
            } catch (IOException ex) {
                montoyaApi.logging().logToError("Dump Binary: Failed to save - " + ex.getMessage());
            }
        }
    }
}
