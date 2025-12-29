package com.accessibilityplus.tts;

import com.accessibilityplus.AccessibilityPlusConfig;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class CloudSpeechEngine implements SpeechEngine
{
    /**
     * Hardcode the only allowed endpoint for Plugin Hub compliance.
     * If you need to change it later, ship an update rather than allowing arbitrary URLs.
     */
    private static final String TTS_HOST = "ttsplugin.com";
    private static final String TTS_SCHEME = "https";

    private final OkHttpClient http;
    private final AccessibilityPlusConfig config;
    private final ScheduledExecutorService executor;
    private final WavPlayer wavPlayer;

    private volatile Call inFlight;

    @Inject
    public CloudSpeechEngine(
            OkHttpClient http,
            AccessibilityPlusConfig config,
            ScheduledExecutorService executor,
            WavPlayer wavPlayer
    )
    {
        this.http = http;
        this.config = config;
        this.executor = executor;
        this.wavPlayer = wavPlayer;
    }

    @Override
    public boolean isAvailable()
    {
        return config.enableTts();
    }

    @Override
    public void speak(String text)
    {
        if (!isAvailable())
        {
            return;
        }

        if (text == null || text.trim().isEmpty())
        {
            return;
        }

        final long gen = wavPlayer.bumpGeneration();

        Call prev = inFlight;
        if (prev != null)
        {
            prev.cancel();
        }

        executor.execute(() ->
        {
            try
            {
                HttpUrl url = new HttpUrl.Builder()
                        .scheme(TTS_SCHEME)
                        .host(TTS_HOST)
                        // If your service expects a path, set it here:
                        // .addPathSegment("tts")
                        .addQueryParameter("m", text)
                        .addQueryParameter("r", String.valueOf(config.cloudTtsRate()))
                        .addQueryParameter("v", String.valueOf(config.cloudTtsVoice()))
                        .build();

                Request req = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

                Call call = http.newCall(req);
                inFlight = call;

                try (Response res = call.execute())
                {
                    if (!res.isSuccessful() || res.body() == null)
                    {
                        return;
                    }

                    byte[] wav = res.body().bytes();

                    if (wavPlayer.currentGeneration() != gen)
                    {
                        return;
                    }

                    wavPlayer.playBytesIfCurrent(wav, gen);
                }
            }
            catch (IOException e)
            {
                if (!"Canceled".equalsIgnoreCase(e.getMessage()))
                {
                    log.debug("Cloud TTS failed: {}", e.toString());
                }
            }
            catch (Exception e)
            {
                log.debug("Cloud TTS unexpected error: {}", e.toString());
            }
        });
    }

    @Override
    public void stopNow()
    {
        wavPlayer.bumpGeneration();

        Call c = inFlight;
        if (c != null)
        {
            c.cancel();
        }
    }

    @Override
    public void shutdown()
    {
        stopNow();
    }
}
