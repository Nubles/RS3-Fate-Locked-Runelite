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

    // Nearest bank/shop cache — recomputed on chunk change or bundle reload.
    private FateLockedBundle cachedBundle;
    private CanonicalChunk cachedChunk;
    private FateLockedBundle.Nearest cachedBank;
    private FateLockedBundle.Nearest cachedShop;

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

        // Fixed width so long area names ("Draynor Village · Misthalin") sit on
        // one line instead of wrapping to three.
        panelComponent.setPreferredSize(new Dimension(165, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Fate Locked")
            .color(GOLD)
            .build());

        if (state != null)
        {
            StringBuilder keyStr = new StringBuilder(String.valueOf(state.getKeys()));
            if (state.getSpecialKeys() > 0) keyStr.append(" · O").append(state.getSpecialKeys());
            if (state.getChaosKeys() > 0) keyStr.append(" · C").append(state.getChaosKeys());
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Keys")
                .right(keyStr.toString())
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

            if (config.showNearest() && bundle.hasNearestData())
            {
                // Recompute only when the player crosses a chunk boundary or a
                // new bundle is imported — render() runs per frame.
                if (bundle != cachedBundle || !chunk.equals(cachedChunk))
                {
                    cachedBundle = bundle;
                    cachedChunk = chunk;
                    cachedBank = bundle.nearestUsableBank(chunk);
                    cachedShop = bundle.nearestUsableShop(chunk);
                }
                addNearestLine("Bank", cachedBank, chunk, bundle);
                addNearestLine("Shop", cachedShop, chunk, bundle);
            }
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

    /** One "Bank:" / "Shop:" line: "here ✓", "<Area> · <dist> <dir>", or "none unlocked". */
    private void addNearestLine(String label, FateLockedBundle.Nearest near,
                                CanonicalChunk from, FateLockedBundle bundle)
    {
        if (near == null)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left(label)
                .right("none unlocked")
                .rightColor(RED)
                .build());
            return;
        }
        if (near.getDistanceChunks() == 0)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left(label)
                .right("here ✓")
                .rightColor(GREEN)
                .build());
            return;
        }
        String area = bundle.labelAt(near.getChunk());
        String name = area == null
            ? "(" + near.getChunk().getCx() + ", " + near.getChunk().getCy() + ")"
            : area.split(" · ")[0];
        String dir = compass(near.getChunk().getCx() - from.getCx(),
            near.getChunk().getCy() - from.getCy());
        panelComponent.getChildren().add(LineComponent.builder()
            .left(label)
            .right(truncate(name, 13) + " · " + near.getDistanceChunks() + " " + dir)
            .rightColor(Color.WHITE)
            .build());
    }

    /** 8-way compass point for a chunk delta; a 2:1 dominant axis collapses
     *  to its cardinal (dx=+5,dy=+1 → "E"; dx=+5,dy=+4 → "NE"). World Y
     *  grows northward. Never called with dx=dy=0 (distance 0 is "here"). */
    static String compass(int dx, int dy)
    {
        String ns = dy > 0 ? "N" : "S";
        String ew = dx > 0 ? "E" : "W";
        if (dy == 0 || Math.abs(dx) >= 2 * Math.abs(dy)) return ew;
        if (dx == 0 || Math.abs(dy) >= 2 * Math.abs(dx)) return ns;
        return ns + ew;
    }

    private static String truncate(String s, int max)
    {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
