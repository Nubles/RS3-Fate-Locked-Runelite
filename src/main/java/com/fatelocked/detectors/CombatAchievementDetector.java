package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.Collections;

public class CombatAchievementDetector
{
    public DetectedEvent detect(String message)
    {
        String text = message == null ? "" : message.trim();
        int marker = text.toLowerCase().indexOf("combat task:");
        String label = marker < 0 ? null : text.substring(marker + "combat task:".length())
            .replaceAll("[.!]+$", "").trim();
        if (label != null && label.isEmpty()) label = null;
        return DetectedEvent.builder()
            .type(FateEventType.COMBAT_ACHIEVEMENT)
            .canonicalLabel(label)
            .confidence(label == null ? EventConfidence.UNCERTAIN : EventConfidence.EXACT)
            .detectorId("combat-achievement-chat-v1")
            .detectorVersion(1)
            .evidence(Collections.<String, Object>singletonMap("signature", "combat-task"))
            .build();
    }
}
