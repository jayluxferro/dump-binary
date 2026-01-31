import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burp.api.montoya.ui.hotkey.HotKeyEvent;
import burp.api.montoya.ui.hotkey.HotKeyHandler;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class DumpBinaryHotKeyHandler implements HotKeyHandler {

    private final MontoyaApi montoyaApi;
    private final DumpBinaryContextMenu contextMenu;

    public DumpBinaryHotKeyHandler(MontoyaApi montoyaApi, DumpBinaryContextMenu contextMenu) {
        this.montoyaApi = montoyaApi;
        this.contextMenu = contextMenu;
    }

    @Override
    public void handle(HotKeyEvent event) {
        HttpRequestResponse pair = null;
        MessageEditorHttpRequestResponse.SelectionContext selectionContext = null;

        if (event.messageEditorRequestResponse().isPresent()) {
            MessageEditorHttpRequestResponse editor = event.messageEditorRequestResponse().get();
            pair = editor.requestResponse();
            selectionContext = editor.selectionContext();
        } else {
            List<HttpRequestResponse> selected = event.selectedRequestResponses();
            if (selected != null && !selected.isEmpty()) {
                pair = selected.get(0);
            }
        }

        if (pair == null) return;

        HttpRequest request = pair.request();
        HttpResponse response = pair.hasResponse() ? pair.response() : null;
        boolean hasRequestBody = request.body().length() > 0;
        boolean hasResponseBody = response != null && response.body().length() > 0;

        if (!hasRequestBody && !hasResponseBody) return;

        Component parent = getParentWindow(event);

        if (selectionContext == MessageEditorHttpRequestResponse.SelectionContext.REQUEST && hasRequestBody) {
            contextMenu.performDumpFromHotKey(pair, "request", parent);
        } else if (selectionContext == MessageEditorHttpRequestResponse.SelectionContext.RESPONSE && hasResponseBody) {
            contextMenu.performDumpFromHotKey(pair, "response", parent);
        } else if (hasResponseBody) {
            contextMenu.performDumpFromHotKey(pair, "response", parent);
        } else if (hasRequestBody) {
            contextMenu.performDumpFromHotKey(pair, "request", parent);
        }
    }

    private Component getParentWindow(HotKeyEvent event) {
        if (event != null && event.inputEvent() != null && event.inputEvent().getSource() instanceof Component src) {
            Window w = SwingUtilities.getWindowAncestor(src);
            if (w instanceof Frame f) return f;
            if (w != null) return w;
        }
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    }
}
