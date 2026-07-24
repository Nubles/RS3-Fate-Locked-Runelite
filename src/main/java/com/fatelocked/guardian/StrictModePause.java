package com.fatelocked.guardian;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public final class StrictModePause
{
    private final Clock clock;
    private Instant pausedUntil;

    public StrictModePause(Clock clock)
    {
        this.clock = clock;
    }

    public synchronized void pauseFor(Duration duration)
    {
        pausedUntil = clock.instant().plus(duration);
    }

    public synchronized void resume()
    {
        pausedUntil = null;
    }

    public synchronized boolean isPaused()
    {
        return remainingSeconds() > 0;
    }

    public synchronized long remainingSeconds()
    {
        if (pausedUntil == null) return 0;
        long millis = Duration.between(clock.instant(), pausedUntil).toMillis();
        if (millis <= 0)
        {
            pausedUntil = null;
            return 0;
        }
        return (millis + 999) / 1000;
    }
}
