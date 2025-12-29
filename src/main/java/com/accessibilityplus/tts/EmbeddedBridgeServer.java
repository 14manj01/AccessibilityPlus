package com.accessibilityplus.tts;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class EmbeddedBridgeServer
{
    private final WavPlayer wavPlayer;

    private volatile HttpServer server;
    private volatile ExecutorService executor;

    private final AtomicBoolean running = new AtomicBoolean(false);

    // Piper config (set on start)
    private volatile String piperExePath = "";
    private volatile String voiceModelPath = "";

    // Used to kill pending / queued speech when leaving chat
    private volatile long speechGeneration = 0;

    @Inject
    public EmbeddedBridgeServer(WavPlayer wavPlayer)
    {
        this.wavPlayer = wavPlayer;
    }

    public boolean isRunning()
    {
        return running.get();
    }

    public void start(int port, String piperPath, String modelPath) throws IOException
    {
        if (isRunning())
        {
            stop();
        }

        piperExePath = safe(piperPath);
        voiceModelPath = safe(modelPath);

        InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port);
        HttpServer s = HttpServer.create(addr, 0);

        s.createContext("/health", new HealthHandler());
        s.createContext("/speak", new SpeakHandler());

        ExecutorService ex = Executors.newCachedThreadPool(r ->
        {
            Thread t = new Thread(r, "accessibility-plus-bridge");
            t.setDaemon(true);
            return t;
        });

        s.setExecutor(ex);
        s.start();

        server = s;
        executor = ex;
        running.set(true);

        log.info("Embedded bridge started on 127.0.0.1:{}", port);
    }

    public void stop()
    {
        running.set(false);

        try
        {
            if (server != null)
            {
                server.stop(0);
            }
        }
        catch (Exception ignored)
        {
        }

        try
        {
            if (executor != null)
            {
                executor.shutdownNow();
            }
        }
        catch (Exception ignored)
        {
        }

        server = null;
        executor = null;

        // Invalidate any pending speech
        stopSpeechNow();

        log.info("Embedded bridge stopped");
    }

    /**
     * Best-effort cancel: prevents new WAV playback for stale requests.
     * AudioPlayer does not expose an allowed hard-stop, so we invalidate work.
     */
    public void stopSpeechNow()
    {
        speechGeneration++;
        try
        {
            wavPlayer.stopNow();
        }
        catch (Exception ignored)
        {
        }
    }

    private final class HealthHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange ex) throws IOException
        {
            byte[] out = "ok".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody())
            {
                os.write(out);
            }
        }
    }

    private final class SpeakHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange ex) throws IOException
        {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod()))
            {
                ex.sendResponseHeaders(405, -1);
                return;
            }

            long myGen = speechGeneration;

            String body = readAll(ex.getRequestBody());
            String text = extractTextFromJson(body);

            if (text.isEmpty())
            {
                ex.sendResponseHeaders(400, -1);
                return;
            }

            // Immediately ACK so the client doesn't block on piper runtime
            ex.sendResponseHeaders(204, -1);
            ex.close();

            // Do work async
            ExecutorService exr = executor;
            if (exr == null)
            {
                return;
            }

            exr.submit(() ->
            {
                if (!running.get())
                {
                    return;
                }

                // Cancelled before we started
                if (myGen != speechGeneration)
                {
                    return;
                }

                File wav = null;
                try
                {
                    wav = PiperRunner.runToWav(piperExePath, voiceModelPath, text);

                    // Cancelled while piper was running
                    if (myGen != speechGeneration)
                    {
                        return;
                    }

                    // Play only if still current
                    wavPlayer.playIfCurrent(wav, wavPlayer.currentGeneration());
                }
                catch (Exception e)
                {
                    log.debug("Speak failed: {}", e.toString());
                }
                finally
                {
                    if (wav != null)
                    {
                        try
                        {
                            // best-effort cleanup
                            if (wav.exists())
                            {
                                //noinspection ResultOfMethodCallIgnored
                                wav.delete();
                            }
                        }
                        catch (Exception ignored)
                        {
                        }
                    }
                }
            });
        }
    }

    private static String extractTextFromJson(String body)
    {
        // Minimal JSON extraction: {"text":"..."}
        // We avoid adding JSON libs for Plugin Hub.
        if (body == null)
        {
            return "";
        }

        String b = body.trim();
        int idx = b.indexOf("\"text\"");
        if (idx < 0)
        {
            return "";
        }

        int colon = b.indexOf(':', idx);
        if (colon < 0)
        {
            return "";
        }

        int firstQuote = b.indexOf('"', colon + 1);
        if (firstQuote < 0)
        {
            return "";
        }

        int secondQuote = findStringEndQuote(b, firstQuote + 1);
        if (secondQuote < 0)
        {
            return "";
        }

        String raw = b.substring(firstQuote + 1, secondQuote);
        return unescapeJsonString(raw).trim();
    }

    private static int findStringEndQuote(String s, int start)
    {
        boolean escaped = false;
        for (int i = start; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (escaped)
            {
                escaped = false;
                continue;
            }
            if (c == '\\')
            {
                escaped = true;
                continue;
            }
            if (c == '"')
            {
                return i;
            }
        }
        return -1;
    }

    private static String unescapeJsonString(String s)
    {
        if (s == null || s.isEmpty())
        {
            return "";
        }

        StringBuilder out = new StringBuilder(s.length());
        boolean esc = false;

        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (!esc)
            {
                if (c == '\\')
                {
                    esc = true;
                }
                else
                {
                    out.append(c);
                }
                continue;
            }

            esc = false;
            switch (c)
            {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                default:
                    out.append(c);
            }
        }

        return out.toString();
    }

    private static String readAll(InputStream in) throws IOException
    {
        if (in == null)
        {
            return "";
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) >= 0)
        {
            baos.write(buf, 0, r);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private static String safe(String s)
    {
        return s == null ? "" : s.trim();
    }
}
