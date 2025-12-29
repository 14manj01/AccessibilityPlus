package com.accessibilityplus;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

/**
 * Small utility to wrap text into lines based on pixel width.
 */
public final class TextWrapUtil
{
    private TextWrapUtil()
    {
    }

    public static List<String> wrap(FontMetrics fm, String text, int maxWidthPx)
    {
        final List<String> lines = new ArrayList<>();
        if (text == null)
        {
            return lines;
        }

        String cleaned = text
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')
            .trim();

        if (cleaned.isEmpty())
        {
            return lines;
        }

        String[] words = cleaned.split("\\s+");
        StringBuilder line = new StringBuilder();

        for (String w : words)
        {
            if (line.length() == 0)
            {
                line.append(w);
                continue;
            }

            String candidate = line + " " + w;
            if (fm.stringWidth(candidate) <= maxWidthPx)
            {
                line.append(" ").append(w);
            }
            else
            {
                lines.add(line.toString());
                line.setLength(0);

                // if a single word is too long, hard-break it
                if (fm.stringWidth(w) > maxWidthPx)
                {
                    lines.addAll(hardBreakWord(fm, w, maxWidthPx));
                }
                else
                {
                    line.append(w);
                }
            }
        }

        if (line.length() > 0)
        {
            lines.add(line.toString());
        }

        return lines;
    }

    private static List<String> hardBreakWord(FontMetrics fm, String w, int maxWidthPx)
    {
        List<String> out = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < w.length(); i++)
        {
            part.append(w.charAt(i));
            if (fm.stringWidth(part.toString()) > maxWidthPx)
            {
                // move last char to next line
                char last = part.charAt(part.length() - 1);
                part.deleteCharAt(part.length() - 1);
                if (part.length() > 0)
                {
                    out.add(part.toString());
                }
                part.setLength(0);
                part.append(last);
            }
        }
        if (part.length() > 0)
        {
            out.add(part.toString());
        }
        return out;
    }
}
