package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class BossKillDetectorV2
{
    private final Map<String, String> encounters = new HashMap<>();
    private String lastKey;

    public BossKillDetectorV2()
    {
        for (String name : Arrays.asList(
            "Vorkath", "Zulrah", "Bryophyta", "Nex", "Hespori",
            "General Graardor", "Kree'arra", "Commander Zilyana",
            "K'ril Tsutsaroth"))
            encounters.put(name.toLowerCase(), name);
    }

    public Optional<DetectedEvent> detect(String lootType, String name, long cycle)
    {
        String canonical = name == null ? null : encounters.get(name.trim().toLowerCase());
        if (canonical == null) return Optional.empty();
        String key = canonical + ":" + cycle;
        if (key.equals(lastKey)) return Optional.empty();
        lastKey = key;
        return Optional.of(DetectedEvent.builder()
            .type(FateEventType.BOSS_KILL)
            .canonicalLabel(canonical)
            .confidence(EventConfidence.UNCERTAIN)
            .detectorId("boss-kill-v2")
            .detectorVersion(2)
            .evidence(Collections.<String, Object>singletonMap("lootType", lootType))
            .build());
    }
}
