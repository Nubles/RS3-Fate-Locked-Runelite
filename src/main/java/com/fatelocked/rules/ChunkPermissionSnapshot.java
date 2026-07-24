package com.fatelocked.rules;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public final class ChunkPermissionSnapshot
{
    private String chunkKey;
    private String name;
    private String region;
    private PermissionStatus entry;
    private Map<String, List<ChunkPermissionRow>> categories;
    private Counts counts;

    ChunkPermissionSnapshot normalized(String fallbackKey)
    {
        ChunkPermissionSnapshot copy = new ChunkPermissionSnapshot();
        copy.chunkKey = chunkKey == null ? fallbackKey : chunkKey;
        copy.name = name;
        copy.region = region;
        copy.entry = entry == null ? PermissionStatus.UNKNOWN : entry;

        Map<String, List<ChunkPermissionRow>> normalized = new LinkedHashMap<>();
        if (categories != null)
        {
            for (Map.Entry<String, List<ChunkPermissionRow>> category : categories.entrySet())
            {
                List<ChunkPermissionRow> rows = new ArrayList<>();
                if (category.getValue() != null)
                {
                    for (ChunkPermissionRow row : category.getValue())
                    {
                        if (row != null) rows.add(row.normalized());
                    }
                }
                normalized.put(category.getKey(),
                    Collections.unmodifiableList(rows));
            }
        }
        copy.categories = Collections.unmodifiableMap(normalized);
        copy.counts = counts == null ? new Counts() : counts.normalized();
        return copy;
    }

    @Getter
    public static final class Counts
    {
        private int allowed;
        private int notReady;
        private int locked;
        private int unknown;

        private Counts normalized()
        {
            Counts copy = new Counts();
            copy.allowed = Math.max(0, allowed);
            copy.notReady = Math.max(0, notReady);
            copy.locked = Math.max(0, locked);
            copy.unknown = Math.max(0, unknown);
            return copy;
        }
    }
}
