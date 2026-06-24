package com.fatelocked;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * Full-viewport red border flash when the player crosses into locked
 * territory — the visual counterpart to the warning sound. The plugin sets
 * {@code flashUntil}; this overlay pulses a fading frame until it expires.
 */
public class FateLockedFlashOverlay extends Overlay
{
    private static final int FRAME_THICKNESS = 14;

    private final Client client;
    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;

    @Inject
    FateLockedFlashOverlay(Client client, FateLockedPlugin plugin, FateLockedConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (!config.flashOnLocked())
        {
            return null;
        }
        long until = plugin.getLockedFlashUntil();
        long now = System.currentTimeMillis();
        if (now >= until)
        {
            return null;
        }

        // Fade out over the flash window, with a 4 Hz pulse on top.
        float remaining = (until - now) / (float) FateLockedPlugin.LOCKED_FLASH_MS;
        float pulse = 0.6f + 0.4f * (float) Math.abs(Math.sin(now / 125.0));
        int alpha = Math.min(255, Math.max(0, (int) (200 * remaining * pulse)));

        int w = client.getCanvasWidth();
        int h = client.getCanvasHeight();
        g.setColor(new Color(239, 68, 68, alpha));
        g.setStroke(new BasicStroke(FRAME_THICKNESS));
        g.drawRect(FRAME_THICKNESS / 2, FRAME_THICKNESS / 2, w - FRAME_THICKNESS, h - FRAME_THICKNESS);
        return null;
    }
}
