package com.accessibilityplus.tts;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BridgeSpeechEngine implements SpeechEngine
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final URI endpoint;
    private final OkHttpClient http;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Create a bridge speech engine that POSTs JSON to the configured endpoint.
     *
     * @param okHttp RuneLite-injected OkHttpClient
     * @param url Full URL to POST to (example: http://127.0.0.1:59125/speak)
     * @param timeoutMs request timeout in ms (min 100ms)
     */
    public BridgeSpeechEngine(OkHttpClient okHttp, String url, int timeoutMs)
    {
        Objects.requireNonNull(okHttp, "okHttp");
        Objects.requireNonNull(url, "url");

        long ms = Math.max(100, timeoutMs);

        // RuneLite supplies the base OkHttpClient. Clone it with a call timeout.
        this.http = okHttp.newBuilder()
                .callTimeout(Duration.ofMillis(ms))
                .build();

        this.endpoint = URI.create(url.trim());

        ThreadFactory tf = r ->
        {
            Thread t = new Thread(r, "ap-tts-bridge");
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newSingleThreadExecutor(tf);
    }

    @Override
    public boolean isAvailable()
    {
        return !closed.get();
    }

    @Override
    public void speak(String text)
    {
        if (closed.get())
        {
            return;
        }

        final String payload = toJsonPayload(text);
        executor.execute(() -> doSpeak(payload));
    }

    @Override
    public void stop()
    {
        // Fire-and-forget bridge: no stop semantics.
    }

    @Override
    public void shutdown()
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        executor.shutdownNow();
    }

    private void doSpeak(String payload)
    {
        if (closed.get())
        {
            return;
        }

        // IMPORTANT: OkHttp (RuneLite) uses create(MediaType, String) signature
        RequestBody body = RequestBody.create(JSON, payload);

        Request req = new Request.Builder()
                .url(endpoint.toString())
                .post(body)
                .build();

        try (Response resp = http.newCall(req).execute())
        {
            // Intentionally ignore response body.
        }
        catch (Exception ignored)
        {
            // Never crash the client on TTS failures.
        }
    }

    private static String toJsonPayload(String text)
    {
        String s = (text == null) ? "" : text.trim();
        return "{\"text\":\"" + escapeJson(s) + "\"}";
    }

    private static String escapeJson(String s)
    {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 32)
                    {
                        sb.append(String.format("\\u%04x", (int) c));
                    }
                    else
                    {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
