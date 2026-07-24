package com.fatelocked;

import com.fatelocked.panel.ChunkPanelViewModel;
import com.fatelocked.rules.PermissionStatus;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.swing.BorderFactory;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Narrow category-first side panel with compact rule rows. */
class FateLockedPanel extends PluginPanel
{
    static final String TRACKER_URL = "https://nubles.github.io/OSRS-Fate-Locked/";
    private static final Color GREEN = new Color(16, 185, 129);
    private static final Color AMBER = new Color(245, 158, 11);
    private static final Color RED = new Color(239, 68, 68);
    private static final Color GRAY = new Color(156, 163, 175);
    private static final Color SURFACE = new Color(35, 39, 46);
    private static final Color SEPARATOR = new Color(58, 63, 72);

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
    private final JLabel importVal = value();
    private final JPanel chunkBody = column();
    private final JTextArea pasteArea = new JTextArea(6, 10);

    private String rollInboxUrl = TRACKER_URL + "?open=roll-inbox&code=";
    private JPanel bundleBody;
    private JButton bundleHeader;
    private Consumer<String> onImport = json -> {};
    private Runnable onReload = () -> {};

    @Inject
    FateLockedPanel()
    {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(260, 0));

        JPanel col = column();
        col.add(title("FATE LOCKED IRONMAN"));
        col.add(Box.createVerticalStrut(6));

        JButton trackerBtn = new JButton("Open web tracker");
        fullWidth(trackerBtn);
        trackerBtn.addActionListener(e -> LinkBrowser.browse(TRACKER_URL));
        col.add(trackerBtn);
        col.add(Box.createVerticalStrut(10));

        col.add(section("CURRENT CHUNK"));
        chunkBody.add(emptyChunk());
        col.add(chunkBody);
        col.add(Box.createVerticalStrut(12));

        col.add(section("ROLL INBOX"));
        col.add(stats(
            new String[]{"Queued", "Needs review", "Warnings", "Last sync"},
            new JLabel[]{queuedVal, reviewVal, warningsVal, lastSyncVal}));
        JButton inboxBtn = new JButton("Open Roll Inbox");
        fullWidth(inboxBtn);
        inboxBtn.setToolTipText(
            "Open detected events in the tracker; RuneLite never rolls for you");
        inboxBtn.addActionListener(e -> LinkBrowser.browse(rollInboxUrl));
        col.add(Box.createVerticalStrut(6));
        col.add(inboxBtn);
        col.add(Box.createVerticalStrut(12));

        col.add(section("RUN"));
        col.add(stats(
            new String[]{"Profile", "Account", "Run ID", "Keys", "Fate", "Buff", "Goal", "Import"},
            new JLabel[]{profileVal, accountVal, runIdVal, keysVal, fateVal, buffVal, goalVal, importVal}));
        col.add(Box.createVerticalStrut(12));

        bundleBody = column();
        bundleHeader = collapsibleHeader("LOAD BUNDLE", bundleBody, true);
        col.add(bundleHeader);
        col.add(bundleBody);
        buildImportControls();
        add(col, BorderLayout.NORTH);
    }

    private void buildImportControls()
    {
        bundleBody.add(Box.createVerticalStrut(4));
        JButton clipboardBtn = new JButton("Import from clipboard");
        fullWidth(clipboardBtn);
        clipboardBtn.setToolTipText("Click RuneLite in the tracker, then click here");
        clipboardBtn.addActionListener(e -> importFromClipboard());
        bundleBody.add(clipboardBtn);
        bundleBody.add(Box.createVerticalStrut(6));
        bundleBody.add(section("…OR PASTE JSON"));

        pasteArea.setLineWrap(true);
        pasteArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pasteArea.setForeground(Color.LIGHT_GRAY);
        pasteArea.setCaretColor(Color.LIGHT_GRAY);
        pasteArea.setBorder(new EmptyBorder(4, 4, 4, 4));
        JScrollPane scroll = new JScrollPane(pasteArea);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        bundleBody.add(scroll);
        bundleBody.add(Box.createVerticalStrut(6));

        JButton importBtn = new JButton("Import pasted JSON");
        fullWidth(importBtn);
        importBtn.addActionListener(e -> {
            String text = pasteArea.getText().trim();
            if (!text.isEmpty()) onImport.accept(text);
        });
        bundleBody.add(importBtn);
        bundleBody.add(Box.createVerticalStrut(4));

        JButton reloadBtn = new JButton("Reload from file");
        fullWidth(reloadBtn);
        reloadBtn.addActionListener(e -> onReload.run());
        bundleBody.add(reloadBtn);
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
                lastSyncVal.setForeground(GRAY);
            }
            else if (lastSync == null)
            {
                lastSyncVal.setText("Waiting…");
                lastSyncVal.setForeground(AMBER);
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

    private void importFromClipboard()
    {
        try
        {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard()
                .getData(DataFlavor.stringFlavor);
            String text = data == null ? "" : data.toString().trim();
            if (text.isEmpty())
            {
                flashStatus("clipboard empty", false);
                return;
            }
            pasteArea.setText(text);
            onImport.accept(text);
        }
        catch (Exception ex)
        {
            flashStatus("couldn't read clipboard", false);
        }
    }

    void update(FateLockedBundle bundle, ChunkPanelViewModel view)
    {
        FateLockedBundle.RunState state = bundle.getState();
        List<String> goals = state == null || state.getPinnedGoals() == null
            ? Collections.emptyList() : state.getPinnedGoals();
        SwingUtilities.invokeLater(() -> {
            profileVal.setText(orDash(bundle.getProfileName()));
            runIdVal.setText(orDash(bundle.getRunId()));
            String manifestAccount = bundle.getRules() == null
                ? null : bundle.getRules().getAccount();
            accountVal.setText(orDash(manifestAccount));
            if (state != null)
            {
                if (manifestAccount == null) accountVal.setText(orDash(state.getLinkedAccount()));
                keysVal.setText(state.getKeys() + " · O " + state.getSpecialKeys()
                    + " · C " + state.getChaosKeys());
                keysVal.setForeground(AMBER);
                fateVal.setText(String.valueOf(state.getFatePoints()));
                buffVal.setText(orDash(state.getActiveBuff()));
                goalVal.setText(goals.isEmpty() ? "—" : goals.get(0));
            }
            else
            {
                keysVal.setText("—");
                fateVal.setText("—");
                buffVal.setText("—");
                goalVal.setText("—");
            }
            renderChunk(view);
        });
    }

    void renderChunkForTest(ChunkPanelViewModel view)
    {
        renderChunk(view);
    }

    private void renderChunk(ChunkPanelViewModel view)
    {
        chunkBody.removeAll();
        if (view == null)
        {
            chunkBody.add(emptyChunk());
            chunkBody.revalidate();
            return;
        }

        JPanel header = card();
        JLabel area = new JLabel(view.getName());
        area.setForeground(Color.WHITE);
        area.setFont(area.getFont().deriveFont(Font.BOLD, 13f));
        header.add(area);
        JLabel meta = new JLabel(
            (view.getRegion() == null ? "Unknown region" : view.getRegion())
                + "  ·  " + view.getCoordinates());
        meta.setForeground(GRAY);
        meta.setFont(meta.getFont().deriveFont(10f));
        header.add(meta);
        JLabel entry = new JLabel(
            statusText(view.getEntryStatus()) + "  ·  " + view.getFreshnessLabel());
        entry.setForeground(statusColor(view.getEntryStatus()));
        entry.setFont(entry.getFont().deriveFont(Font.BOLD, 10f));
        header.add(entry);
        chunkBody.add(header);
        chunkBody.add(Box.createVerticalStrut(6));

        JPanel counts = new JPanel(new GridLayout(1, 3, 4, 0));
        counts.setOpaque(false);
        counts.setAlignmentX(Component.LEFT_ALIGNMENT);
        counts.add(chip("Can do " + view.getAllowedCount(), GREEN));
        counts.add(chip("Not ready " + view.getNotReadyCount(), AMBER));
        counts.add(chip("Locked " + view.getLockedCount(), RED));
        counts.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        chunkBody.add(counts);

        for (ChunkPanelViewModel.CategoryView category : view.getCategories())
        {
            chunkBody.add(Box.createVerticalStrut(9));
            chunkBody.add(section(category.getTitle()));
            for (ChunkPanelViewModel.RowView row : category.getRows())
            {
                chunkBody.add(permissionRow(row));
            }
        }
        chunkBody.revalidate();
        chunkBody.repaint();
    }

    private static JPanel permissionRow(ChunkPanelViewModel.RowView row)
    {
        JPanel panel = new JPanel(new BorderLayout(5, 1));
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, SEPARATOR),
            new EmptyBorder(5, 6, 5, 6)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        String shown = row.getName().length() > 28
            ? row.getName().substring(0, 27) + "…" : row.getName();
        JLabel name = new JLabel(row.getStatusGlyph() + " " + shown);
        name.setForeground(statusColor(row.getStatus()));
        name.setToolTipText(row.getDetail() == null
            ? row.getName() : row.getName() + " — " + row.getDetail());
        panel.add(name, BorderLayout.CENTER);

        if (row.getStatusText() != null)
        {
            JLabel status = new JLabel(row.getStatusText());
            status.setForeground(statusColor(row.getStatus()));
            status.setFont(status.getFont().deriveFont(10f));
            panel.add(status, BorderLayout.EAST);
        }
        if (row.getDetail() != null)
        {
            JLabel detail = new JLabel(row.getDetail());
            detail.setForeground(GRAY);
            detail.setFont(detail.getFont().deriveFont(9f));
            panel.add(detail, BorderLayout.SOUTH);
        }
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE,
            row.getDetail() == null ? 28 : 40));
        return panel;
    }

    private static JPanel emptyChunk()
    {
        JPanel panel = card();
        JLabel label = new JLabel("Enter the game to see this chunk");
        label.setForeground(GRAY);
        panel.add(label);
        return panel;
    }

    private static JPanel card()
    {
        JPanel panel = column();
        panel.setBackground(SURFACE);
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        return panel;
    }

    private static JLabel chip(String text, Color color)
    {
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setOpaque(true);
        label.setBackground(SURFACE);
        label.setForeground(color);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 9f));
        label.setBorder(new EmptyBorder(4, 2, 4, 2));
        return label;
    }

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

    private static Color statusColor(PermissionStatus status)
    {
        if (status == PermissionStatus.ALLOWED) return GREEN;
        if (status == PermissionStatus.NOT_READY) return AMBER;
        if (status == PermissionStatus.LOCKED) return RED;
        return GRAY;
    }

    private static String statusText(PermissionStatus status)
    {
        if (status == PermissionStatus.ALLOWED) return "Available";
        if (status == PermissionStatus.NOT_READY) return "Not ready";
        if (status == PermissionStatus.LOCKED) return "Locked";
        return "Unknown";
    }

    private static JLabel title(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(AMBER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel section(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(GRAY);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        label.setBorder(new EmptyBorder(0, 0, 4, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JPanel column()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static String headerText(String label, boolean open)
    {
        return (open ? "▾ " : "▸ ") + label;
    }

    private static JButton collapsibleHeader(String label, JPanel body, boolean open)
    {
        JButton header = new JButton(headerText(label, open));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 10f));
        header.setForeground(GRAY);
        header.setContentAreaFilled(false);
        header.setBorder(new EmptyBorder(0, 0, 4, 0));
        header.setFocusPainted(false);
        header.setHorizontalAlignment(JButton.LEFT);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setVisible(open);
        header.addActionListener(event -> {
            boolean nowOpen = !body.isVisible();
            body.setVisible(nowOpen);
            header.setText(headerText(label, nowOpen));
            body.revalidate();
        });
        return header;
    }

    private static void fullWidth(JButton button)
    {
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(
            Integer.MAX_VALUE, button.getPreferredSize().height));
    }

    private static JLabel value()
    {
        JLabel label = new JLabel("—");
        label.setForeground(Color.WHITE);
        return label;
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
        grid.setMaximumSize(new Dimension(
            Integer.MAX_VALUE, grid.getPreferredSize().height));
        return grid;
    }

    private static String orDash(String value)
    {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }
}
