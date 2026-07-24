package com.fatelocked.events;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FateEventOutboxTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Gson gson = new Gson();
    private Path path;
    private FateEventOutbox outbox;

    @Before
    public void setUp() throws Exception
    {
        path = folder.getRoot().toPath().resolve("outbox.json");
        outbox = new FateEventOutbox(gson, path);
    }

    private FateEvent event(String eventId)
    {
        return FateEvent.builder()
            .protocolVersion(1)
            .eventId(eventId)
            .runId("run-1")
            .account("Nubles")
            .runRevision(1)
            .eventType(FateEventType.QUEST)
            .canonicalLabel("Dragon Slayer")
            .occurredAt(System.currentTimeMillis())
            .sessionSequence(1)
            .bundleVersion(3)
            .rulesVersion("1")
            .contentVersion(1)
            .detectorId("quest-widget-v1")
            .detectorVersion(1)
            .confidence(EventConfidence.EXACT)
            .evidence(Collections.<String, Object>emptyMap())
            .build();
    }

    @Test
    public void pendingEventSurvivesRestart() throws Exception
    {
        outbox.enqueue(event("evt-1"));

        FateEventOutbox restarted = new FateEventOutbox(gson, path);

        assertEquals(Collections.singletonList("evt-1"),
            restarted.pending().stream()
                .map(FateEvent::getEventId)
                .collect(Collectors.toList()));
    }

    @Test
    public void acknowledgementSurvivesAndPreventsReplay() throws Exception
    {
        outbox.enqueue(event("evt-1"));
        outbox.acknowledge(Collections.singleton("evt-1"));

        FateEventOutbox restarted = new FateEventOutbox(gson, path);
        assertFalse(restarted.contains("evt-1"));
        assertFalse(restarted.enqueue(event("evt-1")));
    }

    @Test
    public void duplicatePendingEventIsNotWrittenTwice() throws Exception
    {
        assertTrue(outbox.enqueue(event("evt-1")));
        assertFalse(outbox.enqueue(event("evt-1")));
        assertEquals(1, outbox.pending().size());
    }
}
