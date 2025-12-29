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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Embedded local HTTP bridge (similar to Natural Speech):
 * - GET  /health
 * - POST /speak  {"text":"..."}
 *
 * This server is intended to be started from inside the RuneLite plugin JVM.
 * It runs Piper as a subprocess and plays generated WAV audio via Java Sound.
 */
public final class EmbeddedBridgeServer
{
    private final AtomicBoolean running = new AtomicBoolean(false);

    private HttpServer server;
    private ExecutorService httpExecutor;
    private ExecutorService speechExecutor;
    private final AtomicLong speechGeneration = new AtomicLong(0L);

    private volatile String piperPath = "";
    private volatile String modelPath = "";

    public boolean isRunning()
    {
        return running.get();
    }

    public int getPort()
    {
        HttpServer s = server;
        if (s == null)
        {
            return -1;
        }
        return s.getAddress().getPort();
    }

    public synchronized void start(int port, String piperPath, String modelPath) throws IOException
    {
        this.piperPath = safe(piperPath);
        this.modelPath = safe(modelPath);

        if (running.get())
        {
            return;
        }

        InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port);
        server = HttpServer.create(addr, 0);

        httpExecutor = Executors.newFixedThreadPool(2, r ->
        {
            Thread t = new Thread(r, "accessibility-plus-bridge-http");
            t.setDaemon(true);
            return t;
        });

        speechExecutor = Executors.newSingleThreadExecutor(r ->
        {
            Thread t = new Thread(r, "accessibility-plus-bridge-speech");
            t.setDaemon(true);
            return t;
        });

        server.createContext("/health", new HealthHandler());
        server.createContext("/speak", new SpeakHandler());

        server.setExecutor(httpExecutor);
        server.start();
        running.set(true);
    }

    public synchronized void stop()
    {
        running.set(false);

        if (server != null)
        {
            try
            {
                server.stop(0);
            }
            catch (Exception ignored)
            {
            }
            server = null;
        }

        if (httpExecutor != null)
        {
            try
            {
                httpExecutor.shutdownNow();
            }
            catch (Exception ignored)
            {
            }
            httpExecutor = null;
        }

        if (speechExecutor != null)
        {
            try
            {
                speechExecutor.shutdownNow();
            }
            catch (Exception ignored)
            {
            }
            speechExecutor = null;
        }
    }

    /**
     * Stop any current/queued speech without shutting down the HTTP server.
     * Used when the dialog UI closes so speech does not continue after leaving chat.
     */
    public synchronized void stopSpeechNow()
    {
        // Bump generation so in-flight tasks can self-cancel before playback.
        speechGeneration.incrementAndGet();

        // Stop any currently playing audio immediately.
        WavPlayer.stop();

        // Drop any queued speech tasks by recreating the executor.
        ExecutorService old = speechExecutor;
        if (old != null)
        {
            try
            {
                old.shutdownNow();
            }
            catch (Exception ignored)
            {
            }
        }

        speechExecutor = Executors.newSingleThreadExecutor(r ->
        {
            Thread t = new Thread(r, "accessibility-plus-bridge-speech");
            t.setDaemon(true);
            return t;
        });
    }

    private final class HealthHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange ex) throws IOException
        {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod()))
            {
                writeJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
                return;
            }

            boolean ready = new File(piperPath).isFile() && new File(modelPath).isFile();
            String body = "{\"ok\":true,\"running\":" + (running.get() ? "true" : "false") +
                    ",\"ready\":" + (ready ? "true" : "false") + "}";
            writeJson(ex, 200, body);
        }
    }

    private final class SpeakHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange ex) throws IOException
        {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod()))
            {
                writeJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
                return;
            }

            if (!running.get())
            {
                writeJson(ex, 503, "{\"ok\":false,\"error\":\"not_running\"}");
                return;
            }

            final String body = readAll(ex.getRequestBody());
            final String text = SimpleJson.extractString(body, "text");

            if (text == null || text.trim().isEmpty())
            {
                writeJson(ex, 400, "{\"ok\":false,\"error\":\"missing_text\"}");
                return;
            }

            final File piperExe = new File(piperPath);
            final File model = new File(modelPath);

            if (!piperExe.isFile() || !model.isFile())
            {
                writeJson(ex, 400, "{\"ok\":false,\"error\":\"piper_or_model_missing\"}");
                return;
            }

            ExecutorService speech = speechExecutor;
            if (speech == null)
            {
                writeJson(ex, 503, "{\"ok\":false,\"error\":\"speech_executor_down\"}");
                return;
            }

            final long gen = speechGeneration.get();

            speech.submit(() ->
            {
                try
                {
                    if (gen != speechGeneration.get())
                    {
                        return;
                    }

                    File wav = PiperRunner.synthesizeToTempWav(piperExe, model, text);
                    if (gen != speechGeneration.get())
                    {
                        try
                        {
                            wav.delete();
                        }
                        catch (Exception ignored)
                        {
                        }
                        return;
                    }
                    WavPlayer.playBlocking(wav);
                    try
                    {
                        // best-effort cleanup
                        wav.delete();
                    }
                    catch (Exception ignored)
                    {
                    }
                }
                catch (Exception ignored)
                {
                }
            });

            writeJson(ex, 200, "{\"ok\":true}");
        }
    }

    private static void writeJson(HttpExchange ex, int code, String body) throws IOException
    {
        Headers headers = ex.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody())
        {
            os.write(bytes);
        }
    }

    private static String readAll(InputStream in) throws IOException
    {
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
