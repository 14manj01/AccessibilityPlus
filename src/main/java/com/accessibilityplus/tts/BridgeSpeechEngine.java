package com.accessibilityplus.tts;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridge mode: POST text to a locally running speech service (outside RuneLite).
 *
 * This implementation does NOT download audio or play WAVs. It simply sends
 * speech requests and expects the bridge to handle synthesis and playback.
 *
 * This matches the Natural Speech architecture and avoids shipping native
 * binaries or invoking external processes from inside RuneLite.
 */
public final class BridgeSpeechEngine implements SpeechEngine
{
    private final URI endpoint;
    private final Duration timeout;
    private final HttpClient http;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public BridgeSpeechEngine(String url, int timeoutMs)
    {
        this.endpoint = URI.create(Objects.requireNonNull(url, "url").trim());
        this.timeout = Duration.ofMillis(Math.max(100, timeoutMs));
        this.http = HttpClient.newBuilder()
            .connectTimeout(this.timeout)
            .build();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ap-tts-bridge");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public boolean isAvailable()
    {
        // We don't health-check here to keep it cheap; controller can probe if desired.
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

        executor.execute(() -> {
            if (closed.get())
            {
                return;
            }

            try
            {
                HttpRequest req = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

                // We only care that the request was accepted.
                http.send(req, HttpResponse.BodyHandlers.discarding());
            }
            catch (Exception ignored)
            {
                // Fail silently: bridge may be down, user may not want speech, etc.
            }
        });
    }

    @Override
    public void stop()
    {
        // Bridge is responsible for stopping speech, if it supports it.
        // We intentionally no-op here to avoid requiring a control endpoint.
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

    private static String toJsonPayload(String text)
    {
        String safe = (text == null) ? "" : text.trim();
        return "{\"text\":\"" + escapeJson(safe) + "\"}";
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
                    if (c < 0x20)
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
