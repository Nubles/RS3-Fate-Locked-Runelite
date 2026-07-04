package com.fatelocked;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;

/**
 * "In this chunk" — a togglable, draggable overlay that lists what the web
 * app's map chunk-info panel shows for the chunk the player is standing in:
 * its monsters, shops, farming patches and points of interest, plus whether
 * the chunk is available (unlocked) or off-limits. The persistent, in-world
 * twin of the world-map hover tooltip. Off by default (opt-in, draggable).
 */
public class FateLockedContentOverlay extends OverlayPanel
{
    private static final Color GOLD  = new Color(245, 158, 11);
    private static final Color GREEN = new Color(52, 211, 153);
    private static final Color RED   = new Color(248, 113, 113);
    private static final Color GRAY  = new Color(156, 163, 175);
    private static final Color NAMES = new Color(209, 213, 219);

    /** Category header colour, matching the app's sectioned chunk panel. */
    private static Color categoryColor(String label)
    {
        switch (label)
        {
            case "Monsters": return new Color(251, 146, 60);  // combat orange
            case "Shops":    return GOLD;
            case "Farming":  return GREEN;
            case "Points":   return new Color(96, 165, 250);  // POI blue
            default:         return Color.WHITE;
        }
    }

    private final Client client;
    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;

    @Inject
    FateLockedContentOverlay(Client client, FateLockedPlugin plugin, FateLockedConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setResizable(false);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showChunkContentBox())
        {
            return null;
        }

        Player local = client.getLocalPlayer();
        WorldPoint wp = local == null ? null : local.getWorldLocation();
        if (wp == null)
        {
            return null;
        }

        FateLockedBundle bundle = plugin.getBundle();
        CanonicalChunk chunk = CanonicalChunk.of(wp);
        String label = bundle.labelAt(chunk);
        FateLockedBundle.LockState lock = bundle.lockStateAt(chunk);

        panelComponent.setPreferredSize(new Dimension(150, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("In this chunk")
            .color(GOLD)
            .build());

        // Area name (wraps naturally under the title) then availability.
        panelComponent.getChildren().add(LineComponent.builder()
            .left(label == null ? "(" + chunk.getCx() + ", " + chunk.getCy() + ")" : label)
            .leftColor(Color.WHITE)
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left(lock == FateLockedBundle.LockState.UNLOCKED ? "Available"
                : lock == FateLockedBundle.LockState.LOCKED ? "LOCKED — off-limits" : "Unknown area")
            .leftColor(lock == FateLockedBundle.LockState.UNLOCKED ? GREEN
                : lock == FateLockedBundle.LockState.LOCKED ? RED : GRAY)
            .build());

        // Content lines come pre-formatted as "Monsters: A, B, C" — split each
        // into a coloured category header + its wrapped name list.
        List<String> content = bundle.contentAt(chunk, 8);
        if (content.isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("No tracked content here.")
                .leftColor(GRAY)
                .build());
        }
        else
        {
            for (String line : content)
            {
                int sep = line.indexOf(": ");
                String cat = sep < 0 ? line : line.substring(0, sep);
                String names = sep < 0 ? "" : line.substring(sep + 2);
                panelComponent.getChildren().add(LineComponent.builder()
                    .left(cat)
                    .leftColor(categoryColor(cat))
                    .build());
                if (!names.isEmpty())
                {
                    panelComponent.getChildren().add(LineComponent.builder()
                        .left(names)
                        .leftColor(NAMES)
                        .build());
                }
            }
        }

        return super.render(graphics);
    }
}
