package com.accessibilityplus.tts;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TtsController
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private volatile String bridgeBaseUrl = "http://127.0.0.1:59125";
    private volatile long cooldownMs = 700;

    private volatile long lastSpokenAt = 0L;
    private volatile String lastSpokenKey = "";

    private final OkHttpClient http;

    // Lazy so it can be recreated after shutdown/reload
    private volatile ExecutorService worker;

    // Bridge status
    private volatile boolean bridgeUp = false;
    private volatile long lastHealthCheckAt = 0L;
    private volatile String lastBridgeError = "";

        @Inject
    public TtsController(OkHttpClient http)
    {
        this.http = http;
    }

public void setBridgeBaseUrl(String url)
    {
        if (url == null || url.trim().isEmpty())
        {
            return;
        }
        bridgeBaseUrl = url.trim();
    }

    public void setCooldownMs(long ms)
    {
        cooldownMs = Math.max(0, ms);
    }

    public boolean isBridgeUp()
    {
        return bridgeUp;
    }

    public String getLastBridgeError()
    {
        return lastBridgeError;
    }

    public void checkBridgeNow()
    {
        bridgeUp = pingHealth();
        lastHealthCheckAt = System.currentTimeMillis();
    }

    public void speakTest()
    {
        ensureWorker();
        worker.submit(() -> postToBridge("Accessibility Plus text to speech test."));
    }

    public void updateFromDialog(String speaker, String dialogText, List<String> options)
    {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheckAt > 3000)
        {
            bridgeUp = pingHealth();
            lastHealthCheckAt = now;
        }

        if (!bridgeUp)
        {
            return;
        }

        final String sp = safeTrim(speaker);
        final String dt = safeTrim(dialogText);
        final List<String> opts = options == null ? new ArrayList<>() : options;

        final String speakText;
        final String key;

        if (!opts.isEmpty())
        {
            StringBuilder sb = new StringBuilder();
            sb.append("Options. ");
            int n = Math.min(9, opts.size());
            for (int i = 0; i < n; i++)
            {
                String o = safeTrim(opts.get(i));
                if (o.isEmpty())
                {
                    continue;
                }
                sb.append(i + 1).append(". ").append(o).append(". ");
            }
            speakText = sb.toString().trim();
            key = "OPT|" + normalizeForKey(speakText);
        }
        else if (!dt.isEmpty())
        {
            speakText = !sp.isEmpty() ? (sp + ". " + dt) : dt;
            key = "DIA|" + normalizeForKey(speakText);
        }
        else
        {
            return;
        }

        if (!shouldSpeakNow(key))
        {
            return;
        }

        ensureWorker();

        try
        {
            worker.submit(() -> postToBridge(speakText));
        }
        catch (RuntimeException ex)
        {
            worker = null;
            ensureWorker();
            try
            {
                worker.submit(() -> postToBridge(speakText));
            }
            catch (RuntimeException ignored)
            {
            }
        }
    }

    public void shutdown()
    {
        ExecutorService w = worker;
        worker = null;

        if (w != null)
        {
            try
            {
                w.shutdownNow();
            }
            catch (Exception ignored)
            {
            }
        }
    }

    private void ensureWorker()
    {
        if (worker == null || worker.isShutdown() || worker.isTerminated())
        {
            worker = Executors.newSingleThreadExecutor(r ->
            {
                Thread t = new Thread(r, "accessibility-plus-tts");
                t.setDaemon(true);
                return t;
            });
        }
    }

    private boolean shouldSpeakNow(String key)
    {
        long now = System.currentTimeMillis();

        if (cooldownMs > 0 && (now - lastSpokenAt) < cooldownMs)
        {
            if (Objects.equals(lastSpokenKey, key))
            {
                return false;
            }
        }

        if (Objects.equals(lastSpokenKey, key))
        {
            return false;
        }

        lastSpokenKey = key;
        lastSpokenAt = now;
        return true;
    }

    private boolean pingHealth()
    {
        String url = bridgeBaseUrl + "/health";
        Request request = new Request.Builder().url(url).get().build();

        try (Response resp = http.newCall(request).execute())
        {
            if (resp.isSuccessful())
            {
                lastBridgeError = "";
                return true;
            }
            lastBridgeError = "Health check failed: HTTP " + resp.code();
            return false;
        }
        catch (IOException e)
        {
            lastBridgeError = "Bridge not reachable: " + e.getClass().getSimpleName();
            return false;
        }
    }

    private void postToBridge(String text)
    {
        String url = bridgeBaseUrl + "/speak";
        String bodyJson = "{\"text\":\"" + escapeJson(text) + "\"}";
        RequestBody body = RequestBody.create(JSON, bodyJson);

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        try (Response resp = http.newCall(request).execute())
        {
            // ignore body
        }
        catch (Exception ignored)
        {
        }
    }

    private static String safeTrim(String s)
    {
        return s == null ? "" : s.trim();
    }

    private static String normalizeForKey(String s)
    {
        if (s == null)
        {
            return "";
        }
        return s.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static String escapeJson(String s)
    {
        if (s == null)
        {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '\"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20)
                    {
                        out.append(String.format("\\u%04x", (int) c));
                    }
                    else
                    {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }
}