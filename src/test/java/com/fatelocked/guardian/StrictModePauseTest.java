package com.fatelocked.guardian;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class StrictModePauseTest
{
    @Test
    public void pausesForExactlySixtySeconds()
    {
        MutableClock clock = new MutableClock();
        StrictModePause pause = new StrictModePause(clock);
        pause.pauseFor(Duration.ofSeconds(60));
        assertEquals(60, pause.remainingSeconds());
        clock.advance(Duration.ofSeconds(17));
        assertEquals(43, pause.remainingSeconds());
        clock.advance(Duration.ofSeconds(43));
        assertFalse(pause.isPaused());
    }

    private static final class MutableClock extends Clock
    {
        private Instant now = Instant.parse("2026-07-24T10:00:00Z");

        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
