package com.fatelocked.events;

import com.fatelocked.FateLockedBundle;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class FateEventFactoryTest
{
    @Test
    public void createsOneStableOccurrenceWithBundleIdentity()
    {
        FateLockedBundle bundle = FateLockedBundle.loadFromJson(new Gson(),
            "{\"version\":3,\"runId\":\"run-1\",\"runRevision\":12,"
                + "\"rulesVersion\":\"1\",\"contentVersion\":4,"
                + "\"chunks\":{\"Misthalin\":[{\"cx\":50,\"cy\":50}]}}");
        FateEventFactory factory = new FateEventFactory();

        FateEvent first = factory.create(
            FateEventType.QUEST, "Dragon Slayer", EventConfidence.EXACT,
            Collections.<String, Object>singletonMap("widget", 153),
            bundle, "Nubles");
        FateEvent second = factory.create(
            FateEventType.QUEST, "Demon Slayer", EventConfidence.EXACT,
            Collections.<String, Object>emptyMap(), bundle, "Nubles");

        assertTrue(first.getEventId().matches("^[0-9a-f-]{36}$"));
        assertNotEquals(first.getEventId(), second.getEventId());
        assertEquals("run-1", first.getRunId());
        assertEquals(12, first.getRunRevision());
        assertEquals("1", first.getRulesVersion());
        assertEquals(4, first.getContentVersion());
        assertEquals(1, first.getSessionSequence());
        assertEquals(2, second.getSessionSequence());
    }
}
