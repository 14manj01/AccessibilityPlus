package com.accessibilityplus;

import com.accessibilityplus.tts.EmbeddedBridgeServer;
import com.accessibilityplus.tts.TtsController;
import com.google.inject.Provides;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
        name = "Accessibility Plus",
        description = "Accessibility improvements: large dialog overlay + minimap shapes + optional TTS via Piper bridge.",
        tags = {"accessibility", "dialog", "overlay", "tts", "minimap"}
)
public class AccessibilityPlusPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private AccessibilityPlusConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DialogTextOverlay dialogTextOverlay;

    @Inject
    private MinimapShapesOverlay minimapShapesOverlay;

    @Inject
    private TtsController ttsController;

    private final EmbeddedBridgeServer embeddedBridge = new EmbeddedBridgeServer();
    private volatile int embeddedPort = -1;

    @Getter
    private String speakerName = "";

    @Getter
    private String dialogText = "";

    @Getter
    private boolean chatboxInputOpen = false;

    @Getter
    private final List<String> dialogOptions = new ArrayList<>();

    @Getter
    private Rectangle dialogBounds = null;

    // --------------------
    // TTS timing / stability helpers
    // --------------------
    private String lastClientTickDialogKey = "";
    private boolean dialogActive = false;

    private String pendingOptionsKey = "";
    private long pendingOptionsFirstSeenAt = 0L;

    // Cache option roots so we don't brute-scan thousands of widgets every tick
    private final List<WidgetRef> cachedOptionRoots = new ArrayList<>();

    private static final class WidgetRef
    {
        private final int group;
        private final int child;

        private WidgetRef(int group, int child)
        {
            this.group = group;
            this.child = child;
        }
    }

    @Provides
    AccessibilityPlusConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AccessibilityPlusConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(dialogTextOverlay);
        overlayManager.add(minimapShapesOverlay);

        refreshBridgeState();
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(dialogTextOverlay);
        overlayManager.remove(minimapShapesOverlay);

        try
        {
            if (ttsController != null)
            {
                ttsController.shutdown();
            }
        }
        catch (Exception ignored)
        {
        }

        stopEmbeddedBridge();

        speakerName = "";
        dialogText = "";
        chatboxInputOpen = false;
        dialogOptions.clear();
        dialogBounds = null;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"accessibilityplus".equals(event.getGroup()))
        {
            return;
        }

        // emulate "button" behavior via boolean toggles
        if ("checkBridge".equals(event.getKey()) && config.checkBridge())
        {
            configManager.setConfiguration("accessibilityplus", "checkBridge", false);
            clientThread.invokeLater(() ->
            {
                refreshBridgeState();

                String msg;
                if (ttsController.isBridgeUp())
                {
                    msg = "Accessibility Plus: Speech bridge is running.";
                }
                else
                {
                    msg = "Accessibility Plus: Speech bridge is NOT running. " + ttsController.getLastBridgeError();
                }

                try
                {
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
                }
                catch (Exception ignored)
                {
                }
            });
            return;
        }

        if ("testTts".equals(event.getKey()) && config.testTts())
        {
            configManager.setConfiguration("accessibilityplus", "testTts", false);
            clientThread.invokeLater(() ->
            {
                refreshBridgeState();

                if (ttsController.isBridgeUp())
                {
                    ttsController.speakTest();
                    try
                    {
                        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Accessibility Plus: Sent test phrase.", null);
                    }
                    catch (Exception ignored)
                    {
                    }
                }
                else
                {
                    String msg = "Accessibility Plus: Speech bridge is NOT running. " + ttsController.getLastBridgeError();
                    try
                    {
                        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
                    }
                    catch (Exception ignored)
                    {
                    }
                }
            });
            return;
        }

        // Any relevant change: re-evaluate whether embedded bridge should be running.
        refreshBridgeState();
    }

    private void refreshBridgeState()
    {
        // Start embedded bridge if TTS enabled and backend is BRIDGE and toggle is on.
        if (config.enableTts()
                && config.ttsBackend() == AccessibilityPlusConfig.SpeechBackend.BRIDGE
                && config.startEmbeddedBridge())
        {
            ensureEmbeddedBridge();
            if (embeddedPort > 0)
            {
                ttsController.setBridgeBaseUrl("http://127.0.0.1:" + embeddedPort);
            }
            else
            {
                // fallback to whatever user configured as external bridge
                ttsController.setBridgeBaseUrl(config.bridgeBaseUrl());
            }
        }
        else
        {
            stopEmbeddedBridge();
            ttsController.setBridgeBaseUrl(config.bridgeBaseUrl());
        }

        ttsController.setCooldownMs(config.ttsCooldownMs());
        ttsController.checkBridgeNow();
    }

    private void ensureEmbeddedBridge()
    {
        int desiredPort = config.embeddedBridgePort();
        String piperPath = config.piperPath();
        String modelPath = config.piperModelPath();

        // Already running on same port
        if (embeddedBridge.isRunning() && embeddedPort == desiredPort)
        {
            return;
        }

        stopEmbeddedBridge();

        try
        {
            embeddedBridge.start(desiredPort, piperPath, modelPath);
            embeddedPort = desiredPort;
        }
        catch (IOException bindFailed)
        {
            embeddedPort = -1;
        }
        catch (Exception ignored)
        {
            embeddedPort = -1;
        }
    }

    private void stopEmbeddedBridge()
    {
        try
        {
            embeddedBridge.stop();
        }
        catch (Exception ignored)
        {
        }
        embeddedPort = -1;
    }

    @Subscribe
    public void onClientTick(ClientTick tick)
    {
        if (client == null || client.getGameState() != GameState.LOGGED_IN)
        {
            lastClientTickDialogKey = "";
            dialogActive = false;
            return;
        }

        // If the dialog UI just closed, stop any ongoing/queued TTS immediately.
        Widget npcTextW = client.getWidget(ComponentID.DIALOG_NPC_TEXT);
        Widget playerTextW = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);

        boolean hasDialogWidgets =
                (npcTextW != null && !isBlank(clean(getTextSafe(npcTextW))))
                        || (playerTextW != null && !isBlank(clean(getTextSafe(playerTextW))));

        // This catches Perdu / banker / GE style headers even when dialog widgets are empty
        boolean hasOptionHeader = hasOptionMenuHeaderQuick();

        // This catches options-only dialogs after the options are populated
        boolean hasOptions = dialogOptions != null && !dialogOptions.isEmpty();

        boolean nowActive = hasDialogWidgets || hasOptionHeader || hasOptions;

        if (dialogActive && !nowActive)
        {
            try
            {
                embeddedBridge.stopSpeechNow();
            }
            catch (Exception ignored)
            {
            }
            lastClientTickDialogKey = "";
        }

        dialogActive = nowActive;

        if (!config.enableTts() || config.ttsBackend() != AccessibilityPlusConfig.SpeechBackend.BRIDGE)
        {
            return;
        }

        // Speak dialog as soon as it changes, without waiting for the next GameTick.
        // Keep this lightweight: only read the direct dialog widgets.
        String npcName = clean(getTextSafe(ComponentID.DIALOG_NPC_NAME));
        String npcText = clean(getTextSafe(ComponentID.DIALOG_NPC_TEXT));
        String playerText = clean(getTextSafe(ComponentID.DIALOG_PLAYER_TEXT));

        String speaker;
        String dialog;

        if (!isBlank(npcText))
        {
            speaker = isBlank(npcName) ? "" : npcName;
            dialog = npcText;
        }
        else if (!isBlank(playerText))
        {
            speaker = "You";
            dialog = playerText;
        }
        else
        {
            speaker = "";
            dialog = "";
        }

        String speakSpeaker = config.ttsIncludeSpeaker() ? speaker : "";
        String speakDialog = config.ttsSpeakDialog() ? dialog : "";

        String key = speakSpeaker + "||" + speakDialog;
        if (!key.equals(lastClientTickDialogKey))
        {
            lastClientTickDialogKey = key;

            // Do NOT pass options here. Options are spoken later (when stable) on GameTick.
            ttsController.updateFromDialog(speakSpeaker, speakDialog, null);
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (client == null || client.getGameState() != GameState.LOGGED_IN)
        {
            speakerName = "";
            dialogText = "";
            chatboxInputOpen = false;
            dialogOptions.clear();
            dialogBounds = null;
            return;
        }

        chatboxInputOpen = isChatboxTyping();

        if (!config.enableDialogOverlay())
        {
            speakerName = "";
            dialogText = "";
            dialogOptions.clear();
            dialogBounds = null;
            return;
        }

        String npcName = getTextSafe(ComponentID.DIALOG_NPC_NAME);
        String npcText = getTextSafe(ComponentID.DIALOG_NPC_TEXT);

        if (!isBlank(npcText))
        {
            speakerName = clean(npcName);
            dialogText = clean(npcText);
        }
        else
        {
            String playerText = getTextSafe(ComponentID.DIALOG_PLAYER_TEXT);
            if (!isBlank(playerText))
            {
                speakerName = "You";
                dialogText = clean(playerText);
            }
            else
            {
                speakerName = "";
                dialogText = "";
            }
        }

        updateDialogOptionsAndBounds();

        if (config.enableTts() && ttsController != null && config.ttsBackend() == AccessibilityPlusConfig.SpeechBackend.BRIDGE)
        {
            ttsController.setCooldownMs(config.ttsCooldownMs());

            String speakSpeaker = config.ttsIncludeSpeaker() ? speakerName : "";
            String speakDialog = config.ttsSpeakDialog() ? dialogText : "";

            List<String> speakOptions = null;

            // Speak options only after they stabilize, so we don't announce partial lists as they populate.
            if (config.ttsSpeakOptions() && dialogOptions != null && !dialogOptions.isEmpty())
            {
                StringBuilder sb = new StringBuilder();
                int n = Math.min(10, dialogOptions.size());
                for (int i = 0; i < n; i++)
                {
                    String o = dialogOptions.get(i);
                    if (o != null)
                    {
                        sb.append(o.trim());
                    }
                    sb.append('|');
                }

                String optionsKey = sb.toString();
                long now = System.currentTimeMillis();

                if (!optionsKey.equals(pendingOptionsKey))
                {
                    pendingOptionsKey = optionsKey;
                    pendingOptionsFirstSeenAt = now;
                }

                // Require a short stable window before speaking.
                if (now - pendingOptionsFirstSeenAt >= 250L)
                {
                    speakOptions = dialogOptions;
                }
            }
            else
            {
                pendingOptionsKey = "";
                pendingOptionsFirstSeenAt = 0L;
            }

            ttsController.updateFromDialog(speakSpeaker, speakDialog, speakOptions);
        }
    }

    private boolean isChatboxTyping()
    {
        Widget chatboxInput = client.getWidget(ComponentID.CHATBOX_INPUT);
        return chatboxInput != null && !chatboxInput.isHidden();
    }

    private String getTextSafe(Widget w)
    {
        try
        {
            return w == null ? null : w.getText();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String getTextSafe(int componentId)
    {
        try
        {
            Widget w = client.getWidget(componentId);
            return w == null ? null : w.getText();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    // ----------------------------
    // NEW: quick header detection (safe for ClientTick)
    // ----------------------------
    private boolean hasOptionMenuHeaderQuick()
    {
        // Fast path: official option container
        try
        {
            Widget opt = client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS);
            if (opt != null && !opt.isHidden())
            {
                if (containsOptionMenuHeaderText(opt, 0))
                {
                    return true;
                }
            }
        }
        catch (Exception ignored)
        {
        }

        // Fallback: check cached roots we already found during scans
        try
        {
            for (WidgetRef ref : cachedOptionRoots)
            {
                Widget root = client.getWidget(ref.group, ref.child);
                if (root == null || root.isHidden())
                {
                    continue;
                }
                if (containsOptionMenuHeaderText(root, 0))
                {
                    return true;
                }
            }
        }
        catch (Exception ignored)
        {
        }

        return false;
    }

    private boolean containsOptionMenuHeaderText(Widget w, int depth)
    {
        if (w == null || w.isHidden() || depth > 10)
        {
            return false;
        }

        String t = clean(getTextSafe(w)).toLowerCase();
        if (isOptionMenuHeaderText(t))
        {
            return true;
        }

        Widget[] children = w.getChildren();
        if (children != null)
        {
            for (Widget c : children)
            {
                if (containsOptionMenuHeaderText(c, depth + 1))
                {
                    return true;
                }
            }
        }

        Widget[] staticChildren = w.getStaticChildren();
        if (staticChildren != null)
        {
            for (Widget c : staticChildren)
            {
                if (containsOptionMenuHeaderText(c, depth + 1))
                {
                    return true;
                }
            }
        }

        Widget[] dyn = w.getDynamicChildren();
        if (dyn != null)
        {
            for (Widget c : dyn)
            {
                if (containsOptionMenuHeaderText(c, depth + 1))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private void updateDialogOptionsAndBounds()
    {
        dialogOptions.clear();
        dialogBounds = null;

        // Anchor bounds to actual dialog widgets (safe, small)
        unionBounds(client.getWidget(ComponentID.DIALOG_NPC_TEXT));
        unionBounds(client.getWidget(ComponentID.DIALOG_PLAYER_TEXT));
        unionBounds(client.getWidget(ComponentID.DIALOG_NPC_NAME));

        final int[] candidateGroups = new int[]{219, 231, 193, 162, 161};
        final int maxChildScan = 1400;

        // Find the root(s) that contain the option menu header
        final List<WidgetRef> rootsToScan = new ArrayList<>();

        for (WidgetRef ref : cachedOptionRoots)
        {
            Widget root = client.getWidget(ref.group, ref.child);
            if (root == null || root.isHidden())
            {
                continue;
            }
            if (containsSelectAnOption(root, 0))
            {
                rootsToScan.add(ref);
            }
        }

        if (rootsToScan.isEmpty())
        {
            // Fallback: scan until we find a few plausible roots, then cache them.
            outer:
            for (int group : candidateGroups)
            {
                for (int child = 0; child < maxChildScan; child++)
                {
                    Widget root = client.getWidget(group, child);
                    if (root == null || root.isHidden())
                    {
                        continue;
                    }
                    if (containsSelectAnOption(root, 0))
                    {
                        rootsToScan.add(new WidgetRef(group, child));
                        if (rootsToScan.size() >= 3)
                        {
                            break outer;
                        }
                    }
                }
            }

            cachedOptionRoots.clear();
            cachedOptionRoots.addAll(rootsToScan);
        }

        if (rootsToScan.isEmpty())
        {
            return;
        }

        final List<OptionCandidate> candidates = new ArrayList<>();

        for (WidgetRef ref : rootsToScan)
        {
            Widget root = client.getWidget(ref.group, ref.child);
            if (root == null || root.isHidden())
            {
                continue;
            }
            collectOptionCandidates(root, 0, candidates);
        }

        candidates.sort(Comparator
                .comparingInt((OptionCandidate c) -> c.bounds != null ? c.bounds.y : Integer.MAX_VALUE)
                .thenComparingInt(c -> c.bounds != null ? c.bounds.x : Integer.MAX_VALUE));

        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (OptionCandidate c : candidates)
        {
            if (!seen.containsKey(c.text))
            {
                seen.put(c.text, Boolean.TRUE);
                dialogOptions.add(c.text);
                unionBoundsByRect(c.bounds);
            }
            if (dialogOptions.size() >= 10)
            {
                break;
            }
        }

        ensureDialogBoundsHeightForOptions(dialogOptions.size());
        clampDialogBoundsToCanvas();
    }

    private void clampDialogBoundsToCanvas()
    {
        if (dialogBounds == null || client == null)
        {
            return;
        }

        int cw = client.getCanvasWidth();
        int ch = client.getCanvasHeight();

        if (cw <= 0 || ch <= 0)
        {
            return;
        }

        int x = Math.max(0, dialogBounds.x);
        int y = Math.max(0, dialogBounds.y);
        int w = Math.min(dialogBounds.width, cw - x);
        int h = Math.min(dialogBounds.height, ch - y);

        if (w <= 0 || h <= 0)
        {
            dialogBounds = null;
            return;
        }

        dialogBounds = new Rectangle(x, y, w, h);
    }

    private void ensureDialogBoundsHeightForOptions(int optionCount)
    {
        if (dialogBounds == null || optionCount <= 0)
        {
            return;
        }

        final int topPadding = 18;
        final int bottomPadding = 18;
        final int perOptionHeight = 32;
        final int headerHeight = 18;

        int needed = topPadding + headerHeight + bottomPadding + (optionCount * perOptionHeight);

        if (dialogBounds.height < needed)
        {
            dialogBounds = new Rectangle(dialogBounds.x, dialogBounds.y, dialogBounds.width, needed);
        }
    }

    private static final class OptionCandidate
    {
        private final String text;
        private final Rectangle bounds;

        private OptionCandidate(String text, Rectangle bounds)
        {
            this.text = text;
            this.bounds = bounds;
        }
    }

    private boolean isOptionMenuHeaderText(String lowerText)
    {
        if (lowerText == null || lowerText.isEmpty())
        {
            return false;
        }

        // Common OSRS prompt headers for option menus
        return lowerText.contains("select an option")
                || lowerText.contains("what would you like to say")
                || lowerText.contains("what would you like to do")
                || lowerText.contains("what would you like to ask");
    }

    private boolean containsSelectAnOption(Widget w, int depth)
    {
        if (w == null || w.isHidden() || depth > 10)
        {
            return false;
        }

        String t = clean(w.getText()).toLowerCase();
        if (isOptionMenuHeaderText(t))
        {
            unionBounds(w);
            return true;
        }

        Widget[] children = w.getChildren();
        if (children != null)
        {
            for (Widget c : children)
            {
                if (containsSelectAnOption(c, depth + 1))
                {
                    return true;
                }
            }
        }

        Widget[] staticChildren = w.getStaticChildren();
        if (staticChildren != null)
        {
            for (Widget c : staticChildren)
            {
                if (containsSelectAnOption(c, depth + 1))
                {
                    return true;
                }
            }
        }

        Widget[] dyn = w.getDynamicChildren();
        if (dyn != null)
        {
            for (Widget c : dyn)
            {
                if (containsSelectAnOption(c, depth + 1))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private void collectOptionCandidates(Widget w, int depth, List<OptionCandidate> out)
    {
        if (w == null || w.isHidden() || depth > 10)
        {
            return;
        }

        String t = clean(w.getText());
        if (!t.isEmpty())
        {
            String lower = t.toLowerCase();

            // Header line should not become an option, but should count toward bounds
            if (isOptionMenuHeaderText(lower))
            {
                unionBounds(w);
            }
            else if (lower.contains("select an option") || lower.contains("click here to continue"))
            {
                unionBounds(w);
            }
            else
            {
                if (!t.equalsIgnoreCase(clean(dialogText)) && !t.equalsIgnoreCase(clean(speakerName)))
                {
                    if (!isChatTabLabel(lower) && looksLikeOptionLabel(t))
                    {
                        out.add(new OptionCandidate(t, w.getBounds()));
                    }
                }
            }
        }

        Widget[] children = w.getChildren();
        if (children != null)
        {
            for (Widget c : children)
            {
                collectOptionCandidates(c, depth + 1, out);
            }
        }

        Widget[] dyn = w.getDynamicChildren();
        if (dyn != null)
        {
            for (Widget c : dyn)
            {
                collectOptionCandidates(c, depth + 1, out);
            }
        }
    }

    private static boolean isChatTabLabel(String lower)
    {
        return lower.equals("all") || lower.equals("game") || lower.equals("public") || lower.equals("private")
                || lower.equals("channel") || lower.equals("clan") || lower.equals("trade") || lower.equals("friends");
    }

    private static boolean looksLikeOptionLabel(String t)
    {
        if (t == null)
        {
            return false;
        }

        String s = t.trim();
        if (s.length() < 2)
        {
            return false;
        }

        if (s.equalsIgnoreCase("on") || s.equalsIgnoreCase("off"))
        {
            return false;
        }

        if (s.matches("^\\d+\\.$"))
        {
            return false;
        }

        if (s.matches("^\\d+$") || s.matches("^\\d+:\\d+(?::\\d+)?$"))
        {
            return false;
        }

        if (looksLikeChatLine(s))
        {
            return false;
        }

        if (looksLikeSystemChatNoise(s))
        {
            return false;
        }

        return true;
    }

    private static boolean looksLikeChatLine(String s)
    {
        return s != null && s.trim().matches("^[A-Za-z0-9 _\\-]{1,12}:\\s+.+$");
    }

    private static boolean looksLikeSystemChatNoise(String s)
    {
        if (s == null)
        {
            return false;
        }
        String lower = s.trim().toLowerCase();
        return lower.contains("press enter to chat");
    }

    private void unionBoundsByRect(Rectangle b)
    {
        if (b == null)
        {
            return;
        }
        if (dialogBounds == null)
        {
            dialogBounds = new Rectangle(b);
        }
        else
        {
            dialogBounds = dialogBounds.union(b);
        }
    }

    private void unionBounds(Widget w)
    {
        if (w == null)
        {
            return;
        }
        Rectangle b = w.getBounds();
        if (b == null)
        {
            return;
        }
        if (dialogBounds == null)
        {
            dialogBounds = new Rectangle(b);
        }
        else
        {
            dialogBounds = dialogBounds.union(b);
        }
    }

    private static boolean isBlank(String s)
    {
        return s == null || s.trim().isEmpty();
    }

    private static String clean(String s)
    {
        if (s == null)
        {
            return "";
        }
        return s.replaceAll("<[^>]*>", " ")
                .replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }
}
