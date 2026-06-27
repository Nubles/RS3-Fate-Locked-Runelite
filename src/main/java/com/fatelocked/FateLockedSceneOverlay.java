package com.fatelocked;

import net.runelite.api.Client;
import net.runelite.api.Constants;
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
        if (!config.drawScene() && !config.highlightLockedBorders() && !config.shadeNearbyLocked()) return null;
        Player local = client.getLocalPlayer();
        if (local == null) return null;
        WorldPoint wp = local.getWorldLocation();
        if (wp == null) return null;

        FateLockedBundle bundle = plugin.getBundle();
        CanonicalChunk chunk = CanonicalChunk.of(wp);
        int plane = wp.getPlane();

        // Light shading for surrounding locked chunks goes first, under the
        // current-chunk tint and borders.
        if (config.shadeNearbyLocked())
        {
            drawSurroundingLocked(graphics, chunk, plane, bundle);
        }

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

    /**
     * Lightly tint every locked chunk overlapping the loaded scene (except the
     * one the player is standing in, which gets the full treatment elsewhere).
     */
    private void drawSurroundingLocked(Graphics2D g, CanonicalChunk current, int plane, FateLockedBundle bundle)
    {
        int baseX = client.getBaseX();
        int baseY = client.getBaseY();
        int cxMin = baseX >> 6, cxMax = (baseX + Constants.SCENE_SIZE - 1) >> 6;
        int cyMin = baseY >> 6, cyMax = (baseY + Constants.SCENE_SIZE - 1) >> 6;

        Color light = faint(config.lockedColor());
        g.setColor(light);
        for (int cx = cxMin; cx <= cxMax; cx++)
        {
            for (int cy = cyMin; cy <= cyMax; cy++)
            {
                if (cx == current.getCx() && cy == current.getCy()) continue;
                CanonicalChunk c = new CanonicalChunk(cx, cy);
                if (bundle.lockStateAt(c) != FateLockedBundle.LockState.LOCKED) continue;
                Polygon p = chunkScenePolyClamped(c, plane);
                if (p != null) g.fillPolygon(p);
            }
        }
    }

    /** Chunk outline polygon clipped to the loaded scene, so partly-visible chunks still draw. */
    private Polygon chunkScenePolyClamped(CanonicalChunk chunk, int plane)
    {
        int minX = client.getBaseX(), minY = client.getBaseY();
        int maxX = minX + Constants.SCENE_SIZE - 1, maxY = minY + Constants.SCENE_SIZE - 1;
        int x0 = Math.max(chunk.getCx() << 6, minX);
        int y0 = Math.max(chunk.getCy() << 6, minY);
        int x1 = Math.min((chunk.getCx() << 6) + 63, maxX);
        int y1 = Math.min((chunk.getCy() << 6) + 63, maxY);
        if (x0 > x1 || y0 > y1) return null; // no overlap with the scene

        int[][] cs = { { x0, y0 }, { x1, y0 }, { x1, y1 }, { x0, y1 } };
        Polygon p = new Polygon();
        for (int[] c : cs)
        {
            LocalPoint lp = LocalPoint.fromWorld(client, c[0], c[1]);
            if (lp == null) return null;
            Point cv = Perspective.localToCanvas(client, lp, plane);
            if (cv == null) return null;
            p.addPoint(cv.getX(), cv.getY());
        }
        return p;
    }

    /** A faint version of a color for subtle background shading. */
    private static Color faint(Color c)
    {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(20, Math.min(c.getAlpha(), 110) / 3));
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
