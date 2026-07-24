package com.fatelocked.events;

import com.fatelocked.FateLockedBundle;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class FateEventFactory
{
    private final AtomicLong sessionSequence = new AtomicLong();

    public FateEvent create(
        FateEventType type,
        String canonicalLabel,
        EventConfidence confidence,
        Map<String, Object> evidence,
        FateLockedBundle bundle,
        String account)
    {
        return create(type, canonicalLabel, confidence, evidence, bundle, account,
            "plugin-v1", 1);
    }

    public FateEvent create(
        FateEventType type,
        String canonicalLabel,
        EventConfidence confidence,
        Map<String, Object> evidence,
        FateLockedBundle bundle,
        String account,
        String detectorId,
        int detectorVersion)
    {
        return FateEvent.builder()
            .protocolVersion(1)
            .eventId(UUID.randomUUID().toString())
            .runId(bundle == null ? null : bundle.getRunId())
            .account(account)
            .runRevision(bundle == null ? 0 : bundle.getRunRevision())
            .eventType(type)
            .canonicalLabel(canonicalLabel)
            .occurredAt(System.currentTimeMillis())
            .sessionSequence(sessionSequence.incrementAndGet())
            .bundleVersion(bundle == null ? 0 : bundle.getVersion())
            .rulesVersion(bundle == null ? "1" : bundle.getRulesVersion())
            .contentVersion(bundle == null ? 0 : bundle.getContentVersion())
            .detectorId(detectorId)
            .detectorVersion(detectorVersion)
            .confidence(confidence)
            .evidence(evidence == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(evidence))
            .build();
    }
}
