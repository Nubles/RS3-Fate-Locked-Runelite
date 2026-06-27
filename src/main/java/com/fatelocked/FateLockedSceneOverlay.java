package com.fatelocked;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;

/**
 * Tints the player's current chunk on the main game scene. Gives an at-a-glance
 * sense of the chunk's status (green/red/grey) matching the web app's colors.
 */
public class FateLockedSceneOverlay extends Overlay
{
    private static final Stroke STROKE = new BasicStroke(2f);
    private static final Stroke BORDER_STROKE = new BasicStroke(3.5f);

    @Inject private Client client;
    @Inject private FateLockedPlugin plugin;
    @Inject private FateLockedConfig config;

    @Inject
    FateLockedSceneOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.drawScene() && !config.highlightLockedBorders()) return null;
        Player local = client.getLocalPlayer();
        if (local == null) return null;
        WorldPoint wp = local.getWorldLocation();
        if (wp == null) return null;

        FateLockedBundle bundle = plugin.getBundle();
        CanonicalChunk chunk = CanonicalChunk.of(wp);
        int plane = wp.getPlane();

        if (config.drawScene())
        {
            // Sub-area-aware: a Falador chunk reflects Falador's lock state, not
            // all of Asgarnia's.
            Color color;
            switch (bundle.lockStateAt(chunk))
            {
                case UNLOCKED: color = config.unlockedColor(); break;
                case LOCKED: color = config.lockedColor(); break;
                default: color = config.unauthoredColor(); break;
            }
            drawChunkOutline(graphics, chunk, plane, color);
        }

        if (config.highlightLockedBorders())
        {
            drawLockedBorders(graphics, chunk, plane, bundle);
        }
        return null;
    }

    /**
     * Trace a bright line along any edge of the current chunk that borders a
     * locked chunk — the "danger here" cue right where you'd cross over.
     */
    private void drawLockedBorders(Graphics2D g, CanonicalChunk chunk, int plane, FateLockedBundle bundle)
    {
        int cx = chunk.getCx();
        int cy = chunk.getCy();
        int bx = cx << 6;
        int by = cy << 6;

        Color c = config.lockedColor();
        g.setStroke(BORDER_STROKE);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 255));

        if (isLocked(bundle, cx + 1, cy)) drawEdge(g, bx + 63, by, bx + 63, by + 63, plane); // east
        if (isLocked(bundle, cx - 1, cy)) drawEdge(g, bx, by, bx, by + 63, plane);           // west
        if (isLocked(bundle, cx, cy + 1)) drawEdge(g, bx, by + 63, bx + 63, by + 63, plane); // north
        if (isLocked(bundle, cx, cy - 1)) drawEdge(g, bx, by, bx + 63, by, plane);           // south
    }

    private static boolean isLocked(FateLockedBundle bundle, int cx, int cy)
    {
        return bundle.lockStateAt(new CanonicalChunk(cx, cy)) == FateLockedBundle.LockState.LOCKED;
    }

    private void drawEdge(Graphics2D g, int x0, int y0, int x1, int y1, int plane)
    {
        LocalPoint a = LocalPoint.fromWorld(client, x0, y0);
        LocalPoint b = LocalPoint.fromWorld(client, x1, y1);
        if (a == null || b == null) return; // edge off-scene
        Point ca = Perspective.localToCanvas(client, a, plane);
        Point cb = Perspective.localToCanvas(client, b, plane);
        if (ca == null || cb == null) return;
        g.drawLine(ca.getX(), ca.getY(), cb.getX(), cb.getY());
    }

    private void drawChunkOutline(Graphics2D g, CanonicalChunk chunk, int plane, Color color)
    {
        int baseX = chunk.getCx() << 6;
        int baseY = chunk.getCy() << 6;

        // Build a polygon around the chunk's perimeter — draw the four corner
        // tiles and let Perspective do the projection.
        LocalPoint[] corners = new LocalPoint[] {
            LocalPoint.fromWorld(client, baseX, baseY),
            LocalPoint.fromWorld(client, baseX + 63, baseY),
            LocalPoint.fromWorld(client, baseX + 63, baseY + 63),
            LocalPoint.fromWorld(client, baseX, baseY + 63)
        };

        Polygon p = new Polygon();
        for (LocalPoint lp : corners)
        {
            if (lp == null) return; // chunk is off-scene
            Point canvas = Perspective.localToCanvas(client, lp, plane);
            if (canvas == null) return;
            p.addPoint(canvas.getX(), canvas.getY());
        }

        g.setStroke(STROKE);
        g.setColor(color);
        g.fillPolygon(p);
        g.setColor(new Color(
            Math.min(color.getRed() + 30, 255),
            Math.min(color.getGreen() + 30, 255),
            Math.min(color.getBlue() + 30, 255),
            220));
        g.drawPolygon(p);
    }
}
