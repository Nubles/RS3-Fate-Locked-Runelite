package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CombatAchievementDetectorTest
{
    @Test
    public void mappedTaskIsExactAndGenericCompletionIsUncertain()
    {
        CombatAchievementDetector detector = new CombatAchievementDetector();
        assertEquals(EventConfidence.EXACT,
            detector.detect("Congratulations, Combat task: Noxious Foe").getConfidence());
        assertEquals("Noxious Foe",
            detector.detect("Congratulations, Combat task: Noxious Foe").getCanonicalLabel());
        assertEquals(EventConfidence.UNCERTAIN,
            detector.detect("Combat task complete").getConfidence());
    }
}
