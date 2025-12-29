package com.accessibilityplus;

import com.accessibilityplus.tts.TtsController;
import com.google.inject.Provides;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
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
    description = "Accessibility improvements: large dialog overlay + minimap shapes + optional TTS via cloud service.",
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

        if (ttsController != null)
        {
            ttsController.refreshEngine();
        }
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

        speakerName = "";
        dialogText = "";
        chatboxInputOpen = false;
        dialogOptions.clear();
        dialogBounds = null;

        pendingOptionsKey = "";
        pendingOptionsFirstSeenAt = 0L;
        cachedOptionRoots.clear();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"accessibilityplus".equals(event.getGroup()))
        {
            return;
        }

        // Emulate "button" behavior via boolean toggle
        if ("testTts".equals(event.getKey()) && config.testTts())
        {
            configManager.setConfiguration("accessibilityplus", "testTts", false);
            clientThread.invokeLater(() ->
            {
                try
                {
                    ttsController.speakTest();
                }
                catch (Exception ignored)
                {
                }
            });
            return;
        }

        // Rebuild speech engine on relevant config changes
        String key = event.getKey();
        if ("enableTts".equals(key) || key.startsWith("cloudTts") || key.startsWith("tts"))
        {
            clientThread.invokeLater(() ->
            {
                try
                {
                    ttsController.refreshEngine();
                }
                catch (Exception ignored)
                {
                }
            });
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked ev)
    {
        if (ttsController == null || !config.enableTts())
        {
            return;
        }

        // When the user clicks to continue or chooses an option, immediately stop current playback
        // and suppress reading the old option list again.
        ttsController.onUserAdvanceDialog();
        pendingOptionsKey = "";
        pendingOptionsFirstSeenAt = 0L;
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

        if (config.enableTts() && ttsController != null)
        {
            String speakSpeaker = speakerName;
            String speakDialog = dialogText;

            List<String> speakOptions = null;

            // Only attempt to speak options when there are options on screen.
            if (!dialogOptions.isEmpty())
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
