package com.accessibilityplus.tts;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PiperRunner
{
    private PiperRunner()
    {
    }

    /**
     * Runs Piper CLI and returns a WAV file containing synthesized speech.
     */
    public static File runToWav(String piperExePath, String modelPath, String text)
            throws IOException, InterruptedException
    {
        if (isBlank(piperExePath) || isBlank(modelPath) || isBlank(text))
        {
            throw new IllegalArgumentException("Invalid piper arguments");
        }

        File wavOut = File.createTempFile("accessibility-plus-", ".wav");
        wavOut.deleteOnExit();

        ProcessBuilder pb = new ProcessBuilder(
                piperExePath,
                "--model", modelPath,
                "--output_file", wavOut.getAbsolutePath()
        );

        pb.redirectErrorStream(true);

        Process p = pb.start();

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)))
        {
            writer.write(text);
            writer.newLine();
        }

        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        if (!finished)
        {
            p.destroyForcibly();
            throw new IOException("Piper timed out");
        }

        if (p.exitValue() != 0)
        {
            throw new IOException("Piper exited with code " + p.exitValue());
        }

        if (!wavOut.isFile() || wavOut.length() == 0)
        {
            throw new IOException("Piper produced no audio output");
        }

        return wavOut;
    }

    private static boolean isBlank(String s)
    {
        return s == null || s.trim().isEmpty();
    }
}
