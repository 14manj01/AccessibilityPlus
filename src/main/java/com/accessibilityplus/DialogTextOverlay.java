package com.accessibilityplus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Accessibility-first dialog overlay.
 * Renders a single, authoritative dialog surface:
 *  - Speaker
 *  - Current line (no history)
 *  - Response options (as large blocks)
 *
 * Optionally draws an opaque backdrop over the native dialog widgets.
 */
public class DialogTextOverlay extends Overlay
{
    private final AccessibilityPlusPlugin plugin;
    private final AccessibilityPlusConfig config;

    @Inject
    public DialogTextOverlay(AccessibilityPlusPlugin plugin, AccessibilityPlusConfig config)
    {
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (!config.enableDialogOverlay())
        {
            return null;
        }

        final String speaker = plugin.getSpeakerName();
        final String line = plugin.getDialogText();
        final List<String> options = plugin.getDialogOptions();
        final Rectangle anchor = plugin.getDialogBounds();

        final boolean hasLine = line != null && !line.isBlank();
        final boolean hasOptions = options != null && !options.isEmpty();

        if (!hasLine && !hasOptions)
        {
            return null;
        }

        Rectangle bounds = anchor != null ? new Rectangle(anchor) : g.getClipBounds();
        if (bounds == null)
        {
            return null;
        }

        // Expand a bit so we cover the full native dialog area
        bounds.grow(8, 8);

        int width = Math.min(config.dialogPanelWidth(), bounds.width);
        int pad = 16;
        int opacity = clamp(config.dialogOverlayOpacity(), 40, 255);

        int x = bounds.x + (bounds.width - width) / 2;
        int yTop = bounds.y;
        int bottomAnchor = bounds.y + bounds.height;

        Palette pal = paletteFor(config.dialogTheme(), x, yTop, bounds.height, opacity);

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Font base = g.getFont();
        Font dialogFont = base.deriveFont((float) config.dialogFontSize());
        Font optFont = base.deriveFont((float) Math.max(12, config.dialogFontSize() - 2));

        // Build wrapped lines for current dialog only
        List<String> dialogLines = new ArrayList<>();
        g.setFont(dialogFont);
        FontMetrics dfm = g.getFontMetrics();
        int innerW = Math.max(160, width - pad * 2);

        if (speaker != null && !speaker.isBlank())
        {
            dialogLines.add(speaker.trim());
        }

        if (hasLine)
        {
            dialogLines.addAll(TextWrapUtil.wrap(dfm, line.trim(), innerW));
        }

        // Wrap options as rows
        List<List<String>> optionWrapped = new ArrayList<>();
        if (hasOptions)
        {
            g.setFont(optFont);
            FontMetrics ofm = g.getFontMetrics();

            int idx = 1;
            for (String opt : options)
            {
                String label = idx + ". " + opt;
                optionWrapped.add(TextWrapUtil.wrap(ofm, label, innerW));
                idx++;
            }
        }

        // Measure height
        int dialogLineH = dfm.getHeight();
        g.setFont(optFont);
        FontMetrics ofm2 = g.getFontMetrics();
        int optLineH = ofm2.getHeight();

        // Row style (Option A: separators)
        int rowPadY = 10;          // vertical padding inside each row
        int rowPadX = 12;          // horizontal padding inside each row
        int wrapGap = 2;           // extra gap between wrapped lines inside a row (OSRS feel)
        int rowGapTop = 8;         // gap between dialog text and first option row

        int contentH = pad * 2;

        // Dialog area
        contentH += dialogLines.size() * dialogLineH;

        // Options rows
        if (!optionWrapped.isEmpty())
        {
            contentH += rowGapTop;

            for (List<String> rowLines : optionWrapped)
            {
                int linesCount = Math.max(1, rowLines.size());
                int rowTextH = (linesCount * optLineH) + ((linesCount - 1) * wrapGap);
                int rowH = (rowPadY * 2) + rowTextH;

                contentH += rowH;

                // Divider line between rows (1px)
                contentH += 1;
            }

            // Remove divider after last row
            contentH -= 1;
        }

        // Allow the overlay to grow beyond the native dialog widget height.
        Rectangle clip = g.getClipBounds();
        int maxH = bounds.height;
        if (clip != null)
        {
            maxH = Math.max(0, clip.height - 8);
        }
        int height = Math.min(contentH, maxH);

        // Anchor to bottom and grow upward
        int y = bottomAnchor - height;
        if (clip != null)
        {
            int minY = clip.y + 4;
            int maxY = (clip.y + clip.height) - height - 4;
            if (y < minY)
            {
                y = minY;
            }
            if (y > maxY)
            {
                y = maxY;
            }
        }

        // Optional: hide the native dialog underneath
        if (config.hideNativeDialog())
        {
            g.setPaint(pal.backdropPaint);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        // Main panel
        RoundRectangle2D panel = new RoundRectangle2D.Double(x, y, width, height, 12, 12);
        g.setPaint(pal.panelPaint);
        g.fill(panel);

        // subtle double-frame for a more native bevel feel
        g.setColor(pal.border);
        g.draw(panel);

        RoundRectangle2D inner = new RoundRectangle2D.Double(x + 1, y + 1, width - 2, height - 2, 12, 12);
        g.setColor(pal.innerBorder);
        g.draw(inner);

        int cx = x + pad;
        int cy = y + pad;

        // Draw dialog lines
        g.setFont(dialogFont);

        for (int i = 0; i < dialogLines.size(); i++)
        {
            String l = dialogLines.get(i);

            Color textColor;
            if (i == 0 && speaker != null && !speaker.isBlank())
            {
                textColor = pal.speakerText;
            }
            else
            {
                textColor = pal.dialogText;
            }

            cy += dialogLineH;
            if (cy > y + height - pad)
            {
                break;
            }

            if (pal.textShadow != null)
            {
                g.setColor(pal.textShadow);
                g.drawString(l, cx + 1, cy + 1);
            }

            g.setColor(textColor);
            g.drawString(l, cx, cy);
        }

        // Draw options as rows with dividers
        if (!optionWrapped.isEmpty())
        {
            cy += rowGapTop;

            g.setFont(optFont);

            int rowsX = x + pad;
            int rowsW = width - pad * 2;

            for (int i = 0; i < optionWrapped.size(); i++)
            {
                List<String> rowLines = optionWrapped.get(i);
                int linesCount = Math.max(1, rowLines.size());
                int rowTextH = (linesCount * optLineH) + ((linesCount - 1) * wrapGap);
                int rowH = (rowPadY * 2) + rowTextH;

                // Do not draw if it would exceed the visible panel space
                if (cy + rowH > y + height - pad)
                {
                    break;
                }

                // Row background (flat, inset feel)
                g.setColor(pal.rowFill);
                g.fillRect(rowsX, cy, rowsW, rowH);

                // Row outline (very subtle, optional but helps)
                g.setColor(pal.rowOutline);
                g.drawRect(rowsX, cy, rowsW, rowH);

                int tx = rowsX + rowPadX;
                int ty = cy + rowPadY;

                // Option text
                g.setColor(pal.optionText);

                for (int li = 0; li < rowLines.size(); li++)
                {
                    ty += optLineH;
                    if (li > 0)
                    {
                        ty += wrapGap;
                    }

                    String l = rowLines.get(li);

                    if (pal.textShadow != null)
                    {
                        g.setColor(pal.textShadow);
                        g.drawString(l, tx + 1, ty + 1);
                        g.setColor(pal.optionText);
                    }

                    g.drawString(l, tx, ty);
                }

                cy += rowH;

                // Divider under row except last
                if (i < optionWrapped.size() - 1)
                {
                    g.setColor(pal.rowDivider);
                    g.drawLine(rowsX, cy, rowsX + rowsW, cy);
                    cy += 1;
                }
            }
        }

        g.setFont(base);
        return null;
    }

    private static final class Palette
    {
        final Paint panelPaint;
        final Paint backdropPaint;

        final Color border;
        final Color innerBorder;

        final Color dialogText;
        final Color speakerText;

        final Color optionText;

        // Row style paints
        final Color rowFill;
        final Color rowOutline;
        final Color rowDivider;

        final Color textShadow; // may be null

        private Palette(
                Paint panelPaint,
                Paint backdropPaint,
                Color border,
                Color innerBorder,
                Color dialogText,
                Color speakerText,
                Color optionText,
                Color rowFill,
                Color rowOutline,
                Color rowDivider,
                Color textShadow)
        {
            this.panelPaint = panelPaint;
            this.backdropPaint = backdropPaint;
            this.border = border;
            this.innerBorder = innerBorder;
            this.dialogText = dialogText;
            this.speakerText = speakerText;
            this.optionText = optionText;
            this.rowFill = rowFill;
            this.rowOutline = rowOutline;
            this.rowDivider = rowDivider;
            this.textShadow = textShadow;
        }
    }

    private static Palette paletteFor(AccessibilityPlusConfig.DialogTheme theme, int x, int y, int h, int opacity)
    {
        int a = clamp(opacity, 40, 255);
        int hh = Math.max(1, h);

        if (theme == AccessibilityPlusConfig.DialogTheme.PARCHMENT)
        {
            // Warmer/darker parchment closer to native chatbox
            Color top = new Color(214, 203, 168, a);
            Color bottom = new Color(198, 185, 150, a);
            GradientPaint gp = new GradientPaint(x, y, top, x, y + hh, bottom);

            // Borders/text: full alpha only
            Color border = new Color(112, 98, 68);
            Color innerBorder = new Color(235, 226, 198);

            Color dialogText = new Color(40, 34, 24);
            Color speaker = new Color(255, 184, 0);
            Color optionText = new Color(40, 34, 24);

            // Rows: slightly darker inset tone
            Color rowFill = new Color(205, 193, 160, a);
            Color rowOutline = new Color(140, 126, 92);   // subtle
            Color rowDivider = new Color(170, 158, 126);  // subtle

            Color shadow = new Color(120, 110, 90);

            return new Palette(gp, gp, border, innerBorder, dialogText, speaker, optionText, rowFill, rowOutline, rowDivider, shadow);
        }

        // BLACK_PANEL (default)
        Color bg = new Color(15, 15, 15, a);

        Color border = new Color(235, 235, 235);
        Color innerBorder = new Color(60, 60, 60);

        Color dialogText = new Color(235, 235, 235);
        Color speaker = new Color(255, 215, 0);
        Color optionText = new Color(235, 235, 235);

        Color rowFill = new Color(30, 30, 30, a);
        Color rowOutline = new Color(80, 80, 80);
        Color rowDivider = new Color(70, 70, 70);

        Color shadow = new Color(0, 0, 0);

        return new Palette(bg, bg, border, innerBorder, dialogText, speaker, optionText, rowFill, rowOutline, rowDivider, shadow);
    }

    private static int clamp(int v, int lo, int hi)
    {
        return Math.max(lo, Math.min(hi, v));
    }
}
