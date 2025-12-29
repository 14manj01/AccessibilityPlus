package com.accessibilityplus.tts;

import com.accessibilityplus.AccessibilityPlusConfig;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Creates a SpeechEngine from config.
 *
 * Plugin Hub safe: Bridge-only. No FreeTTS references or dependencies.
 */
@Singleton
public final class SpeechEngineFactory
{
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:59125";

    private final OkHttpClient http;
    private final AccessibilityPlusConfig config;

    @Inject
    public SpeechEngineFactory(OkHttpClient http, AccessibilityPlusConfig config)
    {
        this.http = http;
        this.config = config;
    }

    public SpeechEngine create()
    {
        // If you have other backends later, gate them here.
        // For Plugin Hub compliance right now, BRIDGE is the only supported backend.
        if (config == null || config.ttsBackend() != AccessibilityPlusConfig.SpeechBackend.BRIDGE)
        {
            return new NoopSpeechEngine();
        }

        int timeoutMs = config.ttsBridgeTimeoutMs();

        // If embedded bridge is enabled, we always target localhost + configured port.
        // Otherwise use the user-provided base URL.
        String base;
        if (config.startEmbeddedBridge())
        {
            base = "http://127.0.0.1:" + config.embeddedBridgePort();
        }
        else
        {
            base = safeBaseUrl(config.bridgeBaseUrl());
        }

        String endpoint = ensureSpeakEndpoint(base);

        return new BridgeSpeechEngine(http, endpoint, timeoutMs);
    }

    private static String safeBaseUrl(String baseUrl)
    {
        if (baseUrl == null)
        {
            return DEFAULT_BASE_URL;
        }

        String trimmed = baseUrl.trim();
        return trimmed.isEmpty() ? DEFAULT_BASE_URL : trimmed;
    }

    private static String ensureSpeakEndpoint(String base)
    {
        String endpoint = base;

        // Normalize trailing slash
        if (endpoint.endsWith("/"))
        {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }

        // Append /speak if missing
        if (!endpoint.endsWith("/speak"))
        {
            endpoint = endpoint + "/speak";
        }

        return endpoint;
    }
}
