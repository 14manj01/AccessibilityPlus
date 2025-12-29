package com.accessibilityplus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("accessibilityplus")
public interface AccessibilityPlusConfig extends Config
{
    @ConfigSection(
            name = "Dialog",
            description = "Accessibility dialog overlay settings",
            position = 0
    )
    String dialogSection = "dialogSection";

    @ConfigSection(
            name = "Speech",
            description = "Text-to-speech settings",
            position = 1
    )
    String speechSection = "speechSection";

    @ConfigSection(
            name = "Minimap",
            description = "Minimap shapes overlay settings",
            position = 2
    )
    String minimapSection = "minimapSection";

    enum DialogTheme
    {
        PARCHMENT,
        BLACK_PANEL
    }

    enum SpeechBackend
    {
        /**
         * Uses a local HTTP bridge (recommended). Accessibility Plus can auto-start an embedded bridge.
         */
        BRIDGE,

        /**
         * Uses a pure-Java TTS engine (simpler setup, lower quality).
         */
        FREETTS
    }

    // --------------------
    // Dialog
    // --------------------

    @ConfigItem(
            keyName = "enableDialogOverlay",
            name = "Enable accessibility dialog",
            description = "Enable the large, readable dialog overlay.",
            section = dialogSection,
            position = 0
    )
    default boolean enableDialogOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "hideNativeDialog",
            name = "Hide native dialog",
            description = "Draw over the native dialog area so only the accessibility overlay is visible.",
            section = dialogSection,
            position = 1
    )
    default boolean hideNativeDialog()
    {
        return true;
    }

    @ConfigItem(
            keyName = "dialogTheme",
            name = "Dialog theme",
            description = "Visual theme for the dialog overlay.",
            section = dialogSection,
            position = 2
    )
    default DialogTheme dialogTheme()
    {
        return DialogTheme.BLACK_PANEL;
    }

    @Range(min = 12, max = 48)
    @ConfigItem(
            keyName = "dialogFontSize",
            name = "Text size",
            description = "Font size for dialog text and options.",
            section = dialogSection,
            position = 3
    )
    default int dialogFontSize()
    {
        return 28;
    }

    @Range(min = 320, max = 1000)
    @ConfigItem(
            keyName = "dialogPanelWidth",
            name = "Dialog width",
            description = "Width of the dialog overlay in pixels.",
            section = dialogSection,
            position = 4
    )
    default int dialogPanelWidth()
    {
        return 720;
    }

    @Range(min = 40, max = 255)
    @ConfigItem(
            keyName = "dialogOverlayOpacity",
            name = "Background opacity",
            description = "Opacity for the dialog overlay background.",
            section = dialogSection,
            position = 5
    )
    default int dialogOverlayOpacity()
    {
        return 240;
    }

    // --------------------
    // Speech
    // --------------------

    @ConfigItem(
            keyName = "enableTts",
            name = "Enable TTS",
            description = "Speak dialog and option menus using text-to-speech.",
            section = speechSection,
            position = 0
    )
    default boolean enableTts()
    {
        return false;
    }

    @ConfigItem(
            keyName = "ttsBackend",
            name = "TTS backend",
            description = "Select the text-to-speech engine.",
            section = speechSection,
            position = 1
    )
    default SpeechBackend ttsBackend()
    {
        return SpeechBackend.BRIDGE;
    }

    @ConfigItem(
            keyName = "startEmbeddedBridge",
            name = "Start bridge when TTS enabled",
            description = "When enabled, Accessibility Plus starts an embedded local bridge (HTTP) that runs Piper.",
            section = speechSection,
            position = 2
    )
    default boolean startEmbeddedBridge()
    {
        return true;
    }

    @Range(min = 1024, max = 65535)
    @ConfigItem(
            keyName = "embeddedBridgePort",
            name = "Bridge port",
            description = "Port for the embedded bridge. Default matches Natural Speech (59125).",
            section = speechSection,
            position = 3
    )
    default int embeddedBridgePort()
    {
        return 59125;
    }

    @ConfigItem(
            keyName = "piperPath",
            name = "Piper executable path",
            description = "Full path to piper.exe (Windows) or piper binary (Mac/Linux). Could be: C:\\piper\\piper.exe",
            section = speechSection,
            position = 4
    )
    default String piperPath()
    {
        return "";
    }

    @ConfigItem(
            keyName = "piperModelPath",
            name = "Piper voice model (.onnx)",
            description = "Full path to a Piper voice model (.onnx). The matching .onnx.json should be alongside it.",
            section = speechSection,
            position = 5
    )
    default String piperModelPath()
    {
        return "";
    }

    @ConfigItem(
            keyName = "bridgeBaseUrl",
            name = "Bridge base URL",
            description = "If you use an external bridge, set its base URL here. If embedded bridge is running, this is ignored.",
            section = speechSection,
            position = 6
    )
    default String bridgeBaseUrl()
    {
        return "http://127.0.0.1:59125";
    }

    @Range(min = 250, max = 10000)
    @ConfigItem(
            keyName = "ttsBridgeTimeoutMs",
            name = "Bridge timeout (ms)",
            description = "Timeout for bridge requests.",
            section = speechSection,
            position = 7
    )
    default int ttsBridgeTimeoutMs()
    {
        return 2500;
    }

    @ConfigItem(
            keyName = "ttsSpeakDialog",
            name = "Speak dialog lines",
            description = "Speak NPC dialog lines when they appear.",
            section = speechSection,
            position = 10
    )
    default boolean ttsSpeakDialog()
    {
        return true;
    }

    @ConfigItem(
            keyName = "ttsIncludeSpeaker",
            name = "Include speaker name",
            description = "Prefix speech with the NPC name when available.",
            section = speechSection,
            position = 11
    )
    default boolean ttsIncludeSpeaker()
    {
        return true;
    }

    @ConfigItem(
            keyName = "ttsSpeakOptions",
            name = "Speak option menus",
            description = "Speak the numbered option list when Select an option appears.",
            section = speechSection,
            position = 12
    )
    default boolean ttsSpeakOptions()
    {
        return true;
    }

    @Range(min = 0, max = 5000)
    @ConfigItem(
            keyName = "ttsCooldownMs",
            name = "Cooldown (ms)",
            description = "Minimum time between spoken events to prevent spam from widget flicker.",
            section = speechSection,
            position = 15
    )
    default int ttsCooldownMs()
    {
        return 600;
    }

    @ConfigItem(
            keyName = "checkBridge",
            name = "Check bridge",
            description = "Toggle to check whether the speech bridge is running.",
            section = speechSection,
            position = 17
    )
    default boolean checkBridge()
    {
        return false;
    }

    @ConfigItem(
            keyName = "testTts",
            name = "Test TTS",
            description = "Toggle to send a test phrase to the speech bridge.",
            section = speechSection,
            position = 18
    )
    default boolean testTts()
    {
        return false;
    }

    // --------------------
    // Minimap
    // --------------------

    @ConfigItem(
            keyName = "enableMinimapShapes",
            name = "Enable minimap shapes",
            description = "Enable accessibility shapes on the minimap.",
            section = minimapSection,
            position = 0
    )
    default boolean enableMinimapShapes()
    {
        return true;
    }

    @Range(min = 40, max = 255)
    @ConfigItem(
            keyName = "minimapShapeOpacity",
            name = "Minimap shape opacity",
            description = "Opacity for minimap shapes.",
            section = minimapSection,
            position = 1
    )
    default int minimapShapeOpacity()
    {
        return 220;
    }

    @Range(min = 1, max = 10)
    @ConfigItem(
            keyName = "minimapShapeSize",
            name = "Minimap shape size",
            description = "Size of minimap shapes.",
            section = minimapSection,
            position = 2
    )
    default int minimapShapeSize()
    {
        return 4;
    }

    @ConfigItem(
            keyName = "showNpcsOnMinimapShapes",
            name = "Show NPCs",
            description = "Show shapes for NPCs.",
            section = minimapSection,
            position = 3
    )
    default boolean showNpcsOnMinimapShapes()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showLocalPlayerOnMinimapShapes",
            name = "Show local player",
            description = "Show a shape for the local player.",
            section = minimapSection,
            position = 4
    )
    default boolean showLocalPlayerOnMinimapShapes()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showPlayersOnMinimapShapes",
            name = "Show other players",
            description = "Show shapes for other players.",
            section = minimapSection,
            position = 5
    )
    default boolean showPlayersOnMinimapShapes()
    {
        return false;
    }
}
