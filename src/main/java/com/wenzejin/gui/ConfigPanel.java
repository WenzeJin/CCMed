package com.wenzejin.gui;

import com.wenzejin.core.Config;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;

public class ConfigPanel extends JPanel {

    private final JTextField taskNameField;
    private final JSpinner threadLimitSpinner;
    private final JSpinner enumLimitSpinner;
    private final JComboBox<String> stopModeCombo;
    private final JSpinner sourceLimitSpinner;
    private final JSpinner followUpLimitSpinner;
    private final JCheckBox artCheck;
    private final JSpinner batchSizeSpinner;
    private final JSpinner miniBatchSizeSpinner;
    private final JTextField workerTypeField;
    private final JTextField rulesPathField;
    private final JTextField targetPathField;
    private final JTextField bfuncPathField;
    private final JCheckBox simpleModeCheck;
    private final JComboBox<String> storeModeCombo;
    private final JComboBox<String> bfuncPolicyCombo;

    public ConfigPanel() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel formPanel = new JPanel(new GridBagLayout());
        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 0.0;

        int row = 0;

        // Task Name
        taskNameField = addField(formPanel, gbc, row++, "Task Name:", new JTextField("DefaultTask"));
        
        // Thread Limit
        threadLimitSpinner = addSpinner(formPanel, gbc, row++, "Thread Limit:", 1, 1, 128, 1);

        // Enum Limit
        enumLimitSpinner = addSpinner(formPanel, gbc, row++, "Enum Limit:", 1000, 1, Integer.MAX_VALUE, 100);

        // Stop Mode
        stopModeCombo = addCombo(formPanel, gbc, row++, "Stop Mode:", new String[]{"panic", "limited", "panic+limited"});

        // Source Limit
        sourceLimitSpinner = addSpinner(formPanel, gbc, row++, "Source Limit:", 100, 1, Integer.MAX_VALUE, 10);

        // Follow Up Limit
        followUpLimitSpinner = addSpinner(formPanel, gbc, row++, "Follow Up Limit:", 1000, 1, Integer.MAX_VALUE, 100);

        // ART
        artCheck = addCheckBox(formPanel, gbc, row++, "Enable ART", true);

        // Batch Size
        batchSizeSpinner = addSpinner(formPanel, gbc, row++, "Batch Size:", 50, 1, Integer.MAX_VALUE, 10);

        // Mini Batch Size
        miniBatchSizeSpinner = addSpinner(formPanel, gbc, row++, "Mini Batch Size:", 10, 1, Integer.MAX_VALUE, 1);

        // Worker Type
        workerTypeField = addField(formPanel, gbc, row++, "Worker Type:", new JTextField("StandardWorker"));

        // Rules Path
        rulesPathField = addFileField(formPanel, gbc, row++, "Rules Path:", true);

        // Target Path
        targetPathField = addFileField(formPanel, gbc, row++, "Target Path:", false);

        // Bfunc Path
        bfuncPathField = addFileField(formPanel, gbc, row++, "Bfunc Path:", false);

        // Simple Mode
        simpleModeCheck = addCheckBox(formPanel, gbc, row++, "Simple Mode", false);

        // Store Mode
        storeModeCombo = addCombo(formPanel, gbc, row++, "Store Mode:", new String[]{"fail", "all", "none"});

        // Bfunc Policy
        bfuncPolicyCombo = addCombo(formPanel, gbc, row++, "Bfunc Policy:", new String[]{"enum", "random"});

        // Buttons Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton loadButton = new JButton("Load Config");
        JButton saveButton = new JButton("Save Config");
        
        loadButton.addActionListener(e -> loadConfig());
        saveButton.addActionListener(e -> saveConfig());

        buttonPanel.add(loadButton);
        buttonPanel.add(saveButton);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Set default values
        setDefaultValues();
    }

    private void setDefaultValues() {
        Config config = new Config();
        taskNameField.setText(config.taskName);
        threadLimitSpinner.setValue(config.threadLimit);
        enumLimitSpinner.setValue(config.enumLimit);
        stopModeCombo.setSelectedItem(config.stopMode);
        sourceLimitSpinner.setValue(config.sourceLimit);
        followUpLimitSpinner.setValue(config.followUpLimit);
        artCheck.setSelected(config.ART);
        batchSizeSpinner.setValue(config.batchSize);
        miniBatchSizeSpinner.setValue(config.miniBatchSize);
        workerTypeField.setText(config.workerType != null ? config.workerType : "StandardWorker");
        rulesPathField.setText(config.rulesPath != null ? config.rulesPath : "");
        targetPathField.setText(config.targetPath != null ? config.targetPath : "");
        bfuncPathField.setText(config.bfuncPath != null ? config.bfuncPath : "");
        simpleModeCheck.setSelected(config.simpleMode);
        storeModeCombo.setSelectedItem(config.storeMode);
        bfuncPolicyCombo.setSelectedItem(config.bfuncPolicy);
    }

    public Config getConfig() {
        Config config = new Config();
        config.taskName = taskNameField.getText();
        config.timeStamp = true; // Always true in GUI mode
        config.threadLimit = (Integer) threadLimitSpinner.getValue();
        config.enumLimit = (Integer) enumLimitSpinner.getValue();
        config.stopMode = (String) stopModeCombo.getSelectedItem();
        config.sourceLimit = (Integer) sourceLimitSpinner.getValue();
        config.followUpLimit = (Integer) followUpLimitSpinner.getValue();
        config.ART = artCheck.isSelected();
        config.batchSize = (Integer) batchSizeSpinner.getValue();
        config.miniBatchSize = (Integer) miniBatchSizeSpinner.getValue();
        config.workerType = workerTypeField.getText();
        config.rulesPath = rulesPathField.getText();
        config.targetPath = targetPathField.getText();
        config.bfuncPath = bfuncPathField.getText();
        config.simpleMode = simpleModeCheck.isSelected();
        config.storeMode = (String) storeModeCombo.getSelectedItem();
        config.bfuncPolicy = (String) bfuncPolicyCombo.getSelectedItem();
        return config;
    }

    private void loadConfig() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Config config = Config.loadFromJson(fileChooser.getSelectedFile().getAbsolutePath());
                applyConfig(config);
                JOptionPane.showMessageDialog(this, "Config loaded successfully.");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error loading config: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveConfig() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Config config = getConfig();
                config.saveToJson(fileChooser.getSelectedFile().getAbsolutePath());
                JOptionPane.showMessageDialog(this, "Config saved successfully.");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error saving config: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void applyConfig(Config config) {
        taskNameField.setText(config.taskName);
        threadLimitSpinner.setValue(config.threadLimit);
        enumLimitSpinner.setValue(config.enumLimit);
        stopModeCombo.setSelectedItem(config.stopMode);
        sourceLimitSpinner.setValue(config.sourceLimit);
        followUpLimitSpinner.setValue(config.followUpLimit);
        artCheck.setSelected(config.ART);
        batchSizeSpinner.setValue(config.batchSize);
        miniBatchSizeSpinner.setValue(config.miniBatchSize);
        workerTypeField.setText(config.workerType);
        rulesPathField.setText(config.rulesPath);
        targetPathField.setText(config.targetPath);
        bfuncPathField.setText(config.bfuncPath);
        simpleModeCheck.setSelected(config.simpleMode);
        storeModeCombo.setSelectedItem(config.storeMode);
        bfuncPolicyCombo.setSelectedItem(config.bfuncPolicy);
    }

    // Helper methods for UI construction

    private JTextField addField(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(field, gbc);
        return field;
    }

    private JCheckBox addCheckBox(JPanel panel, GridBagConstraints gbc, int row, String label, boolean checked) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        JCheckBox checkBox = new JCheckBox(label, checked);
        panel.add(checkBox, gbc);
        gbc.gridwidth = 1;
        return checkBox;
    }

    private JSpinner addSpinner(JPanel panel, GridBagConstraints gbc, int row, String label, int value, int min, int max, int step) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        panel.add(spinner, gbc);
        return spinner;
    }

    private JComboBox<String> addCombo(JPanel panel, GridBagConstraints gbc, int row, String label, String[] items) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JComboBox<String> combo = new JComboBox<>(items);
        panel.add(combo, gbc);
        return combo;
    }

    private JTextField addFileField(JPanel panel, GridBagConstraints gbc, int row, String label, boolean isDirectory) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JPanel filePanel = new JPanel(new BorderLayout(5, 0));
        JTextField field = new JTextField();
        JButton browseButton = new JButton("...");
        browseButton.setPreferredSize(new Dimension(30, 20));
        
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(isDirectory ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        filePanel.add(field, BorderLayout.CENTER);
        filePanel.add(browseButton, BorderLayout.EAST);
        panel.add(filePanel, gbc);
        return field;
    }
}
