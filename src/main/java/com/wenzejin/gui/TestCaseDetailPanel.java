package com.wenzejin.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.*;
import javax.xml.parsers.*;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Detail viewer for a single test case. Shows constraint tree, context pool, bfunc definitions.
 */
public class TestCaseDetailPanel extends JPanel {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final JTabbedPane tabs;
    private final JLabel headerLabel;
    private Path caseDir;
    private JsonNode infoJson;
    private Consumer<String> onSourceNavigate;

    public TestCaseDetailPanel() {
        setLayout(new BorderLayout());
        setBackground(ModernTheme.BG_CONTENT);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ModernTheme.BG_CARD);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.BORDER),
            ModernTheme.padding(8, 12, 8, 12)
        ));
        headerLabel = new JLabel("Select a test case to view details");
        headerLabel.setFont(ModernTheme.FONT_UI_BOLD.deriveFont(14f));
        headerLabel.setForeground(ModernTheme.TEXT_PRIMARY);
        header.add(headerLabel, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        // Tabs
        tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(ModernTheme.FONT_TAB);
        tabs.setBackground(ModernTheme.BG_CONTENT);
        add(tabs, BorderLayout.CENTER);

        // Placeholder
        JPanel placeholder = new JPanel(new GridBagLayout());
        placeholder.setBackground(ModernTheme.BG_CONTENT);
        JLabel ph = ModernTheme.mutedLabel("Select a test case from the table to view details");
        ph.setFont(ModernTheme.FONT_UI.deriveFont(14f));
        placeholder.add(ph);
        tabs.addTab("Summary", placeholder);
    }

    public void setOnSourceNavigate(Consumer<String> handler) { this.onSourceNavigate = handler; }

    public void loadCase(Path caseDir) {
        this.caseDir = caseDir;
        tabs.removeAll();

        if (caseDir == null || !Files.isDirectory(caseDir)) {
            tabs.addTab("Summary", mutedPanel("Case directory not found: " + caseDir));
            return;
        }

        // Load info.json
        Path infoPath = caseDir.resolve("info.json");
        infoJson = null;
        if (Files.exists(infoPath)) {
            try {
                infoJson = mapper.readTree(infoPath.toFile());
            } catch (IOException ignored) {}
        }

        boolean isSource = infoJson != null && infoJson.has("isSource") && infoJson.get("isSource").asBoolean();

        // ── Summary tab ────────────────────────────────────────
        JPanel summaryPanel = new JPanel();
        summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
        summaryPanel.setBackground(ModernTheme.BG_CONTENT);
        summaryPanel.setBorder(ModernTheme.padding(12, 16, 12, 16));

        if (infoJson != null) {
            JPanel infoCard = ModernTheme.cardPanel();
            infoCard.setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(3, 8, 3, 8);
            gbc.anchor = GridBagConstraints.WEST;

            int r = 0;
            Iterator<Map.Entry<String, JsonNode>> it = infoJson.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                // Skip source array for custom rendering
                if ("source".equals(e.getKey()) && !isSource) continue;

                gbc.gridx = 0; gbc.gridy = r; gbc.weightx = 0.15;
                JLabel key = new JLabel(e.getKey() + ":");
                key.setFont(ModernTheme.FONT_UI_BOLD.deriveFont(11f));
                key.setForeground(ModernTheme.TEXT_SECONDARY);
                infoCard.add(key, gbc);

                gbc.gridx = 1; gbc.weightx = 0.85;
                String val = e.getValue().isArray()
                    ? prettyPrint(e.getValue())
                    : e.getValue().asText();
                JLabel valLabel = new JLabel(val);
                valLabel.setFont(ModernTheme.FONT_UI);
                valLabel.setForeground(ModernTheme.TEXT_PRIMARY);
                infoCard.add(valLabel, gbc);
                r++;
            }

            summaryPanel.add(infoCard);

            // Source navigation for follow-up cases
            if (!isSource && infoJson.has("source") && onSourceNavigate != null) {
                summaryPanel.add(Box.createVerticalStrut(12));
                JPanel sourcePanel = ModernTheme.cardPanel();
                sourcePanel.setLayout(new BoxLayout(sourcePanel, BoxLayout.Y_AXIS));
                JLabel srcTitle = new JLabel("Source Cases");
                srcTitle.setFont(ModernTheme.FONT_UI_BOLD.deriveFont(13f));
                srcTitle.setForeground(ModernTheme.TEXT_PRIMARY);
                sourcePanel.add(srcTitle);
                sourcePanel.add(Box.createVerticalStrut(4));

                JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
                buttons.setBackground(ModernTheme.BG_CARD);
                for (JsonNode srcId : infoJson.get("source")) {
                    String sid = srcId.asText();
                    JButton srcBtn = ModernTheme.secondaryButton("Source #" + sid);
                    srcBtn.setFont(srcBtn.getFont().deriveFont(11f));
                    srcBtn.addActionListener(e -> onSourceNavigate.accept(sid));
                    buttons.add(srcBtn);
                }
                sourcePanel.add(buttons);
                summaryPanel.add(sourcePanel);
            }
        } else {
            summaryPanel.add(mutedLabel("No info.json found"));
        }

        JScrollPane summaryScroll = new JScrollPane(summaryPanel);
        summaryScroll.setBorder(null);
        summaryScroll.setBackground(ModernTheme.BG_CONTENT);
        tabs.addTab("Summary", summaryScroll);

        // ── Constraint tab (rule.xml) ───────────────────────────
        Path rulePath = caseDir.resolve("rule.xml");
        if (Files.exists(rulePath)) {
            tabs.addTab("Constraint", createXmlViewer(rulePath));
        } else {
            tabs.addTab("Constraint", mutedPanel("rule.xml not available"));
        }

        // ── Context Pool tab ────────────────────────────────────
        Path ctxPath = caseDir.resolve("context_pool.json");
        if (Files.exists(ctxPath)) {
            tabs.addTab("Context Pool", createJsonViewer(ctxPath));
        } else {
            tabs.addTab("Context Pool", mutedPanel("context_pool.json not available"));
        }

        // ── Bfunc tab ───────────────────────────────────────────
        Path bfuncIn = caseDir.resolve("bfunc_in.txt");
        Path bfuncOut = caseDir.resolve("bfunc_out.txt");
        boolean hasBfuncIn = Files.exists(bfuncIn);
        boolean hasBfuncOut = Files.exists(bfuncOut);

        if (hasBfuncIn || hasBfuncOut) {
            JTabbedPane bfuncTabs = new JTabbedPane(JTabbedPane.TOP);
            bfuncTabs.setFont(ModernTheme.FONT_TAB);

            if (hasBfuncIn) {
                bfuncTabs.addTab("Input", createTextFileViewer(bfuncIn));
            }
            if (hasBfuncOut) {
                bfuncTabs.addTab("Output", createTextFileViewer(bfuncOut));
            }
            tabs.addTab("Bfunc Definitions", bfuncTabs);
        } else {
            tabs.addTab("Bfunc Definitions", mutedPanel("bfunc_in.txt / bfunc_out.txt not available"));
        }

        // ── Console tab ─────────────────────────────────────────
        Path stdoutPath = caseDir.resolve("stdout.txt");
        Path stderrPath = caseDir.resolve("stderr.txt");
        boolean hasStdout = Files.exists(stdoutPath);
        boolean hasStderr = Files.exists(stderrPath);

        if (hasStdout || hasStderr) {
            JTabbedPane consoleTabs = new JTabbedPane(JTabbedPane.TOP);
            consoleTabs.setFont(ModernTheme.FONT_TAB);
            if (hasStdout) consoleTabs.addTab("stdout", createTextFileViewer(stdoutPath));
            if (hasStderr) consoleTabs.addTab("stderr", createTextFileViewer(stderrPath));
            tabs.addTab("Console", consoleTabs);
        }

        // Update header
        String id = infoJson != null && infoJson.has("id") ? infoJson.get("id").asText() : "?";
        String typeLabel = isSource ? "Source Case" : "Follow-up Case";
        headerLabel.setText(typeLabel + " #" + id);
        headerLabel.setForeground(isSource ? ModernTheme.ACCENT : ModernTheme.WARNING);
    }

    // ── Viewers ────────────────────────────────────────────────────

    private JScrollPane createXmlViewer(Path xmlPath) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ModernTheme.BG_CONTENT);

        // Parse XML into tree
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Constraint");
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setIgnoringElementContentWhitespace(true);
            Document doc = dbf.newDocumentBuilder().parse(xmlPath.toFile());

            Element docElem = doc.getDocumentElement();
            if (docElem != null) {
                buildXmlTree(docElem, root);
            }
        } catch (Exception e) {
            root.add(new DefaultMutableTreeNode("Parse error: " + e.getMessage()));
        }

        JTree tree = new JTree(root);
        tree.setFont(ModernTheme.FONT_MONO);
        tree.setBackground(ModernTheme.BG_CARD);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        expandAllNodes(tree, 0, tree.getRowCount());

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(null);

        // Source view button
        JButton sourceBtn = ModernTheme.secondaryButton("Show Raw XML");
        sourceBtn.setFont(sourceBtn.getFont().deriveFont(11f));
        sourceBtn.addActionListener(e -> {
            try {
                String xml = Files.readString(xmlPath);
                JTextArea ta = new JTextArea(xml);
                ta.setEditable(false);
                ta.setFont(ModernTheme.FONT_MONO);
                ta.setBackground(new Color(0xF1, 0xF5, 0xF9));
                ta.setForeground(new Color(0x0F, 0x17, 0x2A));

                JScrollPane sp = new JScrollPane(ta);
                sp.setPreferredSize(new Dimension(700, 500));
                JOptionPane.showMessageDialog(this, sp, "Rule XML", JOptionPane.PLAIN_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to read XML: " + ex.getMessage());
            }
        });

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setBackground(ModernTheme.BG_CONTENT);
        toolbar.add(sourceBtn);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(treeScroll, BorderLayout.CENTER);
        return new JScrollPane(panel);
    }

    private void buildXmlTree(Node node, DefaultMutableTreeNode treeNode) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element elem = (Element) node;
            StringBuilder label = new StringBuilder();
            label.append("<").append(elem.getTagName());

            // Attributes
            NamedNodeMap attrs = elem.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Node attr = attrs.item(i);
                label.append(" ").append(attr.getNodeName()).append("=\"").append(attr.getNodeValue()).append("\"");
            }

            // If leaf (only text content), show it
            String text = getDirectTextContent(elem);
            if (!text.isEmpty() && elem.getChildNodes().getLength() == 1) {
                label.append("> ").append(text.trim());
            } else {
                label.append(">");
            }

            DefaultMutableTreeNode child = new DefaultMutableTreeNode(label.toString());

            NodeList children = elem.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                buildXmlTree(children.item(i), child);
            }
            treeNode.add(child);
        } else if (node.getNodeType() == Node.TEXT_NODE) {
            String text = node.getTextContent().trim();
            if (!text.isEmpty()) {
                treeNode.add(new DefaultMutableTreeNode("\"" + text + "\""));
            }
        }
    }

    private String getDirectTextContent(Element elem) {
        NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.TEXT_NODE) {
                String text = children.item(i).getTextContent().trim();
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }

    private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < rowCount; i++) {
            tree.expandRow(i);
        }
    }

    private JScrollPane createJsonViewer(Path jsonPath) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ModernTheme.BG_CONTENT);

        try {
            String raw = Files.readString(jsonPath);
            JsonNode root = mapper.readTree(raw);

            DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("context_pool");
            buildJsonTree(root, treeRoot);

            JTree tree = new JTree(treeRoot);
            tree.setFont(ModernTheme.FONT_MONO);
            tree.setBackground(ModernTheme.BG_CARD);
            tree.setRootVisible(true);
            tree.setShowsRootHandles(true);
            expandAllNodes(tree, 0, tree.getRowCount());
            JScrollPane treeScroll = new JScrollPane(tree);

            // Source view
            JTextArea sourceArea = new JTextArea(raw);
            sourceArea.setEditable(false);
            sourceArea.setFont(ModernTheme.FONT_MONO);
            sourceArea.setBackground(new Color(0xF1, 0xF5, 0xF9));
            sourceArea.setForeground(new Color(0x0F, 0x17, 0x2A));

            JTabbedPane subTabs = new JTabbedPane(JTabbedPane.TOP);
            subTabs.setFont(ModernTheme.FONT_TAB);
            subTabs.addTab("Tree", treeScroll);
            subTabs.addTab("Source", new JScrollPane(sourceArea));

            panel.add(subTabs, BorderLayout.CENTER);
        } catch (Exception e) {
            panel.add(new JLabel("Parse error: " + e.getMessage()));
        }

        return new JScrollPane(panel);
    }

    private void buildJsonTree(JsonNode node, DefaultMutableTreeNode treeNode) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode val = entry.getValue();
                if (val.isValueNode()) {
                    treeNode.add(new DefaultMutableTreeNode(key + ": " + val.asText()));
                } else if (val.isArray()) {
                    DefaultMutableTreeNode arr = new DefaultMutableTreeNode(key + " []");
                    int idx = 0;
                    for (JsonNode item : val) {
                        DefaultMutableTreeNode itemNode = new DefaultMutableTreeNode("[" + idx + "]");
                        buildJsonTree(item, itemNode);
                        arr.add(itemNode);
                        idx++;
                    }
                    treeNode.add(arr);
                } else {
                    DefaultMutableTreeNode obj = new DefaultMutableTreeNode(key);
                    buildJsonTree(val, obj);
                    treeNode.add(obj);
                }
            });
        } else if (node.isArray()) {
            int idx = 0;
            for (JsonNode item : node) {
                DefaultMutableTreeNode itemNode = new DefaultMutableTreeNode("[" + idx + "]");
                buildJsonTree(item, itemNode);
                treeNode.add(itemNode);
                idx++;
            }
        } else if (node.isValueNode()) {
            treeNode.setUserObject(treeNode.getUserObject() + ": " + node.asText());
        }
    }

    private JScrollPane createTextFileViewer(Path filePath) {
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        ta.setFont(ModernTheme.FONT_MONO);
        ta.setBackground(new Color(0xF1, 0xF5, 0xF9));
        ta.setForeground(new Color(0x0F, 0x17, 0x2A));
        ta.setBorder(ModernTheme.padding(4, 8, 4, 8));

        try {
            String content = Files.readString(filePath);
            if (content.length() > 512 * 1024) {
                content = content.substring(0, 512 * 1024) + "\n\n... [truncated at 512KB]";
            }
            ta.setText(content);
        } catch (IOException e) {
            ta.setText("Failed to read: " + e.getMessage());
        }

        JScrollPane sp = new JScrollPane(ta);
        sp.setBorder(null);
        return sp;
    }

    // ── Helpers ────────────────────────────────────────────────────

    private JPanel mutedPanel(String text) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(ModernTheme.BG_CONTENT);
        JLabel l = ModernTheme.mutedLabel(text);
        l.setFont(ModernTheme.FONT_UI.deriveFont(13f));
        p.add(l);
        return p;
    }

    private JLabel mutedLabel(String text) {
        JLabel l = ModernTheme.mutedLabel(text);
        l.setFont(ModernTheme.FONT_UI.deriveFont(13f));
        return l;
    }

    private String prettyPrint(JsonNode node) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return node.toString();
        }
    }
}
