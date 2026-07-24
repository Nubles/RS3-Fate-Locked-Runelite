package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class DetectedEvent
{
    FateEventType type;
    String canonicalLabel;
    EventConfidence confidence;
    String detectorId;
    int detectorVersion;
    Map<String, Object> evidence;
}
