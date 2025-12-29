package com.accessibilityplus.tts;

import com.accessibilityplus.AccessibilityPlusConfig;

/**
 * Creates a SpeechEngine from config.
 *
 * Plugin Hub safe: Bridge-only. No FreeTTS references or dependencies.
 */
public final class SpeechEngineFactory
{
    private SpeechEngineFactory()
    {
    }

    public static SpeechEngine create(AccessibilityPlusConfig config)
    {
        // Default endpoint if config is missing or incomplete
        String base = "http://127.0.0.1:59125";
        int timeoutMs = 5000;

        if (config != null)
        {
            if (config.bridgeBaseUrl() != null && !config.bridgeBaseUrl().trim().isEmpty())
            {
                base = config.bridgeBaseUrl().trim();
            }
            timeoutMs = config.ttsBridgeTimeoutMs();
        }

        String endpoint = base;
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

        return new BridgeSpeechEngine(endpoint, timeoutMs);
    }
}
