package com.wenzejin.gui;

import com.wenzejin.core.Config;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * GUI entry point. Builds the main window with four tabs.
 * Launched from Main.java when -g/--gui flag is specified.
 */
public class GuiMain {

    private static final String APP_TITLE = "CCMed — Test Runner v1.2.0";

    /**
     * Launch the GUI application.
     *
     * @param configPath optional path to a config file to pre-load (from -c flag)
     */
    public static void launch(String configPath) {
        try {
            ModernTheme.apply();
        } catch (Exception e) {
            System.err.println("Warning: Failed to apply theme: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            try {
                JFrame frame = new JFrame(APP_TITLE);
                frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                frame.setSize(1280, 850);
                frame.setMinimumSize(new Dimension(1024, 700));

                // Center on screen
                frame.setLocationRelativeTo(null);

                // Build UI
                MainPanel mainPanel = new MainPanel(frame);

                // Pre-load config if provided
                if (configPath != null) {
                    try {
                        Config config = Config.loadFromJson(configPath);
                        mainPanel.getConfigPanel().populateForm(config);
                        mainPanel.selectTab(0);
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(frame,
                            "Failed to load config: " + configPath + "\n" + e.getMessage(),
                            "Config Load Error", JOptionPane.WARNING_MESSAGE);
                    }
                }

                frame.setContentPane(mainPanel);

                // Window close handling
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        if (mainPanel.isTestRunning()) {
                            int choice = JOptionPane.showConfirmDialog(frame,
                                "A test is currently running. Stop and exit?",
                                "Test Running",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE);
                            if (choice != JOptionPane.YES_OPTION) return;
                        }
                        frame.dispose();
                        System.exit(0);
                    }
                });

                // Set application icon (optional — try macOS dock icon)
                trySetDockIcon();

                frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                    "Failed to start GUI: " + e.getMessage(),
                    "Fatal Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    private static void trySetDockIcon() {
        try {
            // macOS dock icon via Apple EAWT — fallback silently on other platforms
            Class<?> appClass = Class.forName("com.apple.eawt.Application");
            Object app = appClass.getMethod("getApplication").invoke(null);
            Image icon = Toolkit.getDefaultToolkit().getImage(
                GuiMain.class.getResource("/icon.png"));
            if (icon != null) {
                appClass.getMethod("setDockIconImage", Image.class).invoke(app, icon);
            }
        } catch (Exception ignored) {
            // Not on macOS or no icon available — that's fine
        }
    }

    /** The main panel containing the tabbed layout. */
    private static class MainPanel extends JPanel {
        private final JTabbedPane tabbedPane;
        private final ConfigPanel configPanel;
        private final TestRunPanel testRunPanel;
        private final ResultsBrowserPanel resultsPanel;
        private final WorkspaceSettingsPanel settingsPanel;
        private final JFrame frame;

        MainPanel(JFrame frame) {
            this.frame = frame;
            setLayout(new BorderLayout());
            setBackground(ModernTheme.BG_CONTENT);

            // ── Tabbed pane ────────────────────────────────────
            tabbedPane = new JTabbedPane(JTabbedPane.LEFT);
            tabbedPane.setFont(ModernTheme.FONT_TAB);
            tabbedPane.setBackground(ModernTheme.BG_CONTENT);
            tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

            // Sidebar-style tabs
            UIManager.put("TabbedPane.tabAreaBackground", ModernTheme.BG_SIDEBAR);

            // ── Create panels ──────────────────────────────────
            settingsPanel = new WorkspaceSettingsPanel();
            configPanel = new ConfigPanel();
            testRunPanel = new TestRunPanel();
            resultsPanel = new ResultsBrowserPanel(settingsPanel);

            // ── Wire up cross-panel interactions ───────────────
            configPanel.setOnRunRequested(() -> {
                Config config = configPanel.buildConfig();
                testRunPanel.setConfig(config);
                tabbedPane.setSelectedIndex(1); // Switch to Run tab
                // Auto-start after a brief delay to let UI settle
                SwingUtilities.invokeLater(() -> {
                    // Just switch tab; user clicks Start manually
                });
            });

            testRunPanel.setOnRunFinished(() -> {
                tabbedPane.setSelectedIndex(2); // Switch to Results tab
                resultsPanel.refresh();
            });

            settingsPanel.setOnSettingsChanged(() -> resultsPanel.refresh());

            // ── Add tabs ───────────────────────────────────────
            tabbedPane.addTab("⚙ Test Config", configPanel);
            tabbedPane.addTab("▶ Run Test", testRunPanel);
            tabbedPane.addTab("📊 Results", resultsPanel);
            tabbedPane.addTab("⚡ Settings", settingsPanel);

            add(tabbedPane, BorderLayout.CENTER);

            // ── Status bar ─────────────────────────────────────
            JPanel statusBar = new JPanel(new BorderLayout());
            statusBar.setBackground(ModernTheme.BG_SIDEBAR);
            statusBar.setBorder(ModernTheme.padding(2, 12, 2, 12));

            JLabel versionLabel = new JLabel("CCMed v1.2.0");
            versionLabel.setFont(ModernTheme.FONT_UI.deriveFont(10f));
            versionLabel.setForeground(ModernTheme.TEXT_MUTED);
            statusBar.add(versionLabel, BorderLayout.EAST);

            add(statusBar, BorderLayout.SOUTH);
        }

        public ConfigPanel getConfigPanel() { return configPanel; }
        public void selectTab(int index) { tabbedPane.setSelectedIndex(index); }
        public boolean isTestRunning() { return testRunPanel.isRunning(); }
    }

    /** Launch without a config path. */
    public static void launch() {
        launch(null);
    }
}
