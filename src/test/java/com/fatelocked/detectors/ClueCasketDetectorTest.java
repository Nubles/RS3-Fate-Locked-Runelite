package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClueCasketDetectorTest
{
    @Test
    public void allowlistedCasketIsExactAndUnknownCasketIsUncertain()
    {
        ClueCasketDetector detector = new ClueCasketDetector();
        assertEquals(EventConfidence.EXACT,
            detector.detect("Casket (hard)").get().getConfidence());
        assertEquals(EventConfidence.UNCERTAIN,
            detector.detect("Ancient casket").get().getConfidence());
    }
}
