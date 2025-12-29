package com.accessibilityplus.tts;

/**
 * Simple abstraction so we can swap TTS backends later.
 *
 * Contract:
 * - speak() must return quickly (do work off-thread).
 * - stopNow() should best-effort stop current and cancel queued work.
 */
public interface SpeechEngine
{
    boolean isAvailable();

    void speak(String text);

    void stopNow();

    void shutdown();
}
