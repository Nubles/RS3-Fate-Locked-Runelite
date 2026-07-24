package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BossRaidDetectorTest
{
    @Test
    public void allowlistedRaidIsExactAndCombatLevelHeuristicIsUncertain()
    {
        BossRaidDetector detector = new BossRaidDetector();
        DetectedEvent raid = detector.detect("EVENT", "Chambers of Xeric", 0).get();
        assertEquals(FateEventType.RAID_COMPLETION, raid.getType());
        assertEquals(EventConfidence.EXACT, raid.getConfidence());

        DetectedEvent boss = detector.detect("NPC", "Unknown creature", 500).get();
        assertEquals(FateEventType.BOSS_KILL, boss.getType());
        assertEquals(EventConfidence.UNCERTAIN, boss.getConfidence());
    }
}
