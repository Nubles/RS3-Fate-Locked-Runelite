package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class BossRaidDetector
{
    private static final Set<String> RAIDS = new HashSet<>(Arrays.asList(
        "chambers of xeric", "theatre of blood", "tombs of amascut"));

    public Optional<DetectedEvent> detect(String lootType, String name, int combatLevel)
    {
        String normalized = name == null ? "" : name.trim().toLowerCase();
        if ("EVENT".equals(lootType) && RAIDS.contains(normalized))
        {
            return Optional.of(event(FateEventType.RAID_COMPLETION, name,
                EventConfidence.EXACT, "raid-loot-v1", combatLevel));
        }
        if ("NPC".equals(lootType) && combatLevel >= 250)
        {
            return Optional.of(event(FateEventType.BOSS_KILL, name,
                EventConfidence.UNCERTAIN, "boss-loot-v1", combatLevel));
        }
        return Optional.empty();
    }

    private DetectedEvent event(FateEventType type, String label,
                                EventConfidence confidence, String detectorId,
                                int combatLevel)
    {
        return DetectedEvent.builder()
            .type(type)
            .canonicalLabel(label)
            .confidence(confidence)
            .detectorId(detectorId)
            .detectorVersion(1)
            .evidence(java.util.Collections.<String, Object>singletonMap(
                "combatLevel", combatLevel))
            .build();
    }
}
