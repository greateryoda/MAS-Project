import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.MetalLookAndFeel;

public final class Theme {
    public static final Color BACKGROUND = new Color(0xF4F6FA);
    public static final Color SURFACE = Color.WHITE;
    public static final Color SURFACE_ALT = new Color(0xF8FAFC);
    public static final Color BORDER = new Color(0xE2E8F0);
    public static final Color BORDER_STRONG = new Color(0xCBD5E1);
    public static final Color TEXT = new Color(0x0F172A);
    public static final Color TEXT_MUTED = new Color(0x64748B);
    public static final Color TEXT_SOFT = new Color(0x94A3B8);

    public static final Color SIDEBAR = new Color(0x0F172A);
    public static final Color SIDEBAR_HOVER = new Color(0x1E293B);
    public static final Color SIDEBAR_ACTIVE = new Color(0x312E81);
    public static final Color SIDEBAR_TEXT = new Color(0xCBD5E1);
    public static final Color SIDEBAR_MUTED = new Color(0x64748B);

    public static final Color ACCENT = new Color(0x4F46E5);
    public static final Color ACCENT_DARK = new Color(0x4338CA);
    public static final Color ACCENT_SOFT = new Color(0xEEF2FF);
    public static final Color SUCCESS = new Color(0x16A34A);
    public static final Color SUCCESS_SOFT = new Color(0xDCFCE7);
    public static final Color WARNING = new Color(0xD97706);
    public static final Color WARNING_SOFT = new Color(0xFEF3C7);
    public static final Color DANGER = new Color(0xDC2626);
    public static final Color DANGER_SOFT = new Color(0xFEE2E2);
    public static final Color INFO = new Color(0x2563EB);
    public static final Color INFO_SOFT = new Color(0xDBEAFE);
    public static final Color NEUTRAL = new Color(0x94A3B8);
    public static final Color NEUTRAL_SOFT = new Color(0xF1F5F9);

    private static final List<Color> AGENT_COLORS = List.of(
            new Color(0x4F46E5), new Color(0x0D9488), new Color(0xD97706), new Color(0xDB2777),
            new Color(0x0284C7), new Color(0x7C3AED), new Color(0x16A34A), new Color(0xEA580C));

    public static final String FONT_FAMILY = pickFont("Inter", "Segoe UI", "SF Pro Text", "Helvetica Neue",
                                                      "Noto Sans", "DejaVu Sans");
    public static final String MONO_FAMILY = pickFont("JetBrains Mono", "Menlo", "Consolas", "DejaVu Sans Mono");

    private Theme() {}

    public static void install() {
        UIManager.put("swing.boldMetal", Boolean.FALSE);
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception e) {
            System.err.println("Could not set the look and feel: " + e.getMessage());
        }
        FontUIResource base = new FontUIResource(font(Font.PLAIN, 13));
        for (Object key : UIManager.getLookAndFeelDefaults().keySet().toArray()) {
            if (key.toString().endsWith(".font")) UIManager.put(key, base);
        }
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("TextField.selectionBackground", ACCENT_SOFT);
        UIManager.put("TextArea.selectionBackground", ACCENT_SOFT);
        UIManager.put("TextField.selectionForeground", TEXT);
        UIManager.put("TextArea.selectionForeground", TEXT);
        UIManager.put("List.selectionBackground", ACCENT_SOFT);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("ComboBox.selectionBackground", ACCENT_SOFT);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("ComboBox.background", SURFACE);
        UIManager.put("ToolTip.background", TEXT);
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(4, 8, 4, 8));
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("Spinner.border", new RoundBorder(BORDER_STRONG, 8, 2));
        UIManager.put("CheckBox.background", SURFACE);
    }

    public static Font font(int style, float size) {
        return new Font(FONT_FAMILY, style, 13).deriveFont(style, size);
    }

    public static Font mono(float size) {
        return new Font(MONO_FAMILY, Font.PLAIN, 13).deriveFont(size);
    }

    public static Color agentColor(String role) {
        return AGENT_COLORS.get(Math.floorMod(role.toUpperCase().hashCode(), AGENT_COLORS.size()));
    }

    public static Color soft(Color c) {
        return mix(c, Color.WHITE, 0.88);
    }

    public static Color mix(Color a, Color b, double ratio) {
        return new Color((int) (a.getRed() * (1 - ratio) + b.getRed() * ratio),
                         (int) (a.getGreen() * (1 - ratio) + b.getGreen() * ratio),
                         (int) (a.getBlue() * (1 - ratio) + b.getBlue() * ratio));
    }

    public static String initials(String name) {
        String clean = name.replace("Agent", "").trim();
        String[] parts = clean.split("(?=[A-Z])|\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isBlank() && sb.length() < 2) sb.append(Character.toUpperCase(p.charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    public static JLabel title(String text) {
        return label(text, font(Font.BOLD, 22), TEXT);
    }

    public static JLabel heading(String text) {
        return label(text, font(Font.BOLD, 15), TEXT);
    }

    public static JLabel caption(String text) {
        return label(text, font(Font.PLAIN, 12), TEXT_MUTED);
    }

    public static JLabel overline(String text) {
        return label(text.toUpperCase(), font(Font.BOLD, 11), TEXT_SOFT);
    }

    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    public static JLabel wrapped(String text, int width, Font font, Color color) {
        JLabel l = label(html(text, width), font, color);
        l.setVerticalAlignment(JLabel.TOP);
        return l;
    }

    public static String html(String text, int width) {
        String escaped = escape(text).replace("\n", "<br>");
        return "<html><div style='width:" + width + "px'>" + escaped + "</div></html>";
    }

    public static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static JTextField field() {
        JTextField f = new JTextField();
        f.setFont(font(Font.PLAIN, 13));
        f.setForeground(TEXT);
        f.setBackground(SURFACE);
        f.setCaretColor(TEXT);
        f.setBorder(fieldBorder());
        return f;
    }

    public static JTextArea area(int rows) {
        JTextArea a = new JTextArea(rows, 20);
        a.setFont(font(Font.PLAIN, 13));
        a.setForeground(TEXT);
        a.setBackground(SURFACE);
        a.setCaretColor(TEXT);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return a;
    }

    public static JScrollPane scroll(Component c, boolean framed) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(framed ? new RoundBorder(BORDER_STRONG, 10, 1) : BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(c.getBackground());
        sp.setBackground(c.getBackground());
        sp.getVerticalScrollBar().setUI(new ThinScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new ThinScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 10));
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    public static Border fieldBorder() {
        return BorderFactory.createCompoundBorder(new RoundBorder(BORDER_STRONG, 8, 1),
                                                  BorderFactory.createEmptyBorder(7, 10, 7, 10));
    }

    public static JComponent fieldGroup(String label, JComponent field) {
        javax.swing.JPanel p = new javax.swing.JPanel();
        p.setOpaque(false);
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        JLabel l = label(label, font(Font.BOLD, 12), TEXT);
        l.setAlignmentX(0f);
        field.setAlignmentX(0f);
        p.add(l);
        p.add(javax.swing.Box.createVerticalStrut(5));
        p.add(field);
        return p;
    }

    private static String pickFont(String... candidates) {
        List<String> available = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                                  .getAvailableFontFamilyNames());
        for (String c : candidates) {
            if (available.contains(c)) return c;
        }
        return Font.SANS_SERIF;
    }
}
