package com.fatelocked;

import com.fatelocked.panel.ChunkPanelViewModel;
import com.fatelocked.rules.PermissionStatus;
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

/** Optional draggable compact twin of the category-first side panel. */
public class FateLockedContentOverlay extends OverlayPanel
{
    private static final Color AMBER = new Color(245, 158, 11);
    private static final Color GREEN = new Color(16, 185, 129);
    private static final Color RED = new Color(239, 68, 68);
    private static final Color GRAY = new Color(156, 163, 175);
    private static final Color WHITE = new Color(229, 231, 235);

    private final Client client;
    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;

    @Inject
    FateLockedContentOverlay(
        Client client,
        FateLockedPlugin plugin,
        FateLockedConfig config)
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
        if (!config.showChunkContentBox()) return null;
        Player local = client.getLocalPlayer();
        WorldPoint point = local == null ? null : local.getWorldLocation();
        if (point == null) return null;

        FateLockedBundle bundle = plugin.getBundle();
        CanonicalChunk chunk = CanonicalChunk.of(point);
        ChunkPanelViewModel view = plugin.viewModelFor(bundle, chunk);
        if (view == null) return null;

        panelComponent.setPreferredSize(new Dimension(210, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
            .text(view.getName())
            .color(AMBER)
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left((view.getRegion() == null ? "Unknown region" : view.getRegion())
                + " · " + view.getCoordinates())
            .leftColor(GRAY)
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left(statusText(view.getEntryStatus()) + " · " + view.getFreshnessLabel())
            .leftColor(statusColor(view.getEntryStatus()))
            .build());

        for (ChunkPanelViewModel.CategoryView category : view.getCategories())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left(category.getTitle())
                .leftColor(AMBER)
                .build());
            int visible = Math.min(5, category.getRows().size());
            for (int i = 0; i < visible; i++)
            {
                ChunkPanelViewModel.RowView row = category.getRows().get(i);
                panelComponent.getChildren().add(LineComponent.builder()
                    .left(row.getStatusGlyph() + " " + row.getName())
                    .leftColor(statusColor(row.getStatus()))
                    .build());
            }
            int more = category.getRows().size() - visible;
            if (more > 0)
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("+" + more + " more")
                    .leftColor(GRAY)
                    .build());
            }
        }

        if (view.getCategories().isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("No mapped permissions here")
                .leftColor(WHITE)
                .build());
        }
        return super.render(graphics);
    }

    private static Color statusColor(PermissionStatus status)
    {
        if (status == PermissionStatus.ALLOWED) return GREEN;
        if (status == PermissionStatus.NOT_READY) return AMBER;
        if (status == PermissionStatus.LOCKED) return RED;
        return GRAY;
    }

    private static String statusText(PermissionStatus status)
    {
        if (status == PermissionStatus.ALLOWED) return "Available";
        if (status == PermissionStatus.NOT_READY) return "Not ready";
        if (status == PermissionStatus.LOCKED) return "Locked";
        return "Unknown";
    }
}
