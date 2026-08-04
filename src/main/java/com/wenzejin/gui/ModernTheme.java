package com.wenzejin.gui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Applies a fresh light green theme via FlatLaf.
 */
public final class ModernTheme {

    // ── Green palette ──────────────────────────────────────────────
    public static final Color ACCENT       = new Color(0x10, 0xB9, 0x81);  // emerald
    public static final Color ACCENT_DIM   = new Color(0x05, 0x9E, 0x6F);
    public static final Color ACCENT_LIGHT = new Color(0xD1, 0xFA, 0xE5);  // 10% tint

    public static final Color SUCCESS      = new Color(0x10, 0xB9, 0x81);
    public static final Color DANGER       = new Color(0xEF, 0x44, 0x44);
    public static final Color WARNING      = new Color(0xF5, 0x9E, 0x0B);

    public static final Color BG_CONTENT   = new Color(0xF8, 0xFA, 0xFC);
    public static final Color BG_CARD      = Color.WHITE;
    public static final Color BG_SIDEBAR   = new Color(0xF1, 0xF5, 0xF9);

    public static final Color TEXT_PRIMARY   = new Color(0x0F, 0x17, 0x2A);
    public static final Color TEXT_SECONDARY = new Color(0x64, 0x74, 0x8B);
    public static final Color TEXT_MUTED     = new Color(0x94, 0xA3, 0xB8);
    public static final Color BORDER         = new Color(0xE2, 0xE8, 0xF0);
    public static final Color BG_DARK_HOVER  = new Color(0xE8, 0xEE, 0xF4);

    // ── Fonts ────────────────────────────────────────────────────────
    public static final Font FONT_MONO;
    public static final Font FONT_UI;
    public static final Font FONT_UI_BOLD;
    public static final Font FONT_TITLE;
    public static final Font FONT_TAB;

    static {
        String[] monoCandidates = {"JetBrains Mono", "Fira Code", "SF Mono", "Cascadia Code", "Menlo", "Monospaced"};
        String monoFamily = "Monospaced";
        for (String c : monoCandidates) { if (fontExists(c)) { monoFamily = c; break; } }
        FONT_MONO = new Font(monoFamily, Font.PLAIN, 13);

        String[] uiCandidates = {"Inter", "Segoe UI", "Helvetica Neue", "Arial", "SansSerif"};
        String uiFamily = "SansSerif";
        for (String c : uiCandidates) { if (fontExists(c)) { uiFamily = c; break; } }
        FONT_UI      = new Font(uiFamily, Font.PLAIN, 13);
        FONT_UI_BOLD = new Font(uiFamily, Font.BOLD, 13);
        FONT_TITLE   = new Font(uiFamily, Font.BOLD, 20);
        FONT_TAB     = new Font(uiFamily, Font.PLAIN, 13);
    }

    private static boolean fontExists(String name) {
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    // ── Apply FlatLaf light green theme ────────────────────────────

    public static void apply() {
        FlatLightLaf.setup();

        UIManager.put("Panel.background",           BG_CONTENT);
        UIManager.put("OptionPane.background",      BG_CONTENT);
        UIManager.put("TextField.background",       BG_CARD);
        UIManager.put("FormattedTextField.background", BG_CARD);
        UIManager.put("TextArea.background",        BG_CARD);
        UIManager.put("ComboBox.background",        BG_CARD);
        UIManager.put("Spinner.background",         BG_CARD);
        UIManager.put("List.background",            BG_CARD);
        UIManager.put("Table.background",           BG_CARD);
        UIManager.put("Tree.background",            BG_CARD);

        UIManager.put("Label.foreground",           TEXT_PRIMARY);
        UIManager.put("TextField.foreground",       TEXT_PRIMARY);
        UIManager.put("TextArea.foreground",        TEXT_PRIMARY);
        UIManager.put("ComboBox.foreground",        TEXT_PRIMARY);
        UIManager.put("Table.foreground",           TEXT_PRIMARY);
        UIManager.put("Tree.foreground",            TEXT_PRIMARY);

        UIManager.put("TextField.caretForeground",  ACCENT);
        UIManager.put("TextArea.caretForeground",   ACCENT);

        UIManager.put("Component.focusColor",       ACCENT);
        UIManager.put("Button.default.focusColor",  ACCENT);

        UIManager.put("ProgressBar.foreground",     ACCENT);
        UIManager.put("ProgressBar.background",     ACCENT_LIGHT);

        UIManager.put("Table.gridColor",            BORDER);
        UIManager.put("TableHeader.background",     BG_SIDEBAR);
        UIManager.put("TableHeader.foreground",     TEXT_SECONDARY);

        UIManager.put("ScrollPane.background",      BG_CONTENT);

        UIManager.put("TabbedPane.background",      BG_CONTENT);
        UIManager.put("TabbedPane.selectedBackground", BG_CARD);
        UIManager.put("TabbedPane.hoverBackground",   BG_DARK_HOVER);

        UIManager.put("Separator.foreground",       BORDER);

        UIManager.put("ToolTip.background",         BG_SIDEBAR);
        UIManager.put("ToolTip.foreground",         TEXT_PRIMARY);

        UIManager.put("TitlePane.background",       BG_SIDEBAR);

        // Fonts
        UIManager.put("defaultFont",            FONT_UI);
        UIManager.put("Label.font",             FONT_UI);
        UIManager.put("Button.font",            FONT_UI_BOLD);
        UIManager.put("TextField.font",         FONT_UI);
        UIManager.put("TextArea.font",          FONT_UI);
        UIManager.put("ComboBox.font",          FONT_UI);
        UIManager.put("Spinner.font",           FONT_UI);
        UIManager.put("Table.font",             FONT_UI);
        UIManager.put("TableHeader.font",       FONT_UI_BOLD);
        UIManager.put("TabbedPane.font",        FONT_TAB);
        UIManager.put("TitledBorder.font",      FONT_UI_BOLD.deriveFont(13f));
        UIManager.put("Tree.font",              FONT_UI);
    }

    // ── Component factories ──────────────────────────────────────────

    public static JButton primaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(ACCENT);
        btn.setForeground(Color.WHITE);
        btn.setFont(FONT_UI_BOLD);
        btn.setBorder(new EmptyBorder(8, 20, 8, 20));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(ACCENT_DIM); }
            public void mouseExited(java.awt.event.MouseEvent e)  { btn.setBackground(ACCENT); }
        });
        return btn;
    }

    public static JButton secondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(BG_CARD);
        btn.setForeground(TEXT_PRIMARY);
        btn.setFont(FONT_UI_BOLD);
        btn.setBorder(new CompoundBorder(new LineBorder(BORDER, 1), new EmptyBorder(7, 19, 7, 19)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        return btn;
    }

    public static JLabel sectionHeader(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_UI_BOLD.deriveFont(15f));
        label.setForeground(TEXT_PRIMARY);
        label.setBorder(new EmptyBorder(12, 0, 4, 0));
        return label;
    }

    public static Border cardBorder() {
        return new CompoundBorder(new LineBorder(BORDER, 1), new EmptyBorder(16, 20, 16, 20));
    }

    public static Border padding(int top, int left, int bottom, int right) {
        return new EmptyBorder(top, left, bottom, right);
    }

    public static JPanel cardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(BG_CARD);
        panel.setBorder(cardBorder());
        return panel;
    }

    public static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT_MUTED);
        label.setFont(FONT_UI);
        return label;
    }

    private ModernTheme() {}
}
