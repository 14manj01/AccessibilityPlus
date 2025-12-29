package com.accessibilityplus.tts;

/**
 * Minimal JSON helper for small payloads like {"text":"..."}.
 * Avoids adding new dependencies.
 */
public final class SimpleJson
{
    private SimpleJson()
    {
    }

    public static String extractString(String json, String key)
    {
        if (json == null || key == null)
        {
            return null;
        }

        // Very small and forgiving parser:
        // finds "key" : "value"
        String needle = "\""+ key +"\"";
        int k = json.indexOf(needle);
        if (k < 0)
        {
            return null;
        }
        int colon = json.indexOf(':', k + needle.length());
        if (colon < 0)
        {
            return null;
        }
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0)
        {
            return null;
        }
        int i = firstQuote + 1;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        while (i < json.length())
        {
            char c = json.charAt(i);
            if (esc)
            {
                switch (c)
                {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    default: sb.append(c); break;
                }
                esc = false;
            }
            else
            {
                if (c == '\\')
                {
                    esc = true;
                }
                else if (c == '"')
                {
                    return sb.toString();
                }
                else
                {
                    sb.append(c);
                }
            }
            i++;
        }
        return sb.toString();
    }
}
