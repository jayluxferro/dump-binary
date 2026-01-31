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
        HotKey dumpHotKey = HotKey.hotKey("Dump Binary", "ctrl shift D");
        for (HotKeyContext ctx : new HotKeyContext[]{
                HotKeyContext.HTTP_MESSAGE_EDITOR,
                HotKeyContext.PROXY_HTTP_HISTORY,
                HotKeyContext.SITE_MAP_CONTENTS_TABLE,
                HotKeyContext.INTRUDER_ATTACK_RESULTS,
                HotKeyContext.ORGANIZER_ENTRIES
        }) {
            montoyaApi.userInterface().registerHotKeyHandler(ctx, dumpHotKey, hotKeyHandler);
        }
        // Note: Montoya API has no HotKeyContext for WebSocket editor; use context menu there.

        montoyaApi.logging().logToOutput("Dump Binary v" + DumpBinaryVersion.VERSION + " loaded (Ctrl+Shift+D to dump)");
    }
}