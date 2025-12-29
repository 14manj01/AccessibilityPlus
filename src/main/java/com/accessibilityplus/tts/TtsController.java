package com.accessibilityplus.tts;

import com.accessibilityplus.AccessibilityPlusConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * High-level TTS coordinator:
 * - De-dupe on dialog/options keys so we do not speak every tick.
 * - Preempt best-effort on user advancing dialog (clicking next / selecting an option).
 * - Delegates actual speech to SpeechEngine.
 */
@Slf4j
@Singleton
public class TtsController
{
    private final AccessibilityPlusConfig config;
    private final SpeechEngineFactory engineFactory;

    private volatile SpeechEngine engine;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private String lastSpokenDialogKey = "";
    private String lastSpokenOptionsKey = "";
    private long lastSpokenAt = 0L;

    // When the user clicks through, suppress speaking stale option lists for a short window.
    private volatile long suppressUntil = 0L;

    @Inject
    public TtsController(AccessibilityPlusConfig config, SpeechEngineFactory engineFactory)
    {
        this.config = config;
        this.engineFactory = engineFactory;
    }

    public synchronized void refreshEngine()
    {
        shutdownEngineOnly();

        if (!config.enableTts())
        {
            engine = null;
            started.set(false);
            return;
        }

        engine = engineFactory.create();
        started.set(true);
    }

    public synchronized void shutdown()
    {
        shutdownEngineOnly();
        started.set(false);
    }

    private void shutdownEngineOnly()
    {
        SpeechEngine e = engine;
        engine = null;

        if (e != null)
        {
            try
            {
                e.shutdown();
            }
            catch (Exception ignored)
            {
            }
        }

        lastSpokenDialogKey = "";
        lastSpokenOptionsKey = "";
        lastSpokenAt = 0L;
        suppressUntil = 0L;
    }

    /**
     * Called when the user clicks "continue" or selects a menu option.
     * We cannot guarantee mid-buffer cut, but we can:
     * - cancel in-flight HTTP work
     * - invalidate any queued playback
     * - suppress repeating the old option list
     */
    public void onUserAdvanceDialog()
    {
        suppressUntil = System.currentTimeMillis() + 750L;

        SpeechEngine e = engine;
        if (e != null)
        {
            try
            {
                e.stopNow();
            }
            catch (Exception ignored)
            {
            }
        }
    }

    public void speakTest()
    {
        if (!config.enableTts())
        {
            return;
        }

        SpeechEngine e = engine;
        if (e == null)
        {
            refreshEngine();
            e = engine;
        }
        if (e == null)
        {
            return;
        }

        e.speak("Accessibility Plus text to speech test.");
    }

    public void updateFromDialog(String speaker, String dialogText, List<String> options)
    {
        if (!config.enableTts())
        {
            return;
        }

        SpeechEngine e = engine;
        if (e == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < suppressUntil)
        {
            return;
        }

        String dialogKey = buildDialogKey(speaker, dialogText);
        if (!dialogKey.isEmpty() && shouldSpeakNow(dialogKey, true))
        {
            lastSpokenDialogKey = dialogKey;
            lastSpokenAt = now;

            String phrase = buildDialogPhrase(speaker, dialogText);
            if (!phrase.isEmpty())
            {
                e.speak(phrase);
            }
        }

        if (options != null && !options.isEmpty())
        {
            String optionsKey = buildOptionsKey(options);
            if (!optionsKey.isEmpty() && shouldSpeakNow(optionsKey, false))
            {
                lastSpokenOptionsKey = optionsKey;
                lastSpokenAt = now;

                String phrase = buildOptionsPhrase(options);
                if (!phrase.isEmpty())
                {
                    e.speak(phrase);
                }
            }
        }
    }

    private boolean shouldSpeakNow(String key, boolean isDialog)
    {
        long now = System.currentTimeMillis();

        int cooldown = Math.max(0, config.ttsCooldownMs());
        if (cooldown > 0 && (now - lastSpokenAt) < cooldown)
        {
            return false;
        }

        if (isDialog)
        {
            return !Objects.equals(key, lastSpokenDialogKey);
        }
        return !Objects.equals(key, lastSpokenOptionsKey);
    }

    private static String buildDialogKey(String speaker, String dialogText)
    {
        String s = safe(speaker);
        String t = safe(dialogText);

        if (t.isEmpty())
        {
            return "";
        }

        return s + "|" + t;
    }

    private static String buildOptionsKey(List<String> options)
    {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(10, options.size());
        for (int i = 0; i < n; i++)
        {
            String o = safe(options.get(i));
            if (!o.isEmpty())
            {
                sb.append(o);
            }
            sb.append('|');
        }
        return sb.toString();
    }

    private String buildDialogPhrase(String speaker, String dialogText)
    {
        String t = safe(dialogText);
        if (t.isEmpty())
        {
            return "";
        }

        if (config.ttsIncludeSpeaker() && !safe(speaker).isEmpty())
        {
            return safe(speaker) + ". " + t;
        }

        return t;
    }

    private String buildOptionsPhrase(List<String> options)
    {
        List<String> clean = new ArrayList<>();
        int n = Math.min(10, options.size());
        for (int i = 0; i < n; i++)
        {
            String o = safe(options.get(i));
            if (!o.isEmpty())
            {
                clean.add(o);
            }
        }

        if (clean.isEmpty())
        {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Options. ");
        for (int i = 0; i < clean.size(); i++)
        {
            sb.append(i + 1).append(". ").append(clean.get(i));
            if (i + 1 < clean.size())
            {
                sb.append(". ");
            }
        }
        return sb.toString();
    }

    private static String safe(String s)
    {
        if (s == null)
        {
            return "";
        }
        return s.trim();
    }
}
