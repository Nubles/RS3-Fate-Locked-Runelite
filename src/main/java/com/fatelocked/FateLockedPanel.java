package com.fatelocked;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

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
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Side panel for the Fate Locked plugin: live run stats, the player's current
 * chunk + status, an Allowed / Forbidden / Unknown content breakdown, and a
 * paste-box for loading a bundle without touching the config file path.
 */
class FateLockedPanel extends PluginPanel
{
    static final String TRACKER_URL = "https://nubles.github.io/OSRS-Fate-Locked/";
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
    private final JLabel queuedVal = value();
    private final JLabel reviewVal = value();
    private final JLabel warningsVal = value();
    private final JLabel lastSyncVal = value();
    private String rollInboxUrl = TRACKER_URL + "?open=roll-inbox&code=";

    private final JLabel chunkVal = value();
    private final JLabel regionVal = value();
    private final JLabel statusVal = value();
    private final JTextArea hereList = listArea();

    private final JLabel allowedHead = section("ALLOWED (0)");
    private final JLabel forbiddenHead = section("FORBIDDEN (0)");
    private final JLabel unknownHead = section("UNKNOWN");
    private final JTextArea allowedList = listArea();
    private final JTextArea forbiddenList = listArea();
    private final JLabel unknownNote = value();

    private final JTextArea pasteArea = new JTextArea(6, 10);
    /** Last import outcome ("imported 12 regions" / "clipboard empty"), colored by result. */
    private final JLabel importVal = value();
    /** The LOAD BUNDLE body — auto-collapsed after a successful import. */
    private JPanel bundleBody;
    private JButton bundleHeader;

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
        col.add(Box.createVerticalStrut(6));

        JButton trackerBtn = new JButton("Open web tracker");
        fullWidth(trackerBtn);
        trackerBtn.setToolTipText("Open the Fate Locked Ironman tracker in your browser");
        trackerBtn.addActionListener(e -> LinkBrowser.browse(TRACKER_URL));
        col.add(trackerBtn);
        col.add(Box.createVerticalStrut(10));

        col.add(section("ROLL INBOX"));
        col.add(stats(new String[]{ "Queued", "Needs review", "Warnings", "Last sync" },
            new JLabel[]{ queuedVal, reviewVal, warningsVal, lastSyncVal }));
        JButton inboxBtn = new JButton("Open Roll Inbox");
        fullWidth(inboxBtn);
        inboxBtn.setToolTipText("Open detected events in the web tracker; RuneLite never rolls for you");
        inboxBtn.addActionListener(e -> LinkBrowser.browse(rollInboxUrl));
        col.add(Box.createVerticalStrut(6));
        col.add(inboxBtn);
        col.add(Box.createVerticalStrut(12));

        col.add(section("RUN"));
        col.add(stats(new String[]{ "Profile", "Account", "Run ID", "Keys", "Fate", "Buff", "Goal", "Import" },
            new JLabel[]{ profileVal, accountVal, runIdVal, keysVal, fateVal, buffVal, goalVal, importVal }));
        col.add(Box.createVerticalStrut(12));

        col.add(section("CURRENT LOCATION"));
        col.add(stats(new String[]{ "Chunk", "Area", "Status" },
            new JLabel[]{ chunkVal, regionVal, statusVal }));
        col.add(Box.createVerticalStrut(4));
        col.add(section("IN THIS CHUNK"));
        col.add(wrap(hereList));
        col.add(Box.createVerticalStrut(12));

        JPanel breakdownBody = column();
        breakdownBody.add(allowedHead);
        breakdownBody.add(wrap(allowedList));
        breakdownBody.add(Box.createVerticalStrut(8));
        breakdownBody.add(forbiddenHead);
        breakdownBody.add(wrap(forbiddenList));
        breakdownBody.add(Box.createVerticalStrut(8));
        breakdownBody.add(unknownHead);
        unknownNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        breakdownBody.add(unknownNote);
        col.add(collapsibleHeader("AREA BREAKDOWN", breakdownBody, true));
        col.add(breakdownBody);
        col.add(Box.createVerticalStrut(12));

        bundleBody = column();
        bundleHeader = collapsibleHeader("LOAD BUNDLE", bundleBody, true);
        col.add(bundleHeader);
        col.add(bundleBody);

        bundleBody.add(Box.createVerticalStrut(4));

        // Primary path: the web app's RL button puts the bundle on the clipboard,
        // so a single click here imports it — no file, no pasting.
        JButton clipboardBtn = new JButton("Import from clipboard");
        fullWidth(clipboardBtn);
        clipboardBtn.setToolTipText("Click RL in the web app, then click here");
        clipboardBtn.addActionListener(e -> importFromClipboard());
        bundleBody.add(clipboardBtn);
        bundleBody.add(Box.createVerticalStrut(6));

        JLabel pasteHint = section("…OR PASTE JSON");
        bundleBody.add(pasteHint);

        pasteArea.setLineWrap(true);
        pasteArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pasteArea.setForeground(Color.LIGHT_GRAY);
        pasteArea.setCaretColor(Color.LIGHT_GRAY);
        pasteArea.setBorder(new EmptyBorder(4, 4, 4, 4));
        pasteArea.setToolTipText("Paste the JSON from the web app's RL export button (it's already on your clipboard)");
        JScrollPane scroll = new JScrollPane(pasteArea);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        bundleBody.add(scroll);
        bundleBody.add(Box.createVerticalStrut(6));

        JButton importBtn = new JButton("Import pasted JSON");
        fullWidth(importBtn);
        importBtn.addActionListener(e -> {
            String txt = pasteArea.getText().trim();
            if (!txt.isEmpty()) onImport.accept(txt);
        });
        bundleBody.add(importBtn);
        bundleBody.add(Box.createVerticalStrut(4));

        JButton reloadBtn = new JButton("Reload from file");
        fullWidth(reloadBtn);
        reloadBtn.addActionListener(e -> onReload.run());
        bundleBody.add(reloadBtn);

        add(col, BorderLayout.NORTH);
    }

    void setCallbacks(Consumer<String> onImport, Runnable onReload)
    {
        this.onImport = onImport;
        this.onReload = onReload;
    }

    void setRollInboxLink(String trackerUrl, String code)
    {
        rollInboxUrl = rollInboxUrl(trackerUrl, code);
    }

    static String rollInboxUrl(String trackerUrl, String code)
    {
        String base = trackerUrl == null || trackerUrl.trim().isEmpty()
            ? TRACKER_URL : trackerUrl.trim();
        try
        {
            return base + "?open=roll-inbox&code=" + URLEncoder.encode(
                code == null ? "" : code,
                StandardCharsets.UTF_8.name());
        }
        catch (java.io.UnsupportedEncodingException impossible)
        {
            throw new IllegalStateException(impossible);
        }
    }

    void updateSyncHealth(
        int pending,
        int readyHint,
        int warnings,
        Instant lastSync,
        boolean offline)
    {
        SwingUtilities.invokeLater(() -> {
            queuedVal.setText(String.valueOf(Math.max(0, pending)));
            reviewVal.setText(String.valueOf(Math.max(0, readyHint)));
            warningsVal.setText(warnings <= 0 ? "None" : warnings + " active");
            warningsVal.setForeground(warnings <= 0 ? GREEN : RED);
            if (offline)
            {
                lastSyncVal.setText("Offline");
                lastSyncVal.setForeground(Color.GRAY);
            }
            else if (lastSync == null)
            {
                lastSyncVal.setText("Waiting...");
                lastSyncVal.setForeground(GOLD);
            }
            else
            {
                lastSyncVal.setText(DateTimeFormatter
                    .ofPattern("HH:mm:ss 'UTC'")
                    .withZone(ZoneOffset.UTC)
                    .format(lastSync));
                lastSyncVal.setForeground(GREEN);
            }
        });
    }

    String queuedTextForTest() { return queuedVal.getText(); }
    String reviewTextForTest() { return reviewVal.getText(); }
    String warningTextForTest() { return warningsVal.getText(); }
    String lastSyncTextForTest() { return lastSyncVal.getText(); }

    /** Read the system clipboard and import it as a bundle (local-only, no network). */
    private void importFromClipboard()
    {
        try
        {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard()
                .getData(DataFlavor.stringFlavor);
            String txt = data == null ? "" : data.toString().trim();
            if (txt.isEmpty())
            {
                flashStatus("clipboard empty", false);
                return;
            }
            pasteArea.setText(txt);
            onImport.accept(txt);
        }
        catch (Exception ex)
        {
            flashStatus("couldn't read clipboard", false);
        }
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
        List<String> here = current == null
            ? Collections.<String>emptyList() : bundle.contentAt(current);

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

            hereList.setText(here.isEmpty() ? "—" : String.join("\n", here));
            hereList.setForeground(Color.LIGHT_GRAY);
        });
    }

    /**
     * Show the latest import outcome in its own row (the old behavior hijacked
     * the Run ID row and left its color stuck red/green forever). A successful
     * import also collapses the LOAD BUNDLE section — once a bundle is in,
     * the paste box is dead weight until the player wants it again.
     */
    void flashStatus(String message, boolean ok)
    {
        SwingUtilities.invokeLater(() -> {
            importVal.setText(message);
            importVal.setForeground(ok ? GREEN : RED);
            if (ok && bundleBody.isVisible())
            {
                bundleBody.setVisible(false);
                bundleHeader.setText(headerText("LOAD BUNDLE", false));
            }
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

    /** Vertical sub-panel matching the main column's look. */
    private static JPanel column()
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static String headerText(String label, boolean open)
    {
        return (open ? "▾ " : "▸ ") + label;
    }

    /** Section header that toggles a body panel's visibility. */
    private static JButton collapsibleHeader(String label, JPanel body, boolean open)
    {
        JButton h = new JButton(headerText(label, open));
        h.setFont(h.getFont().deriveFont(Font.BOLD, 10f));
        h.setForeground(Color.GRAY);
        h.setContentAreaFilled(false);
        h.setBorder(new EmptyBorder(0, 0, 4, 0));
        h.setFocusPainted(false);
        h.setHorizontalAlignment(JButton.LEFT);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        h.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        body.setVisible(open);
        h.addActionListener(e -> {
            boolean nowOpen = !body.isVisible();
            body.setVisible(nowOpen);
            h.setText(headerText(label, nowOpen));
            body.revalidate();
        });
        return h;
    }

    private static void fullWidth(JButton b)
    {
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
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
