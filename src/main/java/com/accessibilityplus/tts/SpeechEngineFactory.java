package com.accessibilityplus.tts;

import com.accessibilityplus.AccessibilityPlusConfig;

/**
 * Creates a SpeechEngine from config.
 *
 * Not currently used by AccessibilityPlusPlugin (which uses TtsController),
 * but kept for future expansion.
 */
public final class SpeechEngineFactory
{
    private SpeechEngineFactory()
    {
    }

    public static SpeechEngine create(AccessibilityPlusConfig config)
    {
        if (config == null)
        {
            return new FreeTtsSpeechEngine();
        }

        AccessibilityPlusConfig.SpeechBackend backend = config.ttsBackend();
        if (backend == null)
        {
            backend = AccessibilityPlusConfig.SpeechBackend.BRIDGE;
        }

        switch (backend)
        {
            case BRIDGE:
                String base = config.bridgeBaseUrl();
                if (base == null || base.trim().isEmpty())
                {
                    base = "http://127.0.0.1:59125";
                }
                String endpoint = base.trim();
                if (!endpoint.endsWith("/speak"))
                {
                    if (endpoint.endsWith("/"))
                    {
                        endpoint = endpoint + "speak";
                    }
                    else
                    {
                        endpoint = endpoint + "/speak";
                    }
                }
                return new BridgeSpeechEngine(endpoint, config.ttsBridgeTimeoutMs());
            case FREETTS:
            default:
                return new FreeTtsSpeechEngine();
        }
    }
}
