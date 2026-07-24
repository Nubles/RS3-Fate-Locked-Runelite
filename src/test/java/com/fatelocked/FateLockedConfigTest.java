package com.fatelocked;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class FateLockedConfigTest
{
    @Test
    public void strictModeDefaultsOff()
    {
        FateLockedConfig config = new FateLockedConfig() {};
        assertFalse(config.strictMode());
    }
}
