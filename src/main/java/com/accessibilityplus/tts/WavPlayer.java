package com.accessibilityplus.tts;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

@Slf4j
@Singleton
public class WavPlayer
{
    /**
     * Used to invalidate any in-flight or queued audio.
     * Bumping generation also triggers a best-effort hard cut.
     */
    private final AtomicLong generation = new AtomicLong(0);

    /**
     * AudioPlayer has no explicit stop API.
     * We approximate a hard cut by immediately playing a tiny silent WAV.
     */
    private static final float SPEECH_GAIN_DB = 0.0f;
    private static final float SILENT_GAIN_DB = -80.0f;

    // 10ms of silence, 8kHz, 16-bit, mono PCM WAV.
    private static final byte[] SILENT_WAV = new byte[] {
            82, 73, 70, 70, -60, 0, 0, 0, 87, 65, 86, 69, 102, 109, 116, 32,
            16, 0, 0, 0, 1, 0, 1, 0, 64, 31, 0, 0, -128, 62, 0, 0,
            2, 0, 16, 0, 100, 97, 116, 97, -96, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    private final AudioPlayer audioPlayer;

    /**
     * Serialize AudioPlayer calls so overlapping requests do not interleave.
     */
    private final Object playLock = new Object();

    @Inject
    public WavPlayer(final AudioPlayer audioPlayer)
    {
        this.audioPlayer = audioPlayer;
    }

    /**
     * Invalidate all current and pending audio.
     * Call this when the dialog advances, a new line appears, or an option is clicked.
     */
    public long bumpGeneration()
    {
        long gen = generation.incrementAndGet();
        hardCutBestEffort();
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
     *
     * IMPORTANT:
     * - No Java Sound usage (Plugin Hub rejection)
     * - No disk writes
     * - Audio is played from an in-memory stream via RuneLite's AudioPlayer
     */
    public void playBytesIfCurrent(final byte[] wavBytes, final long expectedGeneration)
    {
        if (wavBytes == null || wavBytes.length == 0)
        {
            return;
        }

        if (generation.get() != expectedGeneration)
        {
            return;
        }

        synchronized (playLock)
        {
            if (generation.get() != expectedGeneration)
            {
                return;
            }

            try
            {
                audioPlayer.play(new ByteArrayInputStream(wavBytes), SPEECH_GAIN_DB);
            }
            catch (Exception e)
            {
                log.debug("RuneLite AudioPlayer playback failed: {}", e.toString());
            }
        }
    }

    private void hardCutBestEffort()
    {
        synchronized (playLock)
        {
            try
            {
                audioPlayer.play(new ByteArrayInputStream(SILENT_WAV), SILENT_GAIN_DB);
            }
            catch (Exception ignored)
            {
            }
        }
    }
}
