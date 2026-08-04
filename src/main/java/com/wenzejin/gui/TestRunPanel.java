package com.wenzejin.gui;

import com.wenzejin.core.Config;
import com.wenzejin.core.Core;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Test execution panel — clean progress bar on top, config summary below.
 */
public class TestRunPanel extends JPanel {

    private JProgressBar progressBar;
    private JLabel phaseLabel;
    private JLabel detailLabel;
    private JLabel percentLabel;
    private JLabel elapsedLabel;
    private JButton startBtn;
    private JButton stopBtn;
    private JButton openResultsBtn;
    private JPanel configSummaryCard;

    private Instant startTime;
    private Timer elapsedTimer;
    private Timer stopFlashTimer;

    private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(false);
    private SwingWorker<Void, GuiProgressListener.ProgressSnapshot> worker;
    private GuiProgressListener progressListener;
    private Config currentConfig;
    private Runnable onRunFinished;

    public TestRunPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(ModernTheme.BG_CONTENT);

        // ═══════════════════════════════════════════════════════════
        // TOP: Progress section
        // ═══════════════════════════════════════════════════════════
        JPanel progressSection = new JPanel();
        progressSection.setLayout(new BoxLayout(progressSection, BoxLayout.Y_AXIS));
        progressSection.setBackground(ModernTheme.BG_CARD);
        progressSection.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.BORDER),
            BorderFactory.createEmptyBorder(28, 40, 24, 40)
        ));

        // Phase + elapsed row
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setBackground(ModernTheme.BG_CARD);

        phaseLabel = new JLabel("Ready");
        phaseLabel.setFont(ModernTheme.FONT_TITLE);
        phaseLabel.setForeground(ModernTheme.TEXT_PRIMARY);

        elapsedLabel = new JLabel("");
        elapsedLabel.setFont(ModernTheme.FONT_MONO.deriveFont(12f));
        elapsedLabel.setForeground(ModernTheme.TEXT_MUTED);

        topRow.add(phaseLabel, BorderLayout.WEST);
        topRow.add(elapsedLabel, BorderLayout.EAST);

        progressSection.add(topRow);
        progressSection.add(Box.createVerticalStrut(18));

        // Progress bar + percent
        JPanel barRow = new JPanel(new BorderLayout(14, 0));
        barRow.setBackground(ModernTheme.BG_CARD);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(400, 14));
        progressBar.setBackground(ModernTheme.ACCENT_LIGHT);
        progressBar.setForeground(ModernTheme.ACCENT);
        progressBar.setBorder(null);
        progressBar.setValue(0);

        percentLabel = new JLabel("0%");
        percentLabel.setFont(ModernTheme.FONT_TITLE.deriveFont(Font.BOLD, 28f));
        percentLabel.setForeground(ModernTheme.ACCENT);
        percentLabel.setPreferredSize(new Dimension(70, 40));

        barRow.add(progressBar, BorderLayout.CENTER);
        barRow.add(percentLabel, BorderLayout.EAST);

        progressSection.add(barRow);
        progressSection.add(Box.createVerticalStrut(10));

        // Detail label
        detailLabel = new JLabel("Press Start to run the test");
        detailLabel.setFont(ModernTheme.FONT_UI.deriveFont(13f));
        detailLabel.setForeground(ModernTheme.TEXT_SECONDARY);
        progressSection.add(detailLabel);

        add(progressSection, BorderLayout.NORTH);

        // ═══════════════════════════════════════════════════════════
        // CENTER: Config summary card
        // ═══════════════════════════════════════════════════════════
        configSummaryCard = ModernTheme.cardPanel();
        configSummaryCard.setLayout(new BoxLayout(configSummaryCard, BoxLayout.Y_AXIS));
        configSummaryCard.setBorder(BorderFactory.createEmptyBorder(24, 40, 24, 40));
        configSummaryCard.setBackground(ModernTheme.BG_CONTENT);

        JLabel summaryTitle = new JLabel("Configuration");
        summaryTitle.setFont(ModernTheme.FONT_UI_BOLD.deriveFont(15f));
        summaryTitle.setForeground(ModernTheme.TEXT_PRIMARY);
        summaryTitle.setAlignmentX(LEFT_ALIGNMENT);
        configSummaryCard.add(summaryTitle);
        configSummaryCard.add(Box.createVerticalStrut(12));

        JScrollPane scroll = new JScrollPane(configSummaryCard);
        scroll.setBorder(null);
        scroll.setBackground(ModernTheme.BG_CONTENT);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        // ═══════════════════════════════════════════════════════════
        // BOTTOM: Button bar
        // ═══════════════════════════════════════════════════════════
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 16));
        buttonBar.setBackground(ModernTheme.BG_CONTENT);

        startBtn = ModernTheme.primaryButton("▶  Start Test");
        stopBtn  = ModernTheme.secondaryButton("■  Stop");
        stopBtn.setEnabled(false);
        openResultsBtn = ModernTheme.secondaryButton("Open Results");
        openResultsBtn.setEnabled(false);

        buttonBar.add(startBtn);
        buttonBar.add(stopBtn);
        buttonBar.add(Box.createHorizontalStrut(8));
        buttonBar.add(openResultsBtn);

        add(buttonBar, BorderLayout.SOUTH);

        // ── Actions ──────────────────────────────────────────────
        startBtn.addActionListener(e -> startTest());
        stopBtn.addActionListener(e -> stopTest());
        openResultsBtn.addActionListener(e -> {
            if (onRunFinished != null) onRunFinished.run();
        });
    }

    // ── Config summary ─────────────────────────────────────────────

    public void setConfig(Config config) {
        this.currentConfig = config;
        configSummaryCard.removeAll();

        JLabel title = new JLabel("Configuration");
        title.setFont(ModernTheme.FONT_UI_BOLD.deriveFont(15f));
        title.setForeground(ModernTheme.TEXT_PRIMARY);
        title.setAlignmentX(LEFT_ALIGNMENT);
        configSummaryCard.add(title);
        configSummaryCard.add(Box.createVerticalStrut(16));

        // Two-column grid of key-value pairs
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBackground(ModernTheme.BG_CONTENT);
        grid.setAlignmentX(LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 0, 4, 28);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        int col = 0;
        List<ConfigFieldDiscoverer.ConfigFieldMeta> fields = ConfigFieldDiscoverer.discover();

        for (ConfigFieldDiscoverer.ConfigFieldMeta meta : fields) {
            if ("otherArgs".equals(meta.name())) continue;
            Object val = ConfigFieldDiscoverer.readValue(config, meta.name());
            if (isEmptyValue(meta, val)) continue;

            String display = formatValue(meta, val);

            // Key
            gbc.gridx = col * 2;
            gbc.gridy = row;
            gbc.weightx = 0;
            JLabel keyLbl = new JLabel(meta.label());
            keyLbl.setFont(ModernTheme.FONT_UI.deriveFont(12f));
            keyLbl.setForeground(ModernTheme.TEXT_SECONDARY);
            grid.add(keyLbl, gbc);

            // Value
            gbc.gridx = col * 2 + 1;
            JLabel valLbl = new JLabel(display);
            valLbl.setFont(ModernTheme.FONT_UI_BOLD.deriveFont(12f));
            valLbl.setForeground(ModernTheme.TEXT_PRIMARY);
            grid.add(valLbl, gbc);

            col++;
            if (col >= 2) { col = 0; row++; }
        }
        // final row break if we ended on col 0
        if (col > 0) row++;

        configSummaryCard.add(grid);
        configSummaryCard.add(Box.createVerticalGlue());
        configSummaryCard.revalidate();
        configSummaryCard.repaint();
    }

    private boolean isEmptyValue(ConfigFieldDiscoverer.ConfigFieldMeta meta, Object val) {
        if (val == null) return true;
        if (meta.type() == ConfigFieldDiscoverer.FieldType.BOOL && !((Boolean) val)) return true;
        if (meta.type() == ConfigFieldDiscoverer.FieldType.INT && ((Integer) val) == 0
                && !"threadLimit".equals(meta.name())) return true;
        if (val instanceof String s && s.isEmpty()) return true;
        if (val instanceof List<?> list && list.isEmpty()) return true;
        return false;
    }

    private String formatValue(ConfigFieldDiscoverer.ConfigFieldMeta meta, Object val) {
        if (meta.type() == ConfigFieldDiscoverer.FieldType.STRING_LIST) {
            if (val instanceof List<?> list) {
                if (list.isEmpty()) return "(none)";
                return String.join(", ", list.stream().map(Object::toString).toList());
            }
        }
        if (meta.type() == ConfigFieldDiscoverer.FieldType.BOOL) {
            return ((Boolean) val) ? "Yes" : "No";
        }
        String s = val.toString();
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

    // ── Test lifecycle ──────────────────────────────────────────────

    private void startTest() {
        if (currentConfig == null) return;

        progressBar.setValue(0);
        progressBar.setIndeterminate(false);
        percentLabel.setText("0%");
        percentLabel.setForeground(ModernTheme.ACCENT);
        phaseLabel.setText("Initializing...");
        phaseLabel.setForeground(ModernTheme.TEXT_PRIMARY);
        detailLabel.setText("Starting test runner...");
        detailLabel.setForeground(ModernTheme.TEXT_SECONDARY);
        elapsedLabel.setText("");
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        openResultsBtn.setEnabled(false);
        running.set(true);
        startTime = Instant.now();

        if (elapsedTimer != null) elapsedTimer.stop();
        elapsedTimer = new Timer(400, e -> updateElapsed());
        elapsedTimer.start();

        if (stopFlashTimer != null) stopFlashTimer.stop();

        progressListener = new GuiProgressListener(this::handleSnapshot);

        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    Core core = new Core(currentConfig, progressListener);
                    core.execute();
                } catch (Exception e) {
                    progressListener.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                return null;
            }

            @Override
            protected void done() {
                running.set(false);
                startBtn.setEnabled(true);
                stopBtn.setEnabled(false);
                openResultsBtn.setEnabled(true);
                if (elapsedTimer != null) elapsedTimer.stop();
                updateElapsed();

                if (progressListener.hasError()) {
                    phaseLabel.setText("Error");
                    phaseLabel.setForeground(ModernTheme.DANGER);
                    percentLabel.setText("ERR");
                    percentLabel.setForeground(ModernTheme.DANGER);
                    detailLabel.setText(progressListener.getErrorMessage());
                } else {
                    progressBar.setValue(100);
                    phaseLabel.setText("Complete");
                    phaseLabel.setForeground(ModernTheme.SUCCESS);
                    percentLabel.setText("100%");
                    percentLabel.setForeground(ModernTheme.SUCCESS);
                    detailLabel.setText("All tests finished — click Open Results to review");
                }
            }
        };
        worker.execute();
    }

    private void stopTest() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        running.set(false);
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
        openResultsBtn.setEnabled(true);
        if (elapsedTimer != null) elapsedTimer.stop();
        updateElapsed();

        phaseLabel.setText("Stopped");
        phaseLabel.setForeground(ModernTheme.WARNING);
        detailLabel.setText("Test was stopped by user");
        progressBar.setValue(progressBar.getValue()); // keep current value

        // Flash the stop state briefly
        stopFlashTimer = new Timer(2000, e -> {
            if (!running.get()) {
                phaseLabel.setForeground(ModernTheme.TEXT_PRIMARY);
                phaseLabel.setText("Ready");
            }
        });
        stopFlashTimer.setRepeats(false);
        stopFlashTimer.start();
    }

    // ── Progress handling ───────────────────────────────────────────

    private void handleSnapshot(GuiProgressListener.ProgressSnapshot snap) {
        if (snap.error()) {
            phaseLabel.setText("Error");
            phaseLabel.setForeground(ModernTheme.DANGER);
            detailLabel.setText(progressListener.getErrorMessage());
            detailLabel.setForeground(ModernTheme.DANGER);
            return;
        }
        if (snap.done()) {
            return; // done() handles final state
        }

        // Progress bar
        if (snap.isIndeterminate()) {
            progressBar.setIndeterminate(true);
        } else {
            progressBar.setIndeterminate(false);
            progressBar.setMaximum(snap.totalSteps());
            progressBar.setValue(snap.currentSteps());
            int pct = snap.getPercent();
            percentLabel.setText(pct + "%");

            // Phase label style
            if (pct < 100) {
                percentLabel.setForeground(ModernTheme.ACCENT);
                phaseLabel.setForeground(ModernTheme.TEXT_PRIMARY);
            }
        }

        // Parse batch info from message
        if (snap.latestMessage() != null) {
            detailLabel.setText(snap.latestMessage());
        }

        updateElapsed();
    }

    private void updateElapsed() {
        if (startTime == null) return;
        Duration d = Duration.between(startTime, Instant.now());
        long totalSecs = d.toSeconds();
        if (totalSecs < 3600) {
            elapsedLabel.setText(String.format("%02d:%02d", totalSecs / 60, totalSecs % 60));
        } else {
            elapsedLabel.setText(String.format("%d:%02d:%02d", totalSecs / 3600, (totalSecs % 3600) / 60, totalSecs % 60));
        }
    }

    // ── Getters ─────────────────────────────────────────────────────

    public void setOnRunFinished(Runnable r) { this.onRunFinished = r; }
    public boolean isRunning() { return running.get(); }
}
