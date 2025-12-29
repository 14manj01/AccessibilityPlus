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
            description = "Enable text-to-speech for dialog and option menus. This feature sends text to an external speech service to generate audio.",
            section = speechSection,
            position = 0
    )
    default boolean enableTts()
    {
        return false;
    }

    @ConfigItem(
            keyName = "cloudTtsBaseUrl",
            name = "Speech service URL",
            description = "Base URL for the speech service. The service should accept query params m (text), r (rate), v (voice) and return WAV audio bytes.",
            section = speechSection,
            position = 1
    )
    default String cloudTtsBaseUrl()
    {
        return "https://ttsplugin.com";
    }

    @Range(min = 0, max = 10)
    @ConfigItem(
            keyName = "cloudTtsRate",
            name = "Speech rate",
            description = "Speech rate parameter sent to the service.",
            section = speechSection,
            position = 2
    )
    default int cloudTtsRate()
    {
        return 1;
    }

    @Range(min = 0, max = 50)
    @ConfigItem(
            keyName = "cloudTtsVoice",
            name = "Voice",
            description = "Voice parameter sent to the service. Values depend on the service implementation.",
            section = speechSection,
            position = 3
    )
    default int cloudTtsVoice()
    {
        return 0;
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
            description = "Include the speaker name before dialog lines.",
            section = speechSection,
            position = 11
    )
    default boolean ttsIncludeSpeaker()
    {
        return true;
    }

    @ConfigItem(
            keyName = "ttsSpeakOptions",
            name = "Speak dialog options",
            description = "Speak option menus when they appear.",
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
            description = "Minimum milliseconds between spoken phrases.",
            section = speechSection,
            position = 13
    )
    default int ttsCooldownMs()
    {
        return 700;
    }

    @ConfigItem(
            keyName = "testTts",
            name = "Test TTS",
            description = "Toggle to speak a test phrase.",
            section = speechSection,
            position = 20
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
