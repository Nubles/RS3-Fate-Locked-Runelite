package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class ClueCasketDetector
{
    private static final Set<String> ALLOWLIST = new HashSet<>(Arrays.asList(
        "casket (beginner)", "casket (easy)", "casket (medium)",
        "casket (hard)", "casket (elite)", "casket (master)"));

    public Optional<DetectedEvent> detect(String lootName)
    {
        if (lootName == null) return Optional.empty();
        String normalized = lootName.trim().toLowerCase();
        if (!normalized.contains("casket")) return Optional.empty();
        boolean exact = ALLOWLIST.contains(normalized);
        return Optional.of(DetectedEvent.builder()
            .type(FateEventType.CLUE_CASKET)
            .canonicalLabel(lootName.trim())
            .confidence(exact ? EventConfidence.EXACT : EventConfidence.UNCERTAIN)
            .detectorId("clue-casket-loot-v1")
            .detectorVersion(1)
            .evidence(java.util.Collections.<String, Object>singletonMap("allowlisted", exact))
            .build());
    }
}
