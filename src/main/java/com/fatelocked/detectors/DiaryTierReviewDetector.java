package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class DiaryTierReviewDetector
{
    public Optional<DetectedEvent> onVarbit(
        String tierId, int previousValue, int completedValue)
    {
        if (tierId == null || previousValue != 0 || completedValue != 1)
            return Optional.empty();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("tierId", tierId);
        evidence.put("previousValue", previousValue);
        evidence.put("completedValue", completedValue);
        return Optional.of(DetectedEvent.builder()
            .type(FateEventType.DIARY_TASK)
            .canonicalLabel(null)
            .confidence(EventConfidence.UNCERTAIN)
            .detectorId("diary-task-v1")
            .detectorVersion(1)
            .evidence(evidence)
            .build());
    }
}
