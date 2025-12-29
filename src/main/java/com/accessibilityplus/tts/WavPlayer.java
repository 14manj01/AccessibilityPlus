package com.accessibilityplus.tts;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class WavPlayer
{
    private static final AtomicReference<Clip> CURRENT = new AtomicReference<>();

    private WavPlayer()
    {
    }

    /**
     * Stop any currently playing audio immediately.
     */
    public static void stop()
    {
        Clip c = CURRENT.getAndSet(null);
        if (c != null)
        {
            try
            {
                c.stop();
            }
            catch (Exception ignored)
            {
            }
            try
            {
                c.close();
            }
            catch (Exception ignored)
            {
            }
        }
    }

    /**
     * Play the WAV file and block until playback completes.
     */
    public static void playBlocking(File wavFile)
    {
        if (wavFile == null || !wavFile.isFile())
        {
            return;
        }

        // Stop any existing audio before starting new playback.
        stop();

        AudioInputStream stream = null;
        Clip clip = null;
        CountDownLatch done = new CountDownLatch(1);

        try
        {
            stream = AudioSystem.getAudioInputStream(wavFile);
            clip = AudioSystem.getClip();
            clip.open(stream);

            Clip finalClip = clip;
            clip.addLineListener(new LineListener()
            {
                @Override
                public void update(LineEvent event)
                {
                    LineEvent.Type type = event.getType();
                    if (type == LineEvent.Type.STOP || type == LineEvent.Type.CLOSE)
                    {
                        done.countDown();
                        try
                        {
                            finalClip.removeLineListener(this);
                        }
                        catch (Exception ignored)
                        {
                        }
                    }
                }
            });

            CURRENT.set(clip);
            clip.start();
            done.await();
        }
        catch (Exception e)
        {
            log.debug("Wav playback failed: {}", e.toString());
        }
        finally
        {
            // Ensure CURRENT is cleared if we're the active clip.
            Clip cur = CURRENT.get();
            if (cur == clip)
            {
                CURRENT.compareAndSet(cur, null);
            }

            try
            {
                if (clip != null)
                {
                    clip.stop();
                    clip.close();
                }
            }
            catch (Exception ignored)
            {
            }

            try
            {
                if (stream != null)
                {
                    stream.close();
                }
            }
            catch (Exception ignored)
            {
            }
        }
    }
}
