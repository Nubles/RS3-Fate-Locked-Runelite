package com.fatelocked.panel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.fatelocked.rules.ChunkPermissionRow;
import com.fatelocked.rules.ChunkPermissionSnapshot;
import com.fatelocked.rules.FateRuleEngine;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChunkPanelViewModelFactory
{
    private static final List<String> ORDER = Arrays.asList(
        "SKILLING", "BANKS", "SHOPS", "QUESTS", "COMBAT",
        "TRAVEL", "FARMING", "ACTIVITIES");

    private static final Map<String, String> TITLES;
    static
    {
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put("SKILLING", "Skilling");
        titles.put("BANKS", "Banks");
        titles.put("SHOPS", "Shops");
        titles.put("QUESTS", "Quests");
        titles.put("COMBAT", "Combat");
        titles.put("TRAVEL", "Travel");
        titles.put("FARMING", "Farming");
        titles.put("ACTIVITIES", "Activities");
        TITLES = Collections.unmodifiableMap(titles);
    }

    public ChunkPanelViewModel create(
        FateLockedBundle bundle,
        CanonicalChunk chunk,
        boolean accountMatches,
        Instant importedAt)
    {
        RuleDecision entry = new FateRuleEngine(
            bundle, accountMatches, false).entry(chunk);
        ChunkPermissionSnapshot snapshot = accountMatches
            ? bundle.permissionsAt(chunk).orElse(null) : null;
        List<ChunkPanelViewModel.CategoryView> categories = new ArrayList<>();
        int allowed = 0;
        int notReady = 0;
        int locked = 0;
        int unknown = 0;

        if (snapshot != null)
        {
            for (String id : ORDER)
            {
                List<ChunkPermissionRow> source = snapshot.getCategories().get(id);
                if (source == null || source.isEmpty()) continue;
                List<ChunkPanelViewModel.RowView> rows = new ArrayList<>();
                for (ChunkPermissionRow row : source)
                {
                    PermissionStatus status = row.getStatus();
                    if (status == PermissionStatus.ALLOWED) allowed++;
                    else if (status == PermissionStatus.NOT_READY) notReady++;
                    else if (status == PermissionStatus.LOCKED) locked++;
                    else unknown++;
                    rows.add(new ChunkPanelViewModel.RowView(
                        row.getName(),
                        status,
                        glyph(id, status),
                        statusText(id, status),
                        keepsDetail(id) ? row.getDetail() : null));
                }
                categories.add(new ChunkPanelViewModel.CategoryView(
                    id, TITLES.get(id), rows));
            }
        }

        String name = snapshot == null ? bundle.labelAt(chunk) : snapshot.getName();
        if (name == null || name.trim().isEmpty()) name = "Unknown chunk";
        String region = snapshot == null ? bundle.regionAt(chunk) : snapshot.getRegion();
        return new ChunkPanelViewModel(
            name,
            region,
            chunk.getCx() + ", " + chunk.getCy(),
            entry.getStatus(),
            freshness(importedAt),
            allowed,
            notReady,
            locked,
            unknown,
            categories);
    }

    private static boolean keepsDetail(String category)
    {
        return "SKILLING".equals(category) || "TRAVEL".equals(category);
    }

    private static String glyph(String category, PermissionStatus status)
    {
        if (status == PermissionStatus.UNKNOWN) return "?";
        if ("COMBAT".equals(category))
        {
            return status == PermissionStatus.ALLOWED ? "✓" : "✕";
        }
        if (status == PermissionStatus.ALLOWED) return "✓";
        if (status == PermissionStatus.NOT_READY) return "○";
        return "✕";
    }

    private static String statusText(String category, PermissionStatus status)
    {
        if ("QUESTS".equals(category) || "COMBAT".equals(category)) return null;
        if (status == PermissionStatus.ALLOWED) return "Available";
        if (status == PermissionStatus.NOT_READY) return "Not ready";
        if (status == PermissionStatus.LOCKED) return "Locked";
        return "Unknown";
    }

    private static String freshness(Instant importedAt)
    {
        if (importedAt == null) return "Offline snapshot";
        long minutes = Math.max(0,
            Duration.between(importedAt, Instant.now()).toMinutes());
        return minutes < 1 ? "Synced now" : "Synced " + minutes + "m ago";
    }
}
