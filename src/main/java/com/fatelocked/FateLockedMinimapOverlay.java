package com.fatelocked;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;

/**
 * Tints the player's current 64-tile chunk on the minimap, color-coded by the
 * region's unlock status. Mirrors {@link FateLockedSceneOverlay} but projected
 * onto the minimap instead of the main scene.
 */
public class FateLockedMinimapOverlay extends Overlay
{
    // Local-coordinate radius within which Perspective will project a point.
    // A 64-tile chunk corner can sit ~90 tiles from the player, so 14000 local
    // units (~109 tiles) comfortably covers every corner.
    private static final int PROJECTION_DISTANCE = 14000;
    private static final BasicStroke STROKE = new BasicStroke(1.5f);

    // The minimap draw area widget differs between the fixed and resizable
    // layouts; whichever is currently shown is the non-hidden one. ComponentID
    // is the stable modern API (WidgetInfo was removed).
    private static final int[] MINIMAP_DRAW_AREAS = {
        ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA,
        ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA,
        ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA,
    };

    @Inject private Client client;
    @Inject private FateLockedPlugin plugin;
    @Inject private FateLockedConfig config;

    @Inject
    FateLockedMinimapOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.drawMinimap()) return null;
        Player local = client.getLocalPlayer();
        if (local == null) return null;
        WorldPoint wp = local.getWorldLocation();
        if (wp == null) return null;

        FateLockedBundle bundle = plugin.getBundle();
        CanonicalChunk chunk = CanonicalChunk.of(wp);
        // Sub-area-aware: a Falador chunk reflects Falador's lock state, not
        // all of Asgarnia's.
        Color color;
        switch (bundle.lockStateAt(chunk))
        {
            case UNLOCKED: color = config.unlockedColor(); break;
            case LOCKED: color = config.lockedColor(); break;
            default: color = config.unauthoredColor(); break;
        }

        Polygon poly = chunkMinimapPolygon(chunk);
        if (poly == null) return null;

        // A whole 64-tile chunk projects to a polygon much larger than the
        // minimap circle, so clip to the minimap draw area before filling —
        // otherwise the tint spills across the rest of the UI.
        Shape oldClip = graphics.getClip();
        Shape clip = minimapClip();
        if (clip != null) graphics.clip(clip);

        graphics.setColor(color);
        graphics.fillPolygon(poly);
        graphics.setStroke(STROKE);
        graphics.setColor(new Color(
            Math.min(color.getRed() + 40, 255),
            Math.min(color.getGreen() + 40, 255),
            Math.min(color.getBlue() + 40, 255),
            230));
        graphics.drawPolygon(poly);

        graphics.setClip(oldClip);
        return null;
    }

    /** Elliptical clip matching the currently-visible minimap draw area, or null. */
    private Shape minimapClip()
    {
        for (int componentId : MINIMAP_DRAW_AREAS)
        {
            Widget w = client.getWidget(componentId);
            if (w == null || w.isHidden()) continue;
            Rectangle b = w.getBounds();
            if (b != null && b.width > 0 && b.height > 0)
            {
                return new Ellipse2D.Double(b.x, b.y, b.width, b.height);
            }
        }
        return null;
    }

    /**
     * Project a chunk's four corner tiles onto the minimap. Corner local points
     * are built directly from the scene base so they project even when the
     * corner tile lies outside the loaded scene.
     */
    private Polygon chunkMinimapPolygon(CanonicalChunk chunk)
    {
        int baseX = client.getBaseX();
        int baseY = client.getBaseY();

        int x0 = chunk.getCx() << 6;
        int y0 = chunk.getCy() << 6;
        int[][] corners = {
            { x0,        y0        },
            { x0 + 64,   y0        },
            { x0 + 64,   y0 + 64   },
            { x0,        y0 + 64   },
        };

        Polygon poly = new Polygon();
        for (int[] c : corners)
        {
            LocalPoint lp = new LocalPoint((c[0] - baseX) * 128, (c[1] - baseY) * 128);
            Point mm = Perspective.localToMinimap(client, lp, PROJECTION_DISTANCE);
            if (mm == null) return null; // corner out of projection range
            poly.addPoint(mm.getX(), mm.getY());
        }
        return poly;
    }
}
