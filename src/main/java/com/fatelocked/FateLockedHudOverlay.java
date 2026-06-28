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
 * In-game HUD: current keys / fate points / buff / next goal from the bundle's
 * run state, plus the player's current chunk and its lock status — the
 * always-visible version of what the side panel shows.
 */
public class FateLockedHudOverlay extends OverlayPanel
{
    private static final Color GOLD = new Color(245, 158, 11);
    private static final Color GREEN = new Color(52, 211, 153);
    private static final Color RED = new Color(248, 113, 113);
    private static final Color GRAY = new Color(156, 163, 175);

    private final Client client;
    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;

    @Inject
    FateLockedHudOverlay(Client client, FateLockedPlugin plugin, FateLockedConfig config)
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
        if (!config.showHud())
        {
            return null;
        }

        FateLockedBundle bundle = plugin.getBundle();
        FateLockedBundle.RunState state = bundle.getState();

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Fate Locked")
            .color(GOLD)
            .build());

        if (state != null)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Keys")
                .right(state.getKeys() + " · O " + state.getSpecialKeys() + " · C " + state.getChaosKeys())
                .rightColor(GOLD)
                .build());
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Fate")
                .right(String.valueOf(state.getFatePoints()))
                .rightColor(Color.WHITE)
                .build());
            String buff = state.getActiveBuff();
            if (buff != null && !"NONE".equals(buff))
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Buff")
                    .right(buff)
                    .rightColor("GREED".equals(buff) ? GOLD : GREEN)
                    .build());
            }
            List<String> goals = state.getPinnedGoals();
            if (goals != null && !goals.isEmpty())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Goal")
                    .right(truncate(goals.get(0), 20))
                    .rightColor(Color.WHITE)
                    .build());
            }
            String bound = state.getLinkedAccount();
            if (bound != null && !bound.trim().isEmpty())
            {
                Player me = client.getLocalPlayer();
                String current = me == null ? null : me.getName();
                boolean match = current == null
                    || FateLockedPlugin.normName(bound).equals(FateLockedPlugin.normName(current));
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Account")
                    .right(truncate(bound, 14) + (match ? "" : " ⚠"))
                    .rightColor(match ? GREEN : RED)
                    .build());
            }
        }

        Player local = client.getLocalPlayer();
        WorldPoint wp = local == null ? null : local.getWorldLocation();
        if (wp != null)
        {
            CanonicalChunk chunk = CanonicalChunk.of(wp);
            String label = bundle.labelAt(chunk);
            FateLockedBundle.LockState lock = bundle.lockStateAt(chunk);

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Here")
                .right(label == null ? "(" + chunk.getCx() + ", " + chunk.getCy() + ")" : truncate(label, 22))
                .rightColor(Color.WHITE)
                .build());
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Status")
                .right(lock == FateLockedBundle.LockState.UNLOCKED ? "Unlocked"
                    : lock == FateLockedBundle.LockState.LOCKED ? "LOCKED" : "Unknown")
                .rightColor(lock == FateLockedBundle.LockState.UNLOCKED ? GREEN
                    : lock == FateLockedBundle.LockState.LOCKED ? RED : GRAY)
                .build());
        }

        if (bundle.getTotalChunks() > 0)
        {
            int pct = (int) Math.round(100.0 * bundle.getUnlockedChunks() / bundle.getTotalChunks());
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Unlocked")
                .right(bundle.getUnlockedAreas() + "/" + bundle.getTotalAreas() + " · " + pct + "%")
                .rightColor(GOLD)
                .build());
        }

        String slayerWarn = plugin.getSlayerTaskWarn();
        if (slayerWarn != null)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Slayer")
                .right(truncate(slayerWarn, 18) + " ⚠")
                .leftColor(RED)
                .rightColor(RED)
                .build());
        }

        String overTier = plugin.getOverTierSummary();
        if (overTier != null)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Over-tier")
                .right(truncate(overTier, 20))
                .leftColor(RED)
                .rightColor(RED)
                .build());
        }

        return super.render(graphics);
    }

    private static String truncate(String s, int max)
    {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
