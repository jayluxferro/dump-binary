import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

public class Extension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        montoyaApi.extension().setName("Dump Binary");

        ContextMenuItemsProvider contextMenu = new DumpBinaryContextMenu(montoyaApi);
        montoyaApi.userInterface().registerContextMenuItemsProvider(contextMenu);

        montoyaApi.logging().logToOutput("Dump Binary extension loaded");
    }
}