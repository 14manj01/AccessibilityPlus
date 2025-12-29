package com.accessibilityplus.tts;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class WavPlayer
{
    /**
     * Used to invalidate any in-flight or queued audio.
     * We also stop/close the current Clip when generation bumps.
     */
    private final AtomicLong generation = new AtomicLong(0);

    private final Object clipLock = new Object();
    private Clip currentClip;

    @Inject
    public WavPlayer()
    {
        // No RuneLite AudioPlayer; pure Java Sound Clip for Plugin Hub friendliness.
    }

    /**
     * Invalidate all current and pending audio.
     * Call this when the dialog advances, a new line appears, or an option is clicked.
     */
    public long bumpGeneration()
    {
        long gen = generation.incrementAndGet();
        stopCurrentClip();
        return gen;
    }

    /**
     * Snapshot the current generation.
     */
    public long currentGeneration()
    {
        return generation.get();
    }

    /**
     * Play WAV bytes only if this audio still belongs to the current generation.
     */
    public void playBytesIfCurrent(byte[] wavBytes, long expectedGeneration)
    {
        if (wavBytes == null || wavBytes.length == 0)
        {
            return;
        }

        if (generation.get() != expectedGeneration)
        {
            return;
        }

        synchronized (clipLock)
        {
            if (generation.get() != expectedGeneration)
            {
                return;
            }

            stopCurrentClipLocked();

            try (ByteArrayInputStream bais = new ByteArrayInputStream(wavBytes);
                 AudioInputStream ais = AudioSystem.getAudioInputStream(bais))
            {
                Clip clip = AudioSystem.getClip();
                clip.open(ais);

                // If generation changes while we were opening, abort.
                if (generation.get() != expectedGeneration)
                {
                    try
                    {
                        clip.stop();
                    }
                    catch (Exception ignored)
                    {
                    }
                    try
                    {
                        clip.close();
                    }
                    catch (Exception ignored)
                    {
                    }
                    return;
                }

                currentClip = clip;
                clip.start();
            }
            catch (Exception e)
            {
                log.debug("Audio playback failed: {}", e.toString());
                stopCurrentClipLocked();
            }
        }
    }

    private void stopCurrentClip()
    {
        synchronized (clipLock)
        {
            stopCurrentClipLocked();
        }
    }

    private void stopCurrentClipLocked()
    {
        if (currentClip != null)
        {
            try
            {
                currentClip.stop();
            }
            catch (Exception ignored)
            {
            }

            try
            {
                currentClip.close();
            }
            catch (Exception ignored)
            {
            }

            currentClip = null;
        }
    }
}
