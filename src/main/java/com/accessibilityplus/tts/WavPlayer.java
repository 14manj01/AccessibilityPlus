package com.accessibilityplus.tts;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

@Slf4j
@Singleton
public class WavPlayer
{
    private final AudioPlayer audioPlayer;

    /**
     * Used to invalidate any in-flight or queued audio.
     * RuneLite does not allow hard stop; this prevents stale playback.
     */
    private final AtomicLong generation = new AtomicLong(0);

    @Inject
    public WavPlayer(AudioPlayer audioPlayer)
    {
        this.audioPlayer = audioPlayer;
    }

    /**
     * Invalidate all current and pending audio.
     * Call this when the user clicks a dialog option.
     */
    public long bumpGeneration()
    {
        return generation.incrementAndGet();
    }

    /**
     * Snapshot the current generation.
     */
    public long currentGeneration()
    {
        return generation.get();
    }

    /**
     * Play only if this audio still belongs to the current generation.
     */
    public void playIfCurrent(File wavFile, long expectedGeneration)
    {
        if (wavFile == null || !wavFile.isFile())
        {
            return;
        }

        if (generation.get() != expectedGeneration)
        {
            return;
        }

        try
        {
            audioPlayer.play(wavFile, 0.0f);
        }
        catch (Exception e)
        {
            log.debug("Audio playback failed: {}", e.toString());
        }
    }
}
