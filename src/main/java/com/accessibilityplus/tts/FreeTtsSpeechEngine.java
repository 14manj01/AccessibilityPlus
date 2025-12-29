package com.accessibilityplus.tts;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pure-Java TTS backend using FreeTTS.
 *
 * Notes:
 * - Voice quality is basic, but it works cross-platform without spawning processes.
 * - We serialize speech on a single-thread executor so we never block the client thread.
 */
@Slf4j
public class FreeTtsSpeechEngine implements SpeechEngine
{
    private static final String DEFAULT_VOICE = "kevin16";

    private final ExecutorService exec = Executors.newSingleThreadExecutor(r ->
    {
        Thread t = new Thread(r, "accessibilityplus-tts");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<Future<?>> current = new AtomicReference<>();

    private volatile Voice voice;
    private volatile boolean available;

    private volatile float rateWpm = 160f;
    private volatile float volume = 1.0f;

    public FreeTtsSpeechEngine()
    {
        try
        {
            // FreeTTS sometimes needs the voice directory classes on the classpath.
            // With the Maven artifact we include, kevin16 is usually available.
            VoiceManager vm = VoiceManager.getInstance();
            Voice v = vm.getVoice(DEFAULT_VOICE);
            if (v == null)
            {
                log.warn("FreeTTS voice '{}' not found. TTS will be unavailable.", DEFAULT_VOICE);
                available = false;
                return;
            }

            v.allocate();
            v.setRate(rateWpm);
            v.setVolume(volume);

            voice = v;
            available = true;
            log.info("FreeTTS initialized with voice '{}'", DEFAULT_VOICE);
        }
        catch (Exception e)
        {
            log.warn("Failed to initialize FreeTTS", e);
            available = false;
        }
    }

    public void setRateWpm(float rateWpm)
    {
        this.rateWpm = rateWpm;
        Voice v = this.voice;
        if (v != null)
        {
            try
            {
                v.setRate(rateWpm);
            }
            catch (Exception ignored)
            {
            }
        }
    }

    public void setVolume(float volume)
    {
        this.volume = volume;
        Voice v = this.voice;
        if (v != null)
        {
            try
            {
                v.setVolume(volume);
            }
            catch (Exception ignored)
            {
            }
        }
    }

    @Override
    public boolean isAvailable()
    {
        return available && voice != null;
    }

    @Override
    public void speak(String text)
    {
        if (!isAvailable())
        {
            return;
        }
        final String t = Objects.requireNonNullElse(text, "").trim();
        if (t.isEmpty())
        {
            return;
        }

        Future<?> f = exec.submit(() ->
        {
            Voice v = voice;
            if (v == null)
            {
                return;
            }
            try
            {
                v.speak(t);
            }
            catch (Exception e)
            {
                log.debug("FreeTTS speak failed", e);
            }
        });

        current.set(f);
    }

    @Override
    public void stop()
    {
        // FreeTTS doesn't provide a reliable "stop speaking mid-utterance" API.
        // Best-effort: cancel queued work and rely on short utterances + cooldown.
        Future<?> f = current.getAndSet(null);
        if (f != null)
        {
            f.cancel(true);
        }
        exec.shutdownNow();
    }

    @Override
    public void shutdown()
    {
        try
        {
            stop();
        }
        finally
        {
            Voice v = voice;
            voice = null;
            if (v != null)
            {
                try
                {
                    v.deallocate();
                }
                catch (Exception ignored)
                {
                }
            }
        }
    }
}
