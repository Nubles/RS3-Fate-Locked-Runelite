package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class PetDropDetector
{
    private final Map<Integer, String> identities = new HashMap<>();
    private long lastEventAt;

    public PetDropDetector()
    {
        identities.put(8029, "Vorki");
        identities.put(7334, "Olmlet");
        identities.put(6637, "Jal-Nib-Rek");
    }

    public Optional<DetectedEvent> detect(
        String message, Integer followerId, long now)
    {
        String normalized = message == null ? "" : message.toLowerCase();
        if (!normalized.contains("funny feeling")
            || normalized.contains("insured") || normalized.contains("reclaim"))
            return Optional.empty();
        if (now - lastEventAt < 5000) return Optional.empty();
        lastEventAt = now;
        String label = followerId == null ? null : identities.get(followerId);
        return Optional.of(DetectedEvent.builder()
            .type(FateEventType.PET_DROP)
            .canonicalLabel(label)
            .confidence(EventConfidence.UNCERTAIN)
            .detectorId("pet-drop-v1")
            .detectorVersion(1)
            .evidence(Collections.<String, Object>singletonMap(
                "followerId", followerId == null ? -1 : followerId))
            .build());
    }
}
