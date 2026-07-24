package com.fatelocked;

import com.fatelocked.panel.ChunkPanelViewModel;
import com.fatelocked.panel.ChunkPanelViewModelFactory;
import com.google.gson.Gson;
import org.junit.Test;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChunkPanelRenderingTest
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
    public void rendersCompactCategoryFirstContent() throws Exception
    {
        FateLockedBundle bundle = fixture();
        ChunkPanelViewModel view = new ChunkPanelViewModelFactory().create(
            bundle, new CanonicalChunk(50, 50), true, Instant.now());
        FateLockedPanel panel = new FateLockedPanel();
        panel.renderChunkForTest(view);
        String text = String.join("\n", labels(panel));

        assertTrue(text.contains("Lumbridge"));
        assertTrue(text.contains("Misthalin"));
        assertTrue(text.contains("50, 50"));
        assertTrue(text.contains("Synced"));
        assertTrue(text.contains("Can do 3"));
        assertTrue(text.contains("Not ready 1"));
        assertTrue(text.contains("Locked 1"));
        assertTrue(text.contains("Banks"));
        assertTrue(text.contains("Lumbridge Castle"));
        assertTrue(text.contains("○ Cook's Assistant"));
        assertTrue(text.contains("✕ Goblin"));
        assertFalse(text.contains("Travel"));
        assertFalse(text.contains("Unlock from"));
        assertFalse(text.contains("Spend Keys"));
        assertFalse(text.contains("This individual"));
        assertFalse(text.contains("FORBIDDEN"));
    }

    private static List<String> labels(Component component)
    {
        List<String> out = new ArrayList<>();
        if (component instanceof JLabel)
        {
            out.add(((JLabel) component).getText());
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                out.addAll(labels(child));
            }
        }
        return out;
    }
}
