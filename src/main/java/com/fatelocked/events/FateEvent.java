package com.fatelocked.events;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class FateEvent
{
    int protocolVersion;
    String eventId;
    String runId;
    String account;
    long runRevision;
    FateEventType eventType;
    String canonicalLabel;
    long occurredAt;
    long sessionSequence;
    int bundleVersion;
    String rulesVersion;
    int contentVersion;
    String detectorId;
    int detectorVersion;
    EventConfidence confidence;
    Map<String, Object> evidence;
}
