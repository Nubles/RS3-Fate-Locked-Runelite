package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CollectionLogDetectorTest
{
    @Test
    public void uniqueMappingIsExactAndDuplicateNameIsUncertain()
    {
        CollectionLogDetector detector = new CollectionLogDetector();
        assertEquals(EventConfidence.EXACT,
            detector.detect("Abyssal whip", true).getConfidence());
        assertEquals(EventConfidence.UNCERTAIN,
            detector.detect("Mystery item", false).getConfidence());
    }
}
