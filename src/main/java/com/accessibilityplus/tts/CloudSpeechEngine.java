package com.accessibilityplus.tts;

import com.accessibilityplus.AccessibilityPlusConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class CloudSpeechEngine implements SpeechEngine
{
    private final OkHttpClient http;
    private final AccessibilityPlusConfig config;
    private final ExecutorService executor;
    private final WavPlayer wavPlayer;

    private volatile Call inFlight;

    @Inject
    public CloudSpeechEngine(
            OkHttpClient http,
            AccessibilityPlusConfig config,
            ExecutorService executor,
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
        if (!config.enableTts())
        {
            return false;
        }

        String base = config.cloudTtsBaseUrl();
        return base != null && !base.trim().isEmpty();
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
                String base = config.cloudTtsBaseUrl().trim();

                // Match the "TTS" plugin style:
                // https://ttsplugin.com?m=<text>&r=<rate>&v=<voice>
                String url = base
                        + "?m=" + URLEncoder.encode(text, StandardCharsets.UTF_8.name())
                        + "&r=" + config.cloudTtsRate()
                        + "&v=" + config.cloudTtsVoice();

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

                    // Drop late responses if user clicked through
                    if (wavPlayer.currentGeneration() != gen)
                    {
                        return;
                    }

                    File tmp = File.createTempFile("ap_tts_", ".wav");
                    tmp.deleteOnExit();

                    try (FileOutputStream fos = new FileOutputStream(tmp))
                    {
                        fos.write(wav);
                    }

                    wavPlayer.playIfCurrent(tmp, gen);
                }
            }
            catch (IOException e)
            {
                // Most common path when user clicks quickly
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
