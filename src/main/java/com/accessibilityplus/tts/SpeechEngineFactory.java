package com.accessibilityplus.tts;

import com.accessibilityplus.AccessibilityPlusConfig;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.OkHttpClient;

@Singleton
public class SpeechEngineFactory
{
    private final AccessibilityPlusConfig config;
    private final OkHttpClient http;
    private final ScheduledExecutorService executor;
    private final WavPlayer wavPlayer;

    @Inject
    public SpeechEngineFactory(
            AccessibilityPlusConfig config,
            OkHttpClient http,
            ScheduledExecutorService executor,
            WavPlayer wavPlayer
    )
    {
        this.config = config;
        this.http = http;
        this.executor = executor;
        this.wavPlayer = wavPlayer;
    }

    public SpeechEngine create()
    {
        if (!config.enableTts())
        {
            return new NoopSpeechEngine();
        }

        return new CloudSpeechEngine(http, config, executor, wavPlayer);
    }
}
