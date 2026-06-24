package com.fatelocked;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Side panel for the Fate Locked plugin: live run stats, the player's current
 * chunk + status, an Allowed / Forbidden / Unknown content breakdown, and a
 * paste-box for loading a bundle without touching the config file path.
 */
class FateLockedPanel extends PluginPanel
{
    private static final Color GREEN = new Color(52, 211, 153);
    private static final Color RED = new Color(248, 113, 113);
    private static final Color GOLD = new Color(245, 158, 11);

    private final JLabel profileVal = value();
    private final JLabel accountVal = value();
    private final JLabel runIdVal = value();
    private final JLabel keysVal = value();
    private final JLabel fateVal = value();
    private final JLabel buffVal = value();
    private final JLabel goalVal = value();

    private final JLabel chunkVal = value();
    private final JLabel regionVal = value();
    private final JLabel statusVal = value();

    private final JLabel allowedHead = section("ALLOWED (0)");
    private final JLabel forbiddenHead = section("FORBIDDEN (0)");
    private final JLabel unknownHead = section("UNKNOWN");
    private final JTextArea allowedList = listArea();
    private final JTextArea forbiddenList = listArea();
    private final JLabel unknownNote = value();

    private final JTextArea pasteArea = new JTextArea(6, 10);

    private Consumer<String> onImport = j -> {};
    private Runnable onReload = () -> {};

    @Inject
    FateLockedPanel()
    {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBackground(ColorScheme.DARK_GRAY_COLOR);

        col.add(title("FATE LOCKED IRONMAN"));
        col.add(Box.createVerticalStrut(10));

        col.add(section("RUN"));
        col.add(stats(new String[]{ "Profile", "Account", "Run ID", "Keys", "Fate", "Buff", "Goal" },
            new JLabel[]{ profileVal, accountVal, runIdVal, keysVal, fateVal, buffVal, goalVal }));
        col.add(Box.createVerticalStrut(12));

        col.add(section("CURRENT LOCATION"));
        col.add(stats(new String[]{ "Chunk", "Area", "Status" },
            new JLabel[]{ chunkVal, regionVal, statusVal }));
        col.add(Box.createVerticalStrut(12));

        col.add(allowedHead);
        col.add(wrap(allowedList));
        col.add(Box.createVerticalStrut(8));
        col.add(forbiddenHead);
        col.add(wrap(forbiddenList));
        col.add(Box.createVerticalStrut(8));
        col.add(unknownHead);
        unknownNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(unknownNote);
        col.add(Box.createVerticalStrut(12));

        col.add(section("LOAD BUNDLE"));
        col.add(Box.createVerticalStrut(4));

        pasteArea.setLineWrap(true);
        pasteArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pasteArea.setForeground(Color.LIGHT_GRAY);
        pasteArea.setCaretColor(Color.LIGHT_GRAY);
        pasteArea.setBorder(new EmptyBorder(4, 4, 4, 4));
        pasteArea.setToolTipText("Paste the JSON from the web app's RL export button (it's already on your clipboard)");
        JScrollPane scroll = new JScrollPane(pasteArea);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        col.add(scroll);
        col.add(Box.createVerticalStrut(6));

        JButton importBtn = new JButton("Import pasted JSON");
        importBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        importBtn.addActionListener(e -> {
            String txt = pasteArea.getText().trim();
            if (!txt.isEmpty()) onImport.accept(txt);
        });
        col.add(importBtn);
        col.add(Box.createVerticalStrut(4));

        JButton reloadBtn = new JButton("Reload from file");
        reloadBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        reloadBtn.addActionListener(e -> onReload.run());
        col.add(reloadBtn);

        add(col, BorderLayout.NORTH);
    }

    void setCallbacks(Consumer<String> onImport, Runnable onReload)
    {
        this.onImport = onImport;
        this.onReload = onReload;
    }

    /** Push fresh state into the panel. Safe to call from the client thread. */
    void update(FateLockedBundle bundle, CanonicalChunk current, String label, boolean unlocked)
    {
        // ── compute the Allowed / Forbidden / Unknown breakdown ────────────────
        // Allowed: always-free areas + everything the player has unlocked.
        // Forbidden: authored sub-areas (and continents) that are still locked.
        // Unknown: chunks not claimed by any named sub-area.
        Set<String> allowed = new TreeSet<>(bundle.getAlwaysUnlocked());
        allowed.addAll(bundle.getUnlockedRegions());

        Set<String> forbidden = new TreeSet<>();
        for (Map.Entry<String, List<String>> group : bundle.getRegionGroups().entrySet())
        {
            for (String child : group.getValue())
            {
                if (!bundle.isUnlocked(child)) forbidden.add(child);
            }
        }
        // v1 bundles have no hierarchy — fall back to authored continents.
        if (bundle.getRegionGroups().isEmpty())
        {
            for (String region : bundle.getRegionChunks().keySet())
            {
                if (!bundle.isUnlocked(region)) forbidden.add(region);
            }
        }

        int totalChunks = 0;
        for (Set<CanonicalChunk> set : bundle.getRegionChunks().values()) totalChunks += set.size();
        int namedChunks = 0;
        for (Set<CanonicalChunk> set : bundle.getSubAreaChunks().values()) namedChunks += set.size();
        final int unnamed = Math.max(0, totalChunks - namedChunks);

        FateLockedBundle.RunState st = bundle.getState();
        List<String> goals = st == null || st.getPinnedGoals() == null
            ? Collections.<String>emptyList() : st.getPinnedGoals();

        SwingUtilities.invokeLater(() -> {
            profileVal.setText(orDash(bundle.getProfileName()));
            runIdVal.setText(orDash(bundle.getRunId()));
            if (st != null)
            {
                String bound = st.getLinkedAccount();
                accountVal.setText(bound == null || bound.trim().isEmpty() ? "—" : bound);
                keysVal.setText(st.getKeys() + " · O " + st.getSpecialKeys() + " · C " + st.getChaosKeys());
                keysVal.setForeground(GOLD);
                fateVal.setText(String.valueOf(st.getFatePoints()));
                buffVal.setText(st.getActiveBuff() == null ? "—" : st.getActiveBuff());
                goalVal.setText(goals.isEmpty() ? "—" : goals.get(0));
            }
            else
            {
                accountVal.setText("—");
                keysVal.setText("—");
                fateVal.setText("—");
                buffVal.setText("—");
                goalVal.setText("—");
            }

            chunkVal.setText(current == null ? "—"
                : "(" + current.getCx() + ", " + current.getCy() + ")");
            regionVal.setText(label == null ? "Unauthored" : label);

            if (label == null)
            {
                statusVal.setText("—");
                statusVal.setForeground(Color.GRAY);
            }
            else if (unlocked)
            {
                statusVal.setText("Unlocked");
                statusVal.setForeground(GREEN);
            }
            else
            {
                statusVal.setText("LOCKED");
                statusVal.setForeground(RED);
            }

            allowedHead.setText("ALLOWED (" + allowed.size() + ")");
            forbiddenHead.setText("FORBIDDEN (" + forbidden.size() + ")");
            allowedList.setText(allowed.isEmpty() ? "—" : String.join(", ", allowed));
            allowedList.setForeground(GREEN);
            forbiddenList.setText(forbidden.isEmpty() ? "—" : String.join(", ", forbidden));
            forbiddenList.setForeground(RED);
            unknownNote.setText(unnamed + " map chunks in unnamed terrain");
            unknownNote.setForeground(Color.GRAY);
        });
    }

    /** Show a one-off message in the run-id row (e.g. import success/failure). */
    void flashStatus(String message, boolean ok)
    {
        SwingUtilities.invokeLater(() -> {
            runIdVal.setText(message);
            runIdVal.setForeground(ok ? GREEN : RED);
        });
    }

    // ---- UI builders ---------------------------------------------------------

    private static JLabel title(String text)
    {
        JLabel l = new JLabel(text);
        l.setForeground(GOLD);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel section(String text)
    {
        JLabel l = new JLabel(text);
        l.setForeground(Color.GRAY);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 10f));
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel value()
    {
        JLabel l = new JLabel("—");
        l.setForeground(Color.WHITE);
        return l;
    }

    private static JTextArea listArea()
    {
        JTextArea a = new JTextArea(2, 10);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(false);
        a.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        a.setBorder(new EmptyBorder(4, 4, 4, 4));
        a.setFont(a.getFont().deriveFont(11f));
        return a;
    }

    private static JScrollPane wrap(JTextArea area)
    {
        JScrollPane s = new JScrollPane(area,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        s.setBorder(null);
        return s;
    }

    private static JPanel stats(String[] labels, JLabel[] values)
    {
        JPanel grid = new JPanel(new GridLayout(0, 2, 6, 3));
        grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < labels.length; i++)
        {
            JLabel key = new JLabel(labels[i]);
            key.setForeground(Color.LIGHT_GRAY);
            grid.add(key);
            grid.add(values[i]);
        }
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
        return grid;
    }

    private static String orDash(String s)
    {
        return (s == null || s.isEmpty()) ? "—" : s;
    }
}
