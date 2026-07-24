package com.fatelocked;

import org.junit.Test;

import javax.swing.SwingUtilities;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FateLockedPanelStatusTest
{
    @Test
    public void rendersCompactSyncHealth() throws Exception
    {
        FateLockedPanel panel = new FateLockedPanel();
        panel.updateSyncHealth(4, 2, 1,
            Instant.parse("2026-07-24T14:05:06Z"), false);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("4", panel.queuedTextForTest());
        assertEquals("2", panel.reviewTextForTest());
        assertEquals("1 active", panel.warningTextForTest());
        assertTrue(panel.lastSyncTextForTest().contains("14:05:06 UTC"));
    }

    @Test
    public void labelsOfflineWithoutDiscardingCounts() throws Exception
    {
        FateLockedPanel panel = new FateLockedPanel();
        panel.updateSyncHealth(3, 1, 0, null, true);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("3", panel.queuedTextForTest());
        assertEquals("Offline", panel.lastSyncTextForTest());
    }

    @Test
    public void encodesThePairingCodeInTheRollInboxUrl()
    {
        assertEquals(
            "https://tracker.example/app?open=roll-inbox&code=AB+%26%2F%3F",
            FateLockedPanel.rollInboxUrl(
                "https://tracker.example/app",
                "AB &/?"));
    }
}
