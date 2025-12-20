package com.sandcrabcombatnotifier;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;

public class SandCrabCombatNotifierOverlay extends Overlay
{
    private final SandCrabCombatNotifierPlugin plugin;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    SandCrabCombatNotifierOverlay(SandCrabCombatNotifierPlugin plugin)
    {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panel.getChildren().clear();

        if (!plugin.isOverlayRelevant())
        {
            return null;
        }

        panel.getChildren().add(LineComponent.builder()
                .left("Idle Crab")
                .build());

        int totalTicks = plugin.getAggroTotalTicks();
        int remainingTicks = plugin.getAggroRemainingTicks();

        if (remainingTicks < 0)
        {
            panel.getChildren().add(LineComponent.builder()
                    .left("Aggro left")
                    .right("Waiting")
                    .build());
            return panel.render(graphics);
        }

        String remaining = formatTicks(Math.max(0, remainingTicks));
        Color remainingColor = gradientColorByRemaining(remainingTicks, totalTicks);

        if (remainingTicks <= 0 && plugin.isExpireFlashing())
        {
            remainingColor = plugin.isFlashOnTick()
                    ? Color.WHITE
                    : new Color(220, 40, 40);
        }

        if (plugin.isOutOfCombatAlarmActive())
        {
            // Make the overlay visually obnoxious when out of combat
            remainingColor = plugin.isFlashOnTick() ? Color.WHITE : new Color(220, 40, 40);

            panel.getChildren().add(LineComponent.builder()
                    .left("OUT OF COMBAT")
                    .leftColor(remainingColor)
                    .build());
        }

        panel.getChildren().add(LineComponent.builder()
                .left("Aggro left")
                .right(remaining)
                .rightColor(remainingColor)
                .build());

        if (plugin.showProgressBar())
        {
            ProgressBarComponent bar = new ProgressBarComponent();
            bar.setMaximum(totalTicks);
            bar.setValue(Math.max(0, remainingTicks));
            bar.setForegroundColor(remainingColor);
            bar.setBackgroundColor(new Color(0, 0, 0, 120));
            panel.getChildren().add(bar);
        }

        return panel.render(graphics);
    }

    private static String formatTicks(int ticks)
    {
        int seconds = (ticks * 6) / 10;
        int mm = seconds / 60;
        int ss = seconds % 60;
        return String.format("%d:%02d", mm, ss);
    }

    private static Color gradientColorByRemaining(int remainingTicks, int totalTicks)
    {
        if (totalTicks <= 0)
        {
            return Color.WHITE;
        }

        double t = clamp01(remainingTicks / (double) totalTicks);

        Color green = new Color(0, 200, 0);
        Color yellow = new Color(220, 180, 0);
        Color red = new Color(220, 40, 40);

        if (t >= 0.5)
        {
            double u = (t - 0.5) / 0.5;
            return lerp(yellow, green, u);
        }

        double u = t / 0.5;
        return lerp(red, yellow, u);
    }

    private static Color lerp(Color a, Color b, double t)
    {
        t = clamp01(t);
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, g, bl);
    }

    private static double clamp01(double v)
    {
        if (v < 0.0)
        {
            return 0.0;
        }
        if (v > 1.0)
        {
            return 1.0;
        }
        return v;
    }
}
