package com.wenzejin.gui;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class MainGUI {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainGUI::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("CCTester GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        // 输出区域
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        // 按钮面板
        JPanel panel = new JPanel();
        JButton selectConfigBtn = new JButton("Select Config");
        JButton runBtn = new JButton("Run Test");

        panel.add(selectConfigBtn);
        panel.add(runBtn);
        frame.add(panel, BorderLayout.NORTH);

        // 配置路径
        final String[] configPath = {"./config.json"};

        selectConfigBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            int result = chooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                configPath[0] = chooser.getSelectedFile().getAbsolutePath();
                logArea.append("Selected config: " + configPath[0] + "\n");
            }
        });



        frame.setVisible(true);
    }
}
