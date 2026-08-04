package com.wenzejin.gui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenzejin.core.Config;
import com.wenzejin.gui.ConfigFieldDiscoverer.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;

/**
 * Configuration editor panel. Auto-generates form fields from Config class.
 */
public class ConfigPanel extends JPanel {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ConfigFieldMeta> fieldMetas;
    private final Map<String, JComponent> fieldWidgets = new LinkedHashMap<>();
    /** Maps path-field names to the actual JTextField inside their browse wrapper. */
    private final Map<String, JTextField> pathFieldMap = new LinkedHashMap<>();
    private final JTable otherArgsTable;
    private final DefaultTableModel otherArgsModel;
    private Config currentConfig;
    private JLabel statusLabel;
    private Runnable onRunRequested;

    public ConfigPanel() {
        this.fieldMetas = ConfigFieldDiscoverer.discover();

        setLayout(new BorderLayout());
        setBackground(ModernTheme.BG_CONTENT);

        // ── Toolbar ──────────────────────────────────────────────
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.setBackground(ModernTheme.BG_CARD);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.BORDER));

        JButton loadBtn = ModernTheme.secondaryButton("Load Config...");
        JButton saveBtn = ModernTheme.secondaryButton("Save As...");
        JButton runBtn = ModernTheme.primaryButton("Run Test ▶");
        JButton newBtn = ModernTheme.secondaryButton("New");

        statusLabel = new JLabel(" ");
        statusLabel.setFont(ModernTheme.FONT_UI);
        statusLabel.setForeground(ModernTheme.TEXT_SECONDARY);

        toolbar.add(newBtn);
        toolbar.add(loadBtn);
        toolbar.add(saveBtn);
        toolbar.add(Box.createHorizontalStrut(16));
        toolbar.add(runBtn);
        toolbar.add(Box.createHorizontalStrut(16));
        toolbar.add(statusLabel);

        add(toolbar, BorderLayout.NORTH);

        // ── Form body ────────────────────────────────────────────
        JPanel formContainer = new JPanel();
        formContainer.setLayout(new BoxLayout(formContainer, BoxLayout.Y_AXIS));
        formContainer.setBackground(ModernTheme.BG_CONTENT);
        formContainer.setBorder(ModernTheme.padding(12, 16, 12, 16));

        // Group fields by category
        Map<Category, List<ConfigFieldMeta>> grouped = new LinkedHashMap<>();
        for (ConfigFieldMeta meta : fieldMetas) {
            if ("otherArgs".equals(meta.name())) continue; // handled separately
            grouped.computeIfAbsent(meta.category(), k -> new ArrayList<>()).add(meta);
        }

        for (Map.Entry<Category, List<ConfigFieldMeta>> entry : grouped.entrySet()) {
            JPanel section = createSection(entry.getKey().label, entry.getValue());
            formContainer.add(section);
            formContainer.add(Box.createVerticalStrut(8));
        }

        // Custom args (otherArgs) section
        JPanel customSection = createOtherArgsSection();
        formContainer.add(customSection);

        JScrollPane scrollPane = new JScrollPane(formContainer);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBackground(ModernTheme.BG_CONTENT);
        add(scrollPane, BorderLayout.CENTER);

        // ── Sub-component for otherArgs ──────────────────────────
        otherArgsModel = new DefaultTableModel(new String[]{"Key", "JSON Value"}, 0);
        otherArgsTable = new JTable(otherArgsModel);
        otherArgsTable.setFont(ModernTheme.FONT_UI);
        otherArgsTable.setRowHeight(26);
        otherArgsTable.getTableHeader().setFont(ModernTheme.FONT_UI_BOLD);
        otherArgsTable.getTableHeader().setBackground(ModernTheme.BG_CONTENT);
        otherArgsTable.setGridColor(ModernTheme.BORDER);

        // ── Button actions ───────────────────────────────────────
        loadBtn.addActionListener(e -> loadConfig());
        saveBtn.addActionListener(e -> saveConfig());
        newBtn.addActionListener(e -> {
            currentConfig = new Config();
            populateForm(currentConfig);
            statusLabel.setText("New configuration created");
            statusLabel.setForeground(ModernTheme.TEXT_SECONDARY);
        });
        runBtn.addActionListener(e -> {
            if (validateForm()) {
                if (onRunRequested != null) onRunRequested.run();
            }
        });

        // Start with defaults
        currentConfig = new Config();
        populateForm(currentConfig);
    }

    public void setOnRunRequested(Runnable r) { this.onRunRequested = r; }

    private JPanel createSection(String title, List<ConfigFieldMeta> fields) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ModernTheme.BG_CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ModernTheme.BORDER, 1),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                ModernTheme.FONT_UI_BOLD.deriveFont(13f),
                ModernTheme.TEXT_PRIMARY
            ),
            ModernTheme.padding(8, 12, 8, 12)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.NORTHWEST;

        int row = 0;
        for (ConfigFieldMeta meta : fields) {
            // Label
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0.15;
            JLabel label = new JLabel(meta.label() + ":");
            label.setFont(ModernTheme.FONT_UI);
            label.setForeground(ModernTheme.TEXT_PRIMARY);
            panel.add(label, gbc);

            // Editor
            gbc.gridx = 1;
            gbc.weightx = 0.85;
            JComponent editor = createEditor(meta);
            panel.add(editor, gbc);

            // Hint
            if (meta.hint() != null && !meta.hint().isEmpty()) {
                gbc.gridx = 2;
                gbc.weightx = 0.0;
                JLabel hint = ModernTheme.mutedLabel("(?)");
                hint.setToolTipText(meta.hint());
                panel.add(hint, gbc);
            }

            row++;
        }

        // Fill bottom space
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JPanel(), gbc);

        return panel;
    }

    private JComponent createEditor(ConfigFieldMeta meta) {
        JComponent widget;
        switch (meta.type()) {
            case BOOL -> {
                JCheckBox cb = new JCheckBox();
                cb.setBackground(ModernTheme.BG_CARD);
                cb.setFont(ModernTheme.FONT_UI);
                widget = cb;
            }
            case INT -> {
                JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
                spinner.setFont(ModernTheme.FONT_UI);
                JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "#");
                editor.getTextField().setFont(ModernTheme.FONT_UI);
                spinner.setEditor(editor);
                widget = spinner;
            }
            case STRING -> {
                if (meta.comboValues() != null) {
                    JComboBox<String> cb = new JComboBox<>(meta.comboValues());
                    cb.setFont(ModernTheme.FONT_UI);
                    cb.setEditable(true);
                    cb.setBackground(ModernTheme.BG_CARD);
                    widget = cb;
                } else {
                    JTextField tf = new JTextField(30);
                    tf.setFont(ModernTheme.FONT_UI);
                    tf.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ModernTheme.BORDER, 1),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)
                    ));
                    widget = tf;
                }
            }
            case STRING_LIST -> {
                JTextArea ta = new JTextArea(2, 30);
                ta.setFont(ModernTheme.FONT_UI);
                ta.setBorder(BorderFactory.createLineBorder(ModernTheme.BORDER, 1));
                ta.setLineWrap(false);
                JScrollPane sp = new JScrollPane(ta);
                sp.setPreferredSize(new Dimension(300, 50));
                widget = sp;
            }
            default -> {
                JTextField tf = new JTextField(30);
                tf.setFont(ModernTheme.FONT_UI);
                widget = tf;
            }
        }

        // Path fields get a browse button
        if (meta.name().endsWith("Path") || meta.name().endsWith("path")) {
            JTextField tf = findTextField(widget);
            if (tf != null) {
                fieldWidgets.put(meta.name(), widget); // keep original widget too
                pathFieldMap.put(meta.name(), tf);
                return wrapWithBrowse(tf, meta.name().contains("rulesPath") || meta.name().contains("Root"));
            }
        }

        fieldWidgets.put(meta.name(), widget);
        return widget;
    }

    private JTextField findTextField(JComponent widget) {
        if (widget instanceof JTextField) return (JTextField) widget;
        if (widget instanceof JComboBox) {
            Component editor = ((JComboBox<?>) widget).getEditor().getEditorComponent();
            if (editor instanceof JTextField) return (JTextField) editor;
        }
        return null;
    }

    private JPanel wrapWithBrowse(JTextField tf, boolean dirsOnly) {
        JPanel wrap = new JPanel(new BorderLayout(4, 0));
        wrap.setBackground(ModernTheme.BG_CARD);
        wrap.add(tf, BorderLayout.CENTER);
        JButton browse = ModernTheme.secondaryButton("Browse...");
        browse.setFont(browse.getFont().deriveFont(11f));
        browse.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(new File("."));
            fc.setFileSelectionMode(dirsOnly ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
            String current = tf.getText().trim();
            if (!current.isEmpty()) {
                File f = new File(current);
                if (f.exists()) fc.setCurrentDirectory(f.isDirectory() ? f : f.getParentFile());
            }
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                tf.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        wrap.add(browse, BorderLayout.EAST);
        return wrap;
    }

    private JPanel createOtherArgsSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ModernTheme.BG_CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ModernTheme.BORDER, 1),
                "Custom Parameters (otherArgs)",
                TitledBorder.LEFT, TitledBorder.TOP,
                ModernTheme.FONT_UI_BOLD.deriveFont(13f),
                ModernTheme.TEXT_PRIMARY
            ),
            ModernTheme.padding(8, 12, 8, 12)
        ));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        toolbar.setBackground(ModernTheme.BG_CARD);
        JButton addBtn = ModernTheme.secondaryButton("+ Add");
        addBtn.setFont(addBtn.getFont().deriveFont(11f));
        JButton removeBtn = ModernTheme.secondaryButton("- Remove");
        removeBtn.setFont(removeBtn.getFont().deriveFont(11f));
        toolbar.add(addBtn);
        toolbar.add(removeBtn);
        toolbar.add(ModernTheme.mutedLabel("  Custom parameters passed to SUT as additional arguments"));
        panel.add(toolbar, BorderLayout.NORTH);

        // table initialized in constructor
        fieldWidgets.put("otherArgs", new JPanel()); // dummy, actual value read separately

        JScrollPane sp = new JScrollPane(otherArgsTable);
        sp.setPreferredSize(new Dimension(400, 100));
        sp.setBorder(BorderFactory.createLineBorder(ModernTheme.BORDER, 1));
        panel.add(sp, BorderLayout.CENTER);

        addBtn.addActionListener(e -> {
            otherArgsModel.addRow(new String[]{"", ""});
            otherArgsTable.editCellAt(otherArgsModel.getRowCount() - 1, 0);
        });
        removeBtn.addActionListener(e -> {
            int row = otherArgsTable.getSelectedRow();
            if (row >= 0) otherArgsModel.removeRow(row);
        });

        return panel;
    }

    // ── Populate / Read ───────────────────────────────────────────

    public void populateForm(Config config) {
        this.currentConfig = config;
        for (ConfigFieldMeta meta : fieldMetas) {
            if ("otherArgs".equals(meta.name())) continue;

            // For path fields, use the text field inside the browse wrapper
            JComponent widget;
            JTextField pathTF = pathFieldMap.get(meta.name());
            if (pathTF != null) {
                widget = pathTF;
            } else {
                widget = fieldWidgets.get(meta.name());
            }
            if (widget == null) continue;

            Object val = ConfigFieldDiscoverer.readValue(config, meta.name());
            setWidgetValue(meta, widget, val);
        }

        // Populate otherArgs table
        otherArgsModel.setRowCount(0);
        Map<String, JsonNode> other = config.getOtherArgs();
        if (other != null) {
            for (Map.Entry<String, JsonNode> e : other.entrySet()) {
                otherArgsModel.addRow(new String[]{e.getKey(), e.getValue().toString()});
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void setWidgetValue(ConfigFieldMeta meta, JComponent widget, Object val) {
        if (val == null) return;
        switch (meta.type()) {
            case BOOL -> {
                if (widget instanceof JCheckBox cb) cb.setSelected((Boolean) val);
            }
            case INT -> {
                if (widget instanceof JSpinner sp) sp.setValue(val);
            }
            case STRING -> {
                String s = val.toString();
                if (widget instanceof JTextField tf) tf.setText(s);
                else if (widget instanceof JComboBox<?> cb) cb.setSelectedItem(s);
            }
            case STRING_LIST -> {
                if (val instanceof List<?> list && widget instanceof JScrollPane sp) {
                    JTextArea ta = (JTextArea) sp.getViewport().getView();
                    ta.setText(String.join("\n", (List<String>) list));
                }
            }
            default -> {}
        }
    }

    /** Build a Config from the form values. */
    public Config buildConfig() {
        Config config = new Config();
        for (ConfigFieldMeta meta : fieldMetas) {
            if ("otherArgs".equals(meta.name())) continue;

            // For path fields, read from the text field inside the browse wrapper
            JComponent widget;
            JTextField pathTF = pathFieldMap.get(meta.name());
            if (pathTF != null) {
                widget = pathTF;
            } else {
                widget = fieldWidgets.get(meta.name());
            }
            if (widget == null) continue;

            String raw = getWidgetValue(meta, widget);
            if (raw == null) continue;
            Object typed = convertValue(meta, raw);
            if (typed != null) {
                ConfigFieldDiscoverer.writeValue(config, meta.name(), typed);
            }
        }

        // Build otherArgs from table
        Map<String, JsonNode> otherArgs = new LinkedHashMap<>();
        for (int i = 0; i < otherArgsModel.getRowCount(); i++) {
            String key = ((String) otherArgsModel.getValueAt(i, 0)).trim();
            String json = ((String) otherArgsModel.getValueAt(i, 1)).trim();
            if (key.isEmpty()) continue;
            try {
                JsonNode node = mapper.readTree(json.isEmpty() ? "\"\"" : json);
                otherArgs.put(key, node);
            } catch (JsonProcessingException ex) {
                // skip invalid rows
            }
        }
        ConfigFieldDiscoverer.writeValue(config, "otherArgs", new LinkedHashMap<>());
        // Add to existing via handleUnknown
        for (Map.Entry<String, JsonNode> e : otherArgs.entrySet()) {
            config.handleUnknown(e.getKey(), e.getValue());
        }

        return config;
    }

    private String getWidgetValue(ConfigFieldMeta meta, JComponent widget) {
        return switch (meta.type()) {
            case BOOL -> String.valueOf(((JCheckBox) widget).isSelected());
            case INT -> String.valueOf(((JSpinner) widget).getValue());
            case STRING -> {
                if (widget instanceof JTextField tf) yield tf.getText();
                else if (widget instanceof JComboBox<?> cb) {
                    Object sel = cb.getSelectedItem();
                    yield sel != null ? sel.toString() : "";
                }
                yield "";
            }
            case STRING_LIST -> {
                if (widget instanceof JScrollPane sp) {
                    JTextArea ta = (JTextArea) sp.getViewport().getView();
                    yield ta.getText();
                }
                yield "";
            }
            default -> "";
        };
    }

    private Object convertValue(ConfigFieldMeta meta, String raw) {
        return switch (meta.type()) {
            case BOOL -> Boolean.parseBoolean(raw);
            case INT -> {
                try { yield Integer.parseInt(raw); } catch (NumberFormatException e) { yield 0; }
            }
            case STRING -> raw;
            case STRING_LIST -> {
                List<String> list = new ArrayList<>();
                for (String line : raw.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) list.add(trimmed);
                }
                yield list;
            }
            default -> raw;
        };
    }

    // ── Load / Save ───────────────────────────────────────────────

    private void loadConfig() {
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle("Load Configuration");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Config config = Config.loadFromJson(fc.getSelectedFile().getAbsolutePath());
                populateForm(config);
                statusLabel.setText("Loaded: " + fc.getSelectedFile().getName());
                statusLabel.setForeground(ModernTheme.TEXT_SECONDARY);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to load config: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("Load failed");
                statusLabel.setForeground(ModernTheme.DANGER);
            }
        }
    }

    private void saveConfig() {
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle("Save Configuration As");
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (!file.getName().endsWith(".json")) {
                file = new File(file.getAbsolutePath() + ".json");
            }
            try {
                Config config = buildConfig();
                config.saveToJson(file.getAbsolutePath());
                statusLabel.setText("Saved: " + file.getName());
                statusLabel.setForeground(ModernTheme.SUCCESS);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to save config: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("Save failed");
                statusLabel.setForeground(ModernTheme.DANGER);
            }
        }
    }

    // ── Validation ────────────────────────────────────────────────

    public boolean validateForm() {
        Config config = buildConfig();
        List<String> errors = new ArrayList<>();

        int threadLimit = config.getThreadLimit();
        if (threadLimit < 1) errors.add("Thread limit must be >= 1");

        int enumLimit = config.getEnumLimit();
        if (enumLimit < 1) errors.add("Enum limit must be >= 1");

        String rulesPath = config.getRulesPath();
        if (rulesPath == null || !new File(rulesPath).exists())
            errors.add("Rules path does not exist: " + rulesPath);

        String targetPath = config.getTargetPath();
        if (targetPath == null || !new File(targetPath).exists())
            errors.add("Target path does not exist: " + targetPath);

        String bfuncPath = config.getBfuncPath();
        if (bfuncPath != null && !bfuncPath.isEmpty() && !new File(bfuncPath).exists())
            errors.add("Bfunc path does not exist: " + bfuncPath);

        String storeMode = config.getStoreMode();
        if (!Set.of("all", "fail", "none").contains(storeMode))
            errors.add("Store mode must be one of: all, fail, none");

        String bfuncPolicy = config.getBfuncPolicy();
        if (!Set.of("enum", "random").contains(bfuncPolicy))
            errors.add("Bfunc policy must be one of: enum, random");

        if (!errors.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                String.join("\n", errors),
                "Configuration Errors", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    public Config getCurrentConfig() {
        return currentConfig;
    }
}
