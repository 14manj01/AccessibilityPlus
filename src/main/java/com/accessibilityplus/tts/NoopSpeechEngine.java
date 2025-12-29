package com.accessibilityplus.tts;

/**
 * No-op SpeechEngine used when TTS is disabled or backend is unavailable.
 * Safe for Plugin Hub. Performs no work.
 */
public final class NoopSpeechEngine implements SpeechEngine
{
    @Override
    public boolean isAvailable()
    {
        return false;
    }

    @Override
    public void speak(String text)
    {
        // intentionally empty
    }

    @Override
    public void stop()
    {
        // intentionally empty
    }

    @Override
    public void shutdown()
    {
        // intentionally empty
    }
}
