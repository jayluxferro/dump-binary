import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.hotkey.HotKey;
import burp.api.montoya.ui.hotkey.HotKeyContext;

import com.aayushatharva.brotli4j.Brotli4jLoader;

public class Extension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        try {
            Brotli4jLoader.ensureAvailability();
        } catch (Throwable t) {
            montoyaApi.logging().logToError("Dump Binary: Brotli native library failed to load - br decoding disabled: " + t.getMessage());
        }
        montoyaApi.extension().setName("Dump Binary v" + DumpBinaryVersion.VERSION);

        DumpBinarySettings settings = new DumpBinarySettings();
        montoyaApi.userInterface().registerSettingsPanel(settings.getPanel());

        DumpBinaryContextMenu contextMenu = new DumpBinaryContextMenu(montoyaApi, settings);
        montoyaApi.userInterface().registerContextMenuItemsProvider(contextMenu);

        DumpBinaryHotKeyHandler hotKeyHandler = new DumpBinaryHotKeyHandler(montoyaApi, contextMenu);
        montoyaApi.userInterface().registerHotKeyHandler(
                HotKeyContext.HTTP_MESSAGE_EDITOR,
                HotKey.hotKey("Dump Binary", "ctrl shift D"),
                hotKeyHandler
        );
        montoyaApi.userInterface().registerHotKeyHandler(
                HotKeyContext.PROXY_HTTP_HISTORY,
                HotKey.hotKey("Dump Binary", "ctrl shift D"),
                hotKeyHandler
        );

        montoyaApi.logging().logToOutput("Dump Binary v" + DumpBinaryVersion.VERSION + " loaded (Ctrl+Shift+D to dump)");
    }
}