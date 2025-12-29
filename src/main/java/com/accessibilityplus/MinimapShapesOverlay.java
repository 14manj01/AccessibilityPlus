package com.accessibilityplus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Path2D;
import java.util.List;

import javax.inject.Inject;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Colorblind helper: draw high-contrast shapes on the minimap for entities.
 *
 * This does NOT modify the game's minimap colors. It only adds clear shapes
 * on top of the minimap canvas (Plugin Hub safe).
 *
 * Shapes:
 * - You: diamond
 * - Other players: square
 * - NPCs: triangle
 */
public class MinimapShapesOverlay extends Overlay
{
    private final Client client;
    private final AccessibilityPlusConfig config;

    @Inject
    public MinimapShapesOverlay(Client client, AccessibilityPlusConfig config)
    {
        this.client = client;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (!config.enableMinimapShapes())
        {
            return null;
        }

        int size = Math.max(2, config.minimapShapeSize());
        int alpha = clamp(config.minimapShapeOpacity(), 30, 255);

        // Very high contrast: white outline, black fill.
        Color outline = new Color(255, 255, 255, alpha);
        Color fill = new Color(0, 0, 0, alpha);

        g.setStroke(new BasicStroke(2f));

        // Local player
        if (config.showLocalPlayerOnMinimapShapes())
        {
            Player me = client.getLocalPlayer();
            if (me != null)
            {
                drawDiamond(g, me.getLocalLocation(), size + 2, fill, outline);
            }
        }

        // Other players
        if (config.showPlayersOnMinimapShapes())
        {
            List<Player> players = client.getPlayers();
            if (players != null)
            {
                for (Player p : players)
                {
                    if (p == null || p == client.getLocalPlayer())
                    {
                        continue;
                    }
                    drawSquare(g, p.getLocalLocation(), size, fill, outline);
                }
            }
        }

        // NPCs
        if (config.showNpcsOnMinimapShapes())
        {
            List<NPC> npcs = client.getNpcs();
            if (npcs != null)
            {
                for (NPC n : npcs)
                {
                    if (n == null)
                    {
                        continue;
                    }
                    drawTriangle(g, n.getLocalLocation(), size, fill, outline);
                }
            }
        }

        return null;
    }

    private void drawSquare(Graphics2D g, LocalPoint lp, int r, Color fill, Color outline)
    {
        Point p = localToMinimap(lp);
        if (p == null)
        {
            return;
        }

        int x = p.x - r;
        int y = p.y - r;
        int d = r * 2;

        g.setColor(fill);
        g.fillRect(x, y, d, d);
        g.setColor(outline);
        g.drawRect(x, y, d, d);
    }

    private void drawTriangle(Graphics2D g, LocalPoint lp, int r, Color fill, Color outline)
    {
        Point p = localToMinimap(lp);
        if (p == null)
        {
            return;
        }

        Path2D tri = new Path2D.Double();
        tri.moveTo(p.x, p.y - r);
        tri.lineTo(p.x - r, p.y + r);
        tri.lineTo(p.x + r, p.y + r);
        tri.closePath();

        g.setColor(fill);
        g.fill(tri);
        g.setColor(outline);
        g.draw(tri);
    }

    private void drawDiamond(Graphics2D g, LocalPoint lp, int r, Color fill, Color outline)
    {
        Point p = localToMinimap(lp);
        if (p == null)
        {
            return;
        }

        Path2D d = new Path2D.Double();
        d.moveTo(p.x, p.y - r);
        d.lineTo(p.x - r, p.y);
        d.lineTo(p.x, p.y + r);
        d.lineTo(p.x + r, p.y);
        d.closePath();

        g.setColor(fill);
        g.fill(d);
        g.setColor(outline);
        g.draw(d);
    }

    private Point localToMinimap(LocalPoint lp)
    {
        if (lp == null)
        {
            return null;
        }

        net.runelite.api.Point p = Perspective.localToMinimap(client, lp);
        if (p == null)
        {
            return null;
        }

        return new Point(p.getX(), p.getY());
    }

    private static int clamp(int v, int lo, int hi)
    {
        return Math.max(lo, Math.min(hi, v));
    }
}
