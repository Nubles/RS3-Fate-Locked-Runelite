package com.fatelocked.panel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.fatelocked.rules.PermissionStatus;
import com.google.gson.Gson;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ChunkPanelViewModelFactoryTest
{
    private FateLockedBundle fixture() throws Exception
    {
        try (InputStream in = getClass().getClassLoader()
            .getResourceAsStream("bundles/v4-rules.json"))
        {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return FateLockedBundle.loadFromJson(new Gson(), json);
        }
    }

    @Test
    public void createsOrderedCompactCategories() throws Exception
    {
        ChunkPanelViewModel view = new ChunkPanelViewModelFactory().create(
            fixture(),
            new CanonicalChunk(50, 50),
            true,
            Instant.now());

        assertEquals(
            Arrays.asList("SKILLING", "BANKS", "SHOPS", "QUESTS", "COMBAT"),
            view.getCategories().stream()
                .map(ChunkPanelViewModel.CategoryView::getId)
                .collect(Collectors.toList()));

        ChunkPanelViewModel.RowView bank =
            view.category("BANKS").getRows().get(0);
        ChunkPanelViewModel.RowView quest =
            view.category("QUESTS").getRows().get(0);
        ChunkPanelViewModel.RowView combat =
            view.category("COMBAT").getRows().get(0);

        assertEquals("Available", bank.getStatusText());
        assertNull(bank.getDetail());
        assertEquals("○", quest.getStatusGlyph());
        assertNull(quest.getDetail());
        assertEquals("✕", combat.getStatusGlyph());
        assertEquals(PermissionStatus.LOCKED, combat.getStatus());
        assertTrue(view.getFreshnessLabel().startsWith("Synced"));
    }

    @Test
    public void omitsEmptyCategoriesAndCountsVisibleRows() throws Exception
    {
        ChunkPanelViewModel view = new ChunkPanelViewModelFactory().create(
            fixture(),
            new CanonicalChunk(50, 50),
            true,
            null);

        assertNull(view.category("TRAVEL"));
        assertEquals(3, view.getAllowedCount());
        assertEquals(1, view.getNotReadyCount());
        assertEquals(1, view.getLockedCount());
        assertEquals(0, view.getUnknownCount());
        assertEquals("Offline snapshot", view.getFreshnessLabel());
    }

    @Test
    public void wrongAccountProducesUnknownHeaderWithoutInventingLocks()
        throws Exception
    {
        ChunkPanelViewModel view = new ChunkPanelViewModelFactory().create(
            fixture(),
            new CanonicalChunk(50, 50),
            false,
            Instant.now());

        assertEquals(PermissionStatus.UNKNOWN, view.getEntryStatus());
        assertTrue(view.getCategories().isEmpty());
    }
}
