package com.accessibilityplus.tts;

/**
 * No-op TTS backend (used when disabled or misconfigured).
 */
public class NoopSpeechEngine implements SpeechEngine
{
    @Override
    public boolean isAvailable()
    {
        return false;
    }

    @Override
    public void speak(String text)
    {
        // no-op
    }

    @Override
    public void stopNow()
    {
        // no-op
    }

    @Override
    public void shutdown()
    {
        // no-op
    }
}
