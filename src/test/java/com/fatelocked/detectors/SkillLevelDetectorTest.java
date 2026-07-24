package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkillLevelDetectorTest
{
    @Test
    public void baselineDoesNotEmitButIncreaseDoes()
    {
        SkillLevelDetector detector = new SkillLevelDetector();
        assertFalse(detector.detect("Attack", 70).isPresent());
        DetectedEvent event = detector.detect("Attack", 71).get();
        assertEquals(FateEventType.SKILL_LEVEL, event.getType());
        assertEquals("Attack Level 71", event.getCanonicalLabel());
        assertEquals(EventConfidence.EXACT, event.getConfidence());
    }
}
