package com.wenzejin.gui;

import com.wenzejin.core.Config;
import com.wenzejin.core.Core;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class MainGUI {

    private static CardLayout cardLayout;
    private static JPanel mainPanel;
    private static ConfigPanel configPanel;
    private static GlobalSettings globalSettings;

    public static void main(String[] args) {
        try {
            // Set System L&F
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        globalSettings = GlobalSettings.load();
        SwingUtilities.invokeLater(MainGUI::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("CCTester - Semantic-Guided Constructive Metamorphism Testing Framework");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 700);
        frame.setLocationRelativeTo(null); // Center on screen

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // 1. Main Menu
        JPanel mainMenu = createMainMenu(frame);
        mainPanel.add(mainMenu, "MAIN_MENU");

        // 2. Configuration Screen
        JPanel configScreen = createConfigScreen(frame);
        mainPanel.add(configScreen, "CONFIG");

        // 3. Progress Screen (Placeholder)
        JPanel progressScreen = createPlaceholderScreen("Testing in Progress (Not Implemented)", "PROGRESS");
        mainPanel.add(progressScreen, "PROGRESS");

        // 4. Result Screen (Placeholder)
        JPanel resultScreen = createPlaceholderScreen("Test Results (Not Implemented)", "RESULT");
        mainPanel.add(resultScreen, "RESULT");

        frame.add(mainPanel, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private static JPanel createMainMenu(JFrame frame) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("CCTester", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 32));
        gbc.gridy = 0;
        panel.add(titleLabel, gbc);

        JButton settingsButton = new JButton("Set Workspace Root");
        settingsButton.addActionListener(e -> showWorkspaceSettings(frame));
        gbc.gridy = 1;
        panel.add(settingsButton, gbc);

        JButton newTestButton = new JButton("Create New Test");
        newTestButton.setFont(new Font("Arial", Font.BOLD, 16));
        newTestButton.setPreferredSize(new Dimension(200, 50));
        newTestButton.addActionListener(e -> cardLayout.show(mainPanel, "CONFIG"));
        gbc.gridy = 2;
        panel.add(newTestButton, gbc);

        JButton historyButton = new JButton("View Test Results");
        historyButton.addActionListener(e -> JOptionPane.showMessageDialog(frame, "View Test Results (Not Implemented)"));
        gbc.gridy = 3;
        panel.add(historyButton, gbc);

        return panel;
    }

    private static void showWorkspaceSettings(JFrame frame) {
        String currentRoot = globalSettings.workspaceRoot;
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (currentRoot != null && !currentRoot.isEmpty()) {
            chooser.setCurrentDirectory(new File(currentRoot));
        }
        chooser.setDialogTitle("Select Workspace Root Directory");
        
        int result = chooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            globalSettings.workspaceRoot = chooser.getSelectedFile().getAbsolutePath();
            globalSettings.save();
            JOptionPane.showMessageDialog(frame, "Workspace Root updated to:\n" + globalSettings.workspaceRoot);
        }
    }

    private static JPanel createConfigScreen(JFrame frame) {
        JPanel panel = new JPanel(new BorderLayout());
        configPanel = new ConfigPanel();
        panel.add(configPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton backButton = new JButton("Back");
        backButton.addActionListener(e -> cardLayout.show(mainPanel, "MAIN_MENU"));
        
        JButton startButton = new JButton("Start Testing");
        startButton.setFont(new Font("Arial", Font.BOLD, 14));
        startButton.addActionListener(e -> startTesting(frame));

        buttonPanel.add(backButton);
        buttonPanel.add(startButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private static JPanel createPlaceholderScreen(String message, String cardName) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.ITALIC, 20));
        panel.add(label, BorderLayout.CENTER);

        JButton backButton = new JButton("Back to Main Menu");
        backButton.addActionListener(e -> cardLayout.show(mainPanel, "MAIN_MENU"));
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(backButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private static void startTesting(JFrame frame) {
        if (globalSettings.workspaceRoot == null || globalSettings.workspaceRoot.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please set Workspace Root in Main Menu first!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Config config = configPanel.getConfig();
        config.workspaceRoot = globalSettings.workspaceRoot;
        
        // Basic validation
        if (!config.validate(false)) {
            JOptionPane.showMessageDialog(frame, "Invalid Configuration. Please check your settings.", "Configuration Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(frame, "Start testing with current configuration?", "Confirm Start", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // Switch to Progress Screen
        cardLayout.show(mainPanel, "PROGRESS");

        // Run in a separate thread to avoid freezing GUI
        new Thread(() -> {
            try {
                Core core = new Core(config);
                core.execute();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame, "Testing Completed!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    // Ideally switch to RESULT screen here
                    cardLayout.show(mainPanel, "RESULT");
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame, "Error during testing: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    cardLayout.show(mainPanel, "MAIN_MENU");
                });
            }
        }).start();
    }
}
