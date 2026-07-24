package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SkillLevelDetector
{
    private final Map<String, Integer> levels = new HashMap<>();

    public Optional<DetectedEvent> detect(String skill, int level)
    {
        Integer previous = levels.put(skill, level);
        if (previous == null || level <= previous) return Optional.empty();
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("skill", skill);
        evidence.put("previousLevel", previous);
        evidence.put("level", level);
        return Optional.of(DetectedEvent.builder()
            .type(FateEventType.SKILL_LEVEL)
            .canonicalLabel(skill + " Level " + level)
            .confidence(EventConfidence.EXACT)
            .detectorId("skill-level-v1")
            .detectorVersion(1)
            .evidence(evidence)
            .build());
    }

    public void clear()
    {
        levels.clear();
    }
}
