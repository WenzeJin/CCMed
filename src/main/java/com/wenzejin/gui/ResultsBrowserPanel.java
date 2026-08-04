package com.wenzejin.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

/**
 * Results browser panel. Lists runs in a workspace and allows drilling into test cases.
 */
public class ResultsBrowserPanel extends JPanel {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final WorkspaceSettingsPanel settingsPanel;
    private final TestCaseDetailPanel detailPanel;

    private JComboBox<String> runSelector;
    private JPanel summaryPanel;
    private JTable caseTable;
    private CaseTableModel caseTableModel;
    private JLabel resultStatusLabel;
    private JButton refreshBtn;
    private List<RunEntry> runEntries = new ArrayList<>();
    private RunEntry currentRun;
    private List<CaseEntry> currentCases = new ArrayList<>();

    /** Back-stack for source navigation. */
    private final Deque<CaseEntry> backStack = new ArrayDeque<>();
    private JButton backBtn;

    public ResultsBrowserPanel(WorkspaceSettingsPanel settingsPanel) {
        this.settingsPanel = settingsPanel;
        this.detailPanel = new TestCaseDetailPanel();

        setLayout(new BorderLayout());
        setBackground(ModernTheme.BG_CONTENT);

        // ── Top toolbar ──────────────────────────────────────────
        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setBackground(ModernTheme.BG_CARD);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.BORDER),
            ModernTheme.padding(8, 12, 8, 12)
        ));

        JPanel leftTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftTools.setBackground(ModernTheme.BG_CARD);

        leftTools.add(new JLabel("Workspace Run:"));
        runSelector = new JComboBox<>();
        runSelector.setFont(ModernTheme.FONT_UI);
        runSelector.setPreferredSize(new Dimension(300, 28));
        runSelector.addActionListener(e -> selectRun());
        leftTools.add(runSelector);

        refreshBtn = ModernTheme.secondaryButton("↻ Refresh");
        refreshBtn.setFont(refreshBtn.getFont().deriveFont(11f));
        refreshBtn.addActionListener(e -> refreshRuns());
        leftTools.add(refreshBtn);

        toolbar.add(leftTools, BorderLayout.WEST);

        // Back button for source navigation
        backBtn = ModernTheme.secondaryButton("← Back");
        backBtn.setFont(backBtn.getFont().deriveFont(11f));
        backBtn.setEnabled(false);
        backBtn.addActionListener(e -> navigateBack());
        toolbar.add(backBtn, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);

        // ── Center: summary + case table (top) / detail (bottom) ──
        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        centerSplit.setDividerLocation(350);
        centerSplit.setBorder(null);
        centerSplit.setBackground(ModernTheme.BG_CONTENT);

        // Top: summary + case table
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ModernTheme.BG_CONTENT);

        // Summary card
        summaryPanel = new JPanel();
        summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
        summaryPanel.setBackground(ModernTheme.BG_CONTENT);
        summaryPanel.setBorder(ModernTheme.padding(8, 12, 8, 12));

        resultStatusLabel = new JLabel("Select a run to view results");
        resultStatusLabel.setFont(ModernTheme.FONT_UI_BOLD.deriveFont(14f));
        resultStatusLabel.setForeground(ModernTheme.TEXT_PRIMARY);
        summaryPanel.add(resultStatusLabel);

        topPanel.add(summaryPanel, BorderLayout.NORTH);

        // Case table
        caseTableModel = new CaseTableModel();
        caseTable = new JTable(caseTableModel);
        caseTable.setFont(ModernTheme.FONT_UI);
        caseTable.setRowHeight(28);
        caseTable.getTableHeader().setFont(ModernTheme.FONT_UI_BOLD);
        caseTable.getTableHeader().setBackground(ModernTheme.BG_CONTENT);
        caseTable.setGridColor(ModernTheme.BORDER);
        caseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        caseTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) selectCase();
        });

        // Custom renderer for Passed column (col 2) and Status column (col 3)
        caseTable.getColumnModel().getColumn(2).setCellRenderer(new PassedRenderer());
        caseTable.getColumnModel().getColumn(3).setCellRenderer(new StatusRenderer());

        JScrollPane tableScroll = new JScrollPane(caseTable);
        tableScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ModernTheme.BORDER));
        topPanel.add(tableScroll, BorderLayout.CENTER);

        centerSplit.setTopComponent(topPanel);
        centerSplit.setBottomComponent(detailPanel);

        add(centerSplit, BorderLayout.CENTER);

        // Setup source navigation callback
        detailPanel.setOnSourceNavigate(sourceId -> navigateToSource(sourceId));

        // Initial refresh
        refreshRuns();
    }

    // ── Run listing ───────────────────────────────────────────────

    private void refreshRuns() {
        String root = settingsPanel.getWorkspaceRoot();
        Path rootPath = Paths.get(root);
        if (!Files.isDirectory(rootPath)) {
            runEntries.clear();
            runSelector.removeAllItems();
            runSelector.addItem("(No workspace found)");
            return;
        }

        runSelector.removeAllItems();
        runEntries.clear();

        try {
            Files.list(rootPath)
                .filter(Files::isDirectory)
                .sorted((a, b) -> {
                    // Sort by modified time descending
                    try {
                        return Long.compare(
                            Files.getLastModifiedTime(b).toMillis(),
                            Files.getLastModifiedTime(a).toMillis()
                        );
                    } catch (IOException e) { return 0; }
                })
                .forEach(dir -> {
                    // Only include dirs with config.json
                    if (Files.exists(dir.resolve("config.json"))) {
                        RunEntry entry = new RunEntry(dir);
                        runEntries.add(entry);
                        runSelector.addItem(entry.displayName());
                    }
                });
        } catch (IOException e) {
            runSelector.addItem("(Error reading workspace)");
        }

        if (runEntries.isEmpty()) {
            runSelector.addItem("(No runs found)");
        }
    }

    private void selectRun() {
        int idx = runSelector.getSelectedIndex();
        if (idx < 0 || idx >= runEntries.size()) return;

        currentRun = runEntries.get(idx);
        backStack.clear();
        backBtn.setEnabled(false);

        // Parse result.json
        summaryPanel.removeAll();
        caseTableModel.clear();

        Path resultPath = currentRun.path().resolve("result.json");
        if (Files.exists(resultPath)) {
            try {
                JsonNode result = mapper.readTree(resultPath.toFile());

                boolean passed = result.has("passed") && result.get("passed").asBoolean();
                boolean sourceFailed = result.has("source-failed") && result.get("source-failed").asBoolean();
                int firstFailure = result.has("first-failure") ? result.get("first-failure").asInt() : -1;

                resultStatusLabel.setText(passed ? "✓ PASSED" : "✗ FAILED");
                resultStatusLabel.setForeground(passed ? ModernTheme.SUCCESS : ModernTheme.DANGER);
                summaryPanel.add(resultStatusLabel);

                JLabel detail = new JLabel(String.format(
                    "Source-failed: %s  |  First failure at case: %s  |  Failed cases: %d",
                    sourceFailed ? "Yes" : "No",
                    firstFailure > 0 ? "#" + firstFailure : "N/A",
                    result.has("failed_cases") ? result.get("failed_cases").size() : 0
                ));
                detail.setFont(ModernTheme.FONT_UI.deriveFont(12f));
                detail.setForeground(ModernTheme.TEXT_SECONDARY);
                summaryPanel.add(detail);

                // Load cases
                currentCases.clear();
                loadSourceCases(currentRun.path());
                loadFailedCases(currentRun.path(), result);
                caseTableModel.setCases(currentCases);

            } catch (IOException e) {
                resultStatusLabel.setText("Failed to parse result.json: " + e.getMessage());
                resultStatusLabel.setForeground(ModernTheme.DANGER);
            }
        } else {
            resultStatusLabel.setText("Incomplete run (no result.json)");
            resultStatusLabel.setForeground(ModernTheme.WARNING);

            // Load just source cases
            currentCases.clear();
            loadSourceCases(currentRun.path());
            caseTableModel.setCases(currentCases);
        }

        summaryPanel.revalidate();
        summaryPanel.repaint();
    }

    private void loadSourceCases(Path runPath) {
        Path sourceDir = runPath.resolve("logs").resolve("source");
        if (Files.isDirectory(sourceDir)) {
            try {
                Files.list(sourceDir)
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> {
                        try { return Integer.parseInt(p.getFileName().toString()); }
                        catch (NumberFormatException e) { return 0; }
                    }))
                    .forEach(dir -> {
                        currentCases.add(new CaseEntry(dir, true));
                    });
            } catch (IOException ignored) {}
        }
    }

    private void loadFailedCases(Path runPath, JsonNode result) {
        JsonNode failedCases = result.get("failed_cases");
        if (failedCases == null) return;

        Path failedDir = runPath.resolve("logs").resolve("failed");
        for (JsonNode fc : failedCases) {
            String caseId = fc.has("case_id") ? fc.get("case_id").asText() : "?";
            Path caseDir = failedDir.resolve(caseId);
            if (Files.isDirectory(caseDir)) {
                CaseEntry entry = new CaseEntry(caseDir, false);
                // Parse source IDs
                if (fc.has("source")) {
                    for (JsonNode srcId : fc.get("source")) {
                        entry.sourceIds().add(srcId.asText());
                    }
                }
                currentCases.add(entry);
            }
        }
    }

    private void selectCase() {
        int row = caseTable.getSelectedRow();
        if (row < 0 || row >= currentCases.size()) return;

        CaseEntry entry = currentCases.get(row);
        showCase(entry);
    }

    private void showCase(CaseEntry entry) {
        detailPanel.loadCase(entry.path());
        backBtn.setEnabled(!backStack.isEmpty());
    }

    // ── Source navigation ─────────────────────────────────────────

    private void navigateToSource(String sourceId) {
        if (currentRun == null) return;
        Path sourceDir = currentRun.path().resolve("logs").resolve("source").resolve(sourceId);
        if (Files.isDirectory(sourceDir)) {
            // Push current to back stack if we're viewing something
            int currentRow = caseTable.getSelectedRow();
            if (currentRow >= 0 && currentRow < currentCases.size()) {
                backStack.push(currentCases.get(currentRow));
            }

            CaseEntry srcEntry = new CaseEntry(sourceDir, true);
            showCase(srcEntry);

            // Select in table if possible
            for (int i = 0; i < currentCases.size(); i++) {
                if (currentCases.get(i).path().equals(sourceDir)) {
                    caseTable.setRowSelectionInterval(i, i);
                    caseTable.scrollRectToVisible(caseTable.getCellRect(i, 0, true));
                    break;
                }
            }
        }
    }

    private void navigateBack() {
        if (!backStack.isEmpty()) {
            CaseEntry prev = backStack.pop();
            showCase(prev);

            // Select in table
            for (int i = 0; i < currentCases.size(); i++) {
                if (currentCases.get(i).path().equals(prev.path())) {
                    caseTable.setRowSelectionInterval(i, i);
                    break;
                }
            }

            backBtn.setEnabled(!backStack.isEmpty());
        }
    }

    // ── Data models ───────────────────────────────────────────────

    private record RunEntry(Path path) {
        String displayName() {
            // Show dir name + pass/fail indicator
            String name = path.getFileName().toString();
            Path resultPath = path.resolve("result.json");
            if (Files.exists(resultPath)) {
                try {
                    JsonNode result = mapper.readTree(resultPath.toFile());
                    boolean passed = result.has("passed") && result.get("passed").asBoolean();
                    return (passed ? "✓ " : "✗ ") + name;
                } catch (IOException ignored) {}
            }
            return "? " + name;
        }
    }

    private record CaseEntry(Path path, boolean isSource) {
        List<String> sourceIds() {
            // Try to read from info.json
            Path infoPath = path.resolve("info.json");
            if (Files.exists(infoPath)) {
                try {
                    JsonNode info = mapper.readTree(infoPath.toFile());
                    if (info.has("source")) {
                        List<String> ids = new ArrayList<>();
                        for (JsonNode n : info.get("source")) ids.add(n.asText());
                        return ids;
                    }
                } catch (IOException ignored) {}
            }
            return new ArrayList<>();
        }
    }

    private static class CaseTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"ID", "Type", "Passed", "Status", "Duration", "Source IDs"};
        private List<CaseEntry> cases = new ArrayList<>();

        void setCases(List<CaseEntry> cases) {
            this.cases = cases;
            fireTableDataChanged();
        }

        void clear() {
            cases.clear();
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return cases.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            CaseEntry entry = cases.get(row);
            Path infoPath = entry.path().resolve("info.json");
            JsonNode info = null;
            if (Files.exists(infoPath)) {
                try { info = mapper.readTree(infoPath.toFile()); }
                catch (IOException ignored) {}
            }

            return switch (col) {
                case 0 -> info != null && info.has("id") ? info.get("id").asText() : entry.path().getFileName().toString();
                case 1 -> entry.isSource() ? "Source" : "Follow-up";
                case 2 -> {
                    if (entry.isSource()) yield "-";
                    if (info != null && info.has("passed")) {
                        yield info.get("passed").asBoolean() ? "✓ Pass" : "✗ Fail";
                    }
                    yield "?";
                }
                case 3 -> info != null && info.has("status") ? info.get("status").asText() : "?";
                case 4 -> {
                    if (info != null && info.has("duration")) {
                        long ms = info.get("duration").asLong();
                        yield formatDuration(ms);
                    }
                    yield "?";
                }
                case 5 -> {
                    if (!entry.isSource()) {
                        List<String> ids = entry.sourceIds();
                        if (!ids.isEmpty()) yield String.join(", ", ids);
                        yield "-";
                    }
                    yield "-";
                }
                default -> "";
            };
        }

        private String formatDuration(long ms) {
            if (ms < 1000) return ms + "ms";
            if (ms < 60000) return String.format("%.1fs", ms / 1000.0);
            long min = ms / 60000;
            long sec = (ms % 60000) / 1000;
            return String.format("%dm%ds", min, sec);
        }
    }

    private static class PassedRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            String text = value != null ? value.toString() : "";
            if (!isSelected) {
                if (text.startsWith("✓")) c.setForeground(ModernTheme.SUCCESS);
                else if (text.startsWith("✗")) c.setForeground(ModernTheme.DANGER);
                else c.setForeground(ModernTheme.TEXT_MUTED);
            }
            c.setFont(ModernTheme.FONT_UI);
            return c;
        }
    }

    private static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            String status = value != null ? value.toString() : "";
            if (!isSelected) {
                switch (status) {
                    case "SUCCESS" -> c.setForeground(ModernTheme.SUCCESS);
                    case "CRASHED", "TIMEOUT" -> c.setForeground(ModernTheme.DANGER);
                    case "INVALID_OUTPUT" -> c.setForeground(ModernTheme.WARNING);
                    default -> c.setForeground(ModernTheme.TEXT_PRIMARY);
                }
            }
            c.setFont(ModernTheme.FONT_UI);
            return c;
        }
    }

    public void refresh() { refreshRuns(); }
}
