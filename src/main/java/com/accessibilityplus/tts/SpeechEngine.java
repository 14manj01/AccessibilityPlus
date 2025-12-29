package com.accessibilityplus.tts;

/**
 * Simple abstraction so we can swap TTS backends later.
 *
 * Contract:
 * - speak() must return quickly (do work off-thread).
 * - implementers should internally serialize speech requests.
 */
public interface SpeechEngine
{
    boolean isAvailable();

    void speak(String text);

    void stop();

    void shutdown();
}
