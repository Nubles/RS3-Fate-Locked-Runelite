package com.fatelocked.rules;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Getter
public final class RuneliteRulesManifest
{
    private String rulesVersion;
    private int contentVersion;
    private int detectorContractVersion;
    private String runId;
    private long runRevision;
    private String account;
    private String gameModeId;
    private String exportedAt;
    private boolean bankLocks;
    private Unlocks unlocks;
    private Map<String, ChunkPermissionSnapshot> chunks;

    public RuneliteRulesManifest normalized()
    {
        RuneliteRulesManifest copy = new RuneliteRulesManifest();
        copy.rulesVersion = rulesVersion;
        copy.contentVersion = Math.max(0, contentVersion);
        copy.detectorContractVersion = Math.max(0, detectorContractVersion);
        copy.runId = runId;
        copy.runRevision = Math.max(0, runRevision);
        copy.account = account;
        copy.gameModeId = gameModeId;
        copy.exportedAt = exportedAt;
        copy.bankLocks = bankLocks;
        copy.unlocks = unlocks == null ? new Unlocks().normalized() : unlocks.normalized();

        Map<String, ChunkPermissionSnapshot> normalizedChunks = new LinkedHashMap<>();
        if (chunks != null)
        {
            for (Map.Entry<String, ChunkPermissionSnapshot> entry :
                new TreeMap<>(chunks).entrySet())
            {
                if (entry.getValue() != null)
                {
                    normalizedChunks.put(entry.getKey(),
                        entry.getValue().normalized(entry.getKey()));
                }
            }
        }
        copy.chunks = Collections.unmodifiableMap(normalizedChunks);
        return copy;
    }

    public boolean hasRequiredFields()
    {
        return rulesVersion != null && !rulesVersion.trim().isEmpty()
            && runId != null && !runId.trim().isEmpty()
            && gameModeId != null && !gameModeId.trim().isEmpty()
            && exportedAt != null && !exportedAt.trim().isEmpty()
            && unlocks != null && chunks != null;
    }

    @Getter
    public static final class Unlocks
    {
        private List<String> regions;
        private List<String> chunks;
        private Map<String, Integer> skills;
        private Map<String, Integer> levels;
        private Map<String, Integer> equipment;
        private List<String> banks;
        private List<String> merchants;
        private List<String> bosses;
        private List<String> minigames;
        private List<String> mobility;
        private List<String> arcana;
        private List<String> guilds;
        private List<String> farming;
        private List<String> slayer;
        private List<String> quests;

        private Unlocks normalized()
        {
            Unlocks copy = new Unlocks();
            copy.regions = immutableList(regions);
            copy.chunks = immutableList(chunks);
            copy.skills = immutableMap(skills);
            copy.levels = immutableMap(levels);
            copy.equipment = immutableMap(equipment);
            copy.banks = immutableList(banks);
            copy.merchants = immutableList(merchants);
            copy.bosses = immutableList(bosses);
            copy.minigames = immutableList(minigames);
            copy.mobility = immutableList(mobility);
            copy.arcana = immutableList(arcana);
            copy.guilds = immutableList(guilds);
            copy.farming = immutableList(farming);
            copy.slayer = immutableList(slayer);
            copy.quests = immutableList(quests);
            return copy;
        }

        private static List<String> immutableList(List<String> values)
        {
            return values == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
        }

        private static Map<String, Integer> immutableMap(Map<String, Integer> values)
        {
            return values == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new TreeMap<>(values));
        }
    }
}
