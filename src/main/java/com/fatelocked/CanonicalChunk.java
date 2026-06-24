package com.fatelocked;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * A 64-tile chunk in canonical OSRS worldspace — i.e. the same {@code cx=x>>6,
 * cy=y>>6} that RuneLite produces. The web app stores chunks in a translated
 * space; {@link FateLockedBundle} normalizes everything to this class so
 * downstream code never has to think about the offset.
 */
@Value
public class CanonicalChunk
{
    int cx;
    int cy;

    public static CanonicalChunk of(WorldPoint wp)
    {
        return new CanonicalChunk(wp.getX() >> 6, wp.getY() >> 6);
    }

    /** South-west corner tile of this chunk, plane 0. */
    public WorldPoint southWestTile()
    {
        return new WorldPoint(cx << 6, cy << 6, 0);
    }

    /** North-east corner tile of this chunk, plane 0. */
    public WorldPoint northEastTile()
    {
        return new WorldPoint((cx << 6) + 63, (cy << 6) + 63, 0);
    }
}
