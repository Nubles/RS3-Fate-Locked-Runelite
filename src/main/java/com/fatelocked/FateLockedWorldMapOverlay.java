package com.fatelocked;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.RenderOverview;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.Map;
import java.util.Set;

/**
 * Draws tinted rectangles on the world map widget for every authored chunk:
 * green for chunks in an unlocked region, red for locked, grey for unauthored
 * (only when the camera is zoomed close enough that an unauthored overlay isn't
 * visually overwhelming).
 *
 * The render math mirrors RuneLite's built-in WorldMapOverlay — we translate
 * world tile coords to world-map viewport pixels via RenderOverview's zoom
 * level and the centre of the currently-displayed map tile.
 */
public class FateLockedWorldMapOverlay extends Overlay
{
    private static final BasicStroke BORDER = new BasicStroke(1f);

    @Inject private Client client;
    @Inject private FateLockedPlugin plugin;
    @Inject private FateLockedConfig config;
    @Inject private TooltipManager tooltipManager;

    @Inject
    FateLockedWorldMapOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.MANUAL);
        drawAfterInterface(InterfaceID.WORLD_MAP);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.drawWorldMap()) return null;
        FateLockedBundle bundle = plugin.getBundle();
        if (bundle.getRegionChunks().isEmpty()) return null;

        Widget worldMap = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);
        if (worldMap == null) return null;
        RenderOverview ro = client.getRenderOverview();
        if (ro == null) return null;

        Rectangle bounds = worldMap.getBounds();
        if (bounds == null) return null;

        Shape prevClip = graphics.getClip();
        graphics.setClip(bounds);
        graphics.setStroke(BORDER);

        // Clipping region on the world map
        Area clip = new Area(bounds);

        for (Map.Entry<String, Set<CanonicalChunk>> entry : bundle.getRegionChunks().entrySet())
        {
            for (CanonicalChunk chunk : entry.getValue())
            {
                // Sub-area-aware per-chunk colouring (Falador vs the rest of
                // Asgarnia), matching the web app's map exactly.
                Color fill = bundle.lockStateAt(chunk) == FateLockedBundle.LockState.UNLOCKED
                    ? config.unlockedColor()
                    : bundle.isFrontierChunk(chunk) ? config.frontierColor() : config.lockedColor();
                Rectangle2D rect = worldMapRectForChunk(chunk, bounds, ro);
                if (rect == null) continue;
                if (!clip.intersects(rect)) continue;

                graphics.setColor(fill);
                graphics.fill(rect);
                graphics.setColor(fill.darker());
                graphics.draw(rect);
            }
        }

        if (config.worldMapTooltip())
        {
            addHoverTooltip(bundle, bounds, ro);
        }

        graphics.setClip(prevClip);
        return null;
    }

    /** Show the area name + lock status for the authored chunk under the cursor. */
    private void addHoverTooltip(FateLockedBundle bundle, Rectangle bounds, RenderOverview ro)
    {
        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null || !bounds.contains(mouse.getX(), mouse.getY())) return;

        float pixelsPerTile = ro.getWorldMapZoom();
        if (pixelsPerTile <= 0) return;
        Point centre = ro.getWorldMapPosition();
        if (centre == null) return;

        // Invert worldMapRectForChunk: pixel → world tile → chunk.
        double tileX = centre.getX() + (mouse.getX() - bounds.getCenterX()) / pixelsPerTile;
        double tileY = centre.getY() - (mouse.getY() - bounds.getCenterY()) / pixelsPerTile;
        CanonicalChunk hovered = new CanonicalChunk(
            ((int) Math.floor(tileX)) >> 6, ((int) Math.floor(tileY)) >> 6);

        String label = bundle.labelAt(hovered);
        if (label == null) return; // unauthored — nothing to say

        String status = bundle.lockStateAt(hovered) == FateLockedBundle.LockState.UNLOCKED
            ? "<col=2ee59d>Unlocked</col>"
            : bundle.isFrontierChunk(hovered)
                ? "<col=f59e0b>Locked — rollable next</col>" : "<col=ef4444>Locked</col>";
        StringBuilder tip = new StringBuilder(label).append("</br>").append(status);
        if (config.worldMapTooltipContent())
        {
            // Per-chunk "what's here" from the app's chunk-content dataset —
            // capped per category so dense chunks stay a tooltip, not a page.
            for (String line : bundle.contentAt(hovered, 4))
            {
                tip.append("</br><col=a8a8a8>").append(line).append("</col>");
            }
        }
        tooltipManager.add(new Tooltip(tip.toString()));
    }

    /**
     * Translate a canonical chunk into world-map pixel coordinates inside the
     * world-map widget's bounds.
     */
    private Rectangle2D worldMapRectForChunk(CanonicalChunk chunk, Rectangle bounds, RenderOverview ro)
    {
        float pixelsPerTile = ro.getWorldMapZoom();
        // RuneLite's RenderOverview.getWorldMapPosition() returns a Point whose
        // (x, y) are world-tile coordinates of the map centre (not a WorldPoint).
        Point centre = ro.getWorldMapPosition();
        if (centre == null) return null;

        // The world-map widget shows a rectangular view of world tiles, centered
        // on `centre`. For a tile at world (tx, ty), its x-pixel on the widget is:
        //   px = widget.centerX + (tx - centre.x) * pixelsPerTile
        // y-axis is flipped (increasing y = northward in world, upward on screen):
        //   py = widget.centerY - (ty - centre.y) * pixelsPerTile
        double cx = bounds.getCenterX();
        double cy = bounds.getCenterY();

        int tileX = chunk.getCx() << 6;
        int tileY = chunk.getCy() << 6;

        double x0 = cx + (tileX - centre.getX()) * pixelsPerTile;
        double y1 = cy - (tileY - centre.getY()) * pixelsPerTile;
        double x1 = cx + (tileX + 64 - centre.getX()) * pixelsPerTile;
        double y0 = cy - (tileY + 64 - centre.getY()) * pixelsPerTile;

        return new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0);
    }
}
