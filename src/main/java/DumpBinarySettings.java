import burp.api.montoya.ui.settings.SettingsPanelPersistence;
import burp.api.montoya.ui.settings.SettingsPanelSetting;
import burp.api.montoya.ui.settings.SettingsPanelWithData;

import static burp.api.montoya.ui.settings.SettingsPanelBuilder.settingsPanel;
import static burp.api.montoya.ui.settings.SettingsPanelSetting.booleanSetting;
import static burp.api.montoya.ui.settings.SettingsPanelSetting.stringSetting;

public class DumpBinarySettings {

    public static final String DEFAULT_SAVE_DIR = "defaultSaveDir";
    public static final String USE_DEFAULT_DIR = "useDefaultDir";
    public static final String SHOW_SUCCESS_TOAST = "showSuccessToast";
    public static final String CONFIRM_OVERWRITE = "confirmOverwrite";
    public static final String OPEN_AFTER_SAVE = "openAfterSave";
    public static final String CUSTOM_MAGIC_BYTES = "customMagicBytes";

    private final SettingsPanelWithData panel;

    public DumpBinarySettings() {
        this.panel = settingsPanel()
                .withPersistence(SettingsPanelPersistence.USER_SETTINGS)
                .withTitle("Dump Binary")
                .withDescription("Configure default save directory and behavior. Version " + DumpBinaryVersion.VERSION + ".")
                .withKeywords("dump", "binary", "save", "export")
                .withSettings(
                        stringSetting("Default save directory (leave empty for system default)", DEFAULT_SAVE_DIR, ""),
                        booleanSetting("Use default directory when saving (skip file chooser)", USE_DEFAULT_DIR, false),
                        booleanSetting("Show success notification after save", SHOW_SUCCESS_TOAST, true),
                        booleanSetting("Confirm before overwriting existing file", CONFIRM_OVERWRITE, true),
                        booleanSetting("Open saved file in default application", OPEN_AFTER_SAVE, false),
                        stringSetting("Custom magic bytes (hex:ext per line, e.g. 89 50 4E 47:png)", CUSTOM_MAGIC_BYTES, "")
                )
                .build();
    }

    public SettingsPanelWithData getPanel() {
        return panel;
    }

    public String getDefaultSaveDir() {
        String dir = panel.getString(DEFAULT_SAVE_DIR);
        return (dir != null && !dir.isBlank()) ? dir.trim() : null;
    }

    public boolean isUseDefaultDir() {
        return panel.getBoolean(USE_DEFAULT_DIR);
    }

    public boolean isShowSuccessToast() {
        return panel.getBoolean(SHOW_SUCCESS_TOAST);
    }

    public boolean isConfirmOverwrite() {
        return panel.getBoolean(CONFIRM_OVERWRITE);
    }

    public boolean isOpenAfterSave() {
        return panel.getBoolean(OPEN_AFTER_SAVE);
    }

    public String getCustomMagicBytes() {
        String s = panel.getString(CUSTOM_MAGIC_BYTES);
        return (s != null && !s.isBlank()) ? s : null;
    }
}
