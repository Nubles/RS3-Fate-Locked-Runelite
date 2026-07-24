package com.fatelocked;

import com.google.gson.Gson;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FateLockedBundleTest
{
    private FateLockedBundle fixture(String name) throws Exception
    {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name))
        {
            assertNotNull("missing fixture " + name, in);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return FateLockedBundle.loadFromJson(new Gson(), json);
        }
    }

    @Test
    public void legacyBundleStillUsesContinentUnlocks() throws Exception
    {
        FateLockedBundle bundle = fixture("bundles/v1-legacy.json");

        assertEquals(FateLockedBundle.LockState.UNLOCKED,
            bundle.lockStateAt(new CanonicalChunk(46, 52)));
    }

    @Test
    public void standardBundleUsesSubAreaAndBankState() throws Exception
    {
        FateLockedBundle bundle = fixture("bundles/v3-standard.json");

        assertEquals(FateLockedBundle.LockState.UNLOCKED,
            bundle.lockStateAt(new CanonicalChunk(46, 52)));
        assertTrue(bundle.isBankUnlocked(new CanonicalChunk(46, 52)));
        assertEquals(FateLockedBundle.LockState.UNLOCKED,
            bundle.lockStateAt(new CanonicalChunk(50, 50)));
    }

    @Test
    public void emptyChunkedBundleStillUnlocksTheStartChunk() throws Exception
    {
        FateLockedBundle bundle = fixture("bundles/v3-chunked-empty.json");

        assertTrue(bundle.isChunkedBundle());
        assertEquals(FateLockedBundle.LockState.UNLOCKED,
            bundle.lockStateAt(FateLockedBundle.CHUNKED_START));
    }
}
