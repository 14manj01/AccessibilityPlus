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
     * Used to invalidate in-flight work when we "stop".
     * AudioPlayer does not expose a hard stop API, so we prevent new playbacks
     * that belong to an older generation.
     */
    private final AtomicLong generation = new AtomicLong(0);

    @Inject
    public WavPlayer(AudioPlayer audioPlayer)
    {
        this.audioPlayer = audioPlayer;
    }

    /**
     * Cancels any queued or about-to-play audio.
     * This cannot forcibly stop a clip already playing via AudioPlayer,
     * but it prevents stale generations from starting.
     */
    public void stopNow()
    {
        generation.incrementAndGet();
    }

    /**
     * Plays the wav file only if the generation hasn't changed.
     * Gain uses AudioPlayer's float parameter, where 0.0f is typically "no change".
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
            // 0.0f is "no gain adjustment" for most AudioPlayer implementations.
            audioPlayer.play(wavFile, 0.0f);
        }
        catch (Exception e)
        {
            log.debug("Audio playback failed: {}", e.toString());
        }
    }

    /**
     * Snapshot the current generation so callers can guard playback.
     */
    public long currentGeneration()
    {
        return generation.get();
    }
}
