package com.wenzejin.gui;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Global workspace settings panel. Persists to ~/.ccmed_gui.properties.
 */
public class WorkspaceSettingsPanel extends JPanel {

    private static final Path SETTINGS_FILE = Paths.get(
        System.getProperty("user.home"), ".ccmed_gui.properties");

    private JTextField workspaceRootField;
    private JLabel statusLabel;
    private Runnable onSettingsChanged;

    public WorkspaceSettingsPanel() {
        setLayout(new BorderLayout());
        setBackground(ModernTheme.BG_CONTENT);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ModernTheme.BG_CONTENT);
        content.setBorder(ModernTheme.padding(24, 24, 24, 24));

        // Title
        JLabel title = new JLabel("Global Settings");
        title.setFont(ModernTheme.FONT_TITLE);
        title.setForeground(ModernTheme.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(20));

        // Workspace root setting
        JPanel workspaceCard = ModernTheme.cardPanel();
        workspaceCard.setLayout(new BoxLayout(workspaceCard, BoxLayout.Y_AXIS));
        workspaceCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        workspaceCard.setMaximumSize(new Dimension(800, 120));

        JLabel sectionTitle = ModernTheme.sectionHeader("Workspace Root Directory");
        sectionTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        workspaceCard.add(sectionTitle);
        workspaceCard.add(Box.createVerticalStrut(4));

        JLabel desc = ModernTheme.mutedLabel(
            "Default directory where test results are stored. The Results browser will scan this directory "
            + "for past test runs. You can also set this per-test in the configuration.");
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        workspaceCard.add(desc);
        workspaceCard.add(Box.createVerticalStrut(8));

        JPanel fieldRow = new JPanel(new BorderLayout(8, 0));
        fieldRow.setBackground(ModernTheme.BG_CARD);
        fieldRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        fieldRow.setMaximumSize(new Dimension(750, 35));

        workspaceRootField = new JTextField(40);
        workspaceRootField.setFont(ModernTheme.FONT_UI);
        workspaceRootField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ModernTheme.BORDER, 1),
            ModernTheme.padding(4, 8, 4, 8)
        ));
        fieldRow.add(workspaceRootField, BorderLayout.CENTER);

        JButton browseBtn = ModernTheme.secondaryButton("Browse...");
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(new File("."));
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Select Workspace Root Directory");
            String current = workspaceRootField.getText().trim();
            if (!current.isEmpty()) {
                File f = new File(current);
                if (f.exists() && f.isDirectory()) fc.setCurrentDirectory(f);
            }
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                workspaceRootField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        fieldRow.add(browseBtn, BorderLayout.EAST);

        workspaceCard.add(fieldRow);
        content.add(workspaceCard);
        content.add(Box.createVerticalStrut(16));

        // Save button
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonRow.setBackground(ModernTheme.BG_CONTENT);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton saveBtn = ModernTheme.primaryButton("Save Settings");
        saveBtn.addActionListener(e -> saveSettings());
        buttonRow.add(saveBtn);

        buttonRow.add(Box.createHorizontalStrut(8));

        statusLabel = new JLabel(" ");
        statusLabel.setFont(ModernTheme.FONT_UI);
        statusLabel.setForeground(ModernTheme.TEXT_SECONDARY);
        buttonRow.add(statusLabel);

        content.add(buttonRow);
        content.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ModernTheme.BG_CONTENT);
        add(scrollPane, BorderLayout.CENTER);

        // Load settings
        loadSettings();
    }

    public void setOnSettingsChanged(Runnable r) { this.onSettingsChanged = r; }

    public String getWorkspaceRoot() {
        String text = workspaceRootField.getText().trim();
        return text.isEmpty() ? "./workspace" : text;
    }

    private void loadSettings() {
        if (Files.exists(SETTINGS_FILE)) {
            try (InputStream in = Files.newInputStream(SETTINGS_FILE)) {
                Properties props = new Properties();
                props.load(in);
                String root = props.getProperty("workspace.root", "./workspace");
                workspaceRootField.setText(root);
            } catch (IOException e) {
                workspaceRootField.setText("./workspace");
            }
        } else {
            workspaceRootField.setText("./workspace");
        }
    }

    private void saveSettings() {
        try {
            Properties props = new Properties();
            props.setProperty("workspace.root", workspaceRootField.getText().trim());

            Files.createDirectories(SETTINGS_FILE.getParent());
            try (OutputStream out = Files.newOutputStream(SETTINGS_FILE)) {
                props.store(out, "CCMed GUI Settings");
            }

            statusLabel.setText("Settings saved");
            statusLabel.setForeground(ModernTheme.SUCCESS);

            if (onSettingsChanged != null) onSettingsChanged.run();

            // Reset status after a moment
            Timer timer = new Timer(3000, e -> {
                statusLabel.setText(" ");
                statusLabel.setForeground(ModernTheme.TEXT_SECONDARY);
            });
            timer.setRepeats(false);
            timer.start();
        } catch (IOException e) {
            statusLabel.setText("Failed to save: " + e.getMessage());
            statusLabel.setForeground(ModernTheme.DANGER);
        }
    }
}
