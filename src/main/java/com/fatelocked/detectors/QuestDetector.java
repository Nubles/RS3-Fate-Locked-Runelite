package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.Collections;

public class QuestDetector
{
    public DetectedEvent detect(String questName)
    {
        String label = questName == null || questName.trim().isEmpty()
            ? null : questName.trim();
        return DetectedEvent.builder()
            .type(FateEventType.QUEST)
            .canonicalLabel(label)
            .confidence(label == null ? EventConfidence.UNCERTAIN : EventConfidence.EXACT)
            .detectorId("quest-widget-v1")
            .detectorVersion(1)
            .evidence(Collections.<String, Object>singletonMap("rewardWidget", true))
            .build();
    }
}
