package com.accessibilityplus.tts;

import java.io.File;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Runs Piper (CLI) to synthesize text to a WAV file.
 */
public final class PiperRunner
{
    private PiperRunner()
    {
    }

    public static File synthesizeToTempWav(File piperExe, File model, String text) throws Exception
    {
        String tmpDir = System.getProperty("java.io.tmpdir");
        File out = new File(tmpDir, "ap_piper_" + UUID.randomUUID() + ".wav");

        List<String> cmd = new ArrayList<>();
        cmd.add(piperExe.getAbsolutePath());
        cmd.add("--model");
        cmd.add(model.getAbsolutePath());
        cmd.add("--output_file");
        cmd.add(out.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process p = pb.start();

        try (OutputStreamWriter w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8))
        {
            w.write(text);
            w.write("\n");
            w.flush();
        }

        boolean done = p.waitFor(30, TimeUnit.SECONDS);
        if (!done)
        {
            p.destroyForcibly();
            throw new IllegalStateException("Piper timed out");
        }

        if (!out.isFile() || out.length() <= 44)
        {
            throw new IllegalStateException("Piper did not produce audio");
        }

        return out;
    }
}
