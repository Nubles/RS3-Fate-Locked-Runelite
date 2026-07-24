package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QuestDetectorTest
{
    @Test
    public void exactNameIsExactAndMissingNameIsUncertain()
    {
        QuestDetector detector = new QuestDetector();
        assertEquals(EventConfidence.EXACT,
            detector.detect("Dragon Slayer").getConfidence());
        assertEquals(EventConfidence.UNCERTAIN,
            detector.detect(null).getConfidence());
    }
}
