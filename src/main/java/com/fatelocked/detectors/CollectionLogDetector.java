package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.Collections;

public class CollectionLogDetector
{
    public DetectedEvent detect(String itemName, boolean uniqueMapping)
    {
        String label = itemName == null || itemName.trim().isEmpty() ? null : itemName.trim();
        return DetectedEvent.builder()
            .type(FateEventType.COLLECTION_LOG)
            .canonicalLabel(label)
            .confidence(label != null && uniqueMapping
                ? EventConfidence.EXACT : EventConfidence.UNCERTAIN)
            .detectorId("collection-log-chat-v1")
            .detectorVersion(1)
            .evidence(Collections.<String, Object>singletonMap("uniqueMapping", uniqueMapping))
            .build();
    }
}
