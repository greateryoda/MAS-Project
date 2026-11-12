import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JButton;

public class PillButton extends JButton {
    public enum Kind { PRIMARY, SECONDARY, DANGER, SUCCESS, GHOST }

    private final Kind kind;

    public PillButton(String text, Kind kind) {
        super(text);
        this.kind = kind;
        setFont(Theme.font(Font.BOLD, 13));
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        setForeground(textColor());
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setForeground(enabled ? textColor() : Theme.TEXT_SOFT);
    }

    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, Math.max(d.height, 36));
    }

    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean hover = getModel().isRollover() && isEnabled();
        boolean pressed = getModel().isPressed();
        Color fill = fillColor();
        if (fill != null) {
            if (!isEnabled()) fill = Theme.mix(fill, Color.WHITE, 0.55);
            else if (pressed) fill = Theme.mix(fill, Color.BLACK, 0.12);
            else if (hover) fill = Theme.mix(fill, Color.BLACK, 0.06);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
        }
        Color stroke = strokeColor();
        if (stroke != null) {
            g2.setColor(isEnabled() ? stroke : Theme.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
        }
        g2.dispose();
        super.paintComponent(g);
    }

    private Color fillColor() {
        return switch (kind) {
            case PRIMARY -> Theme.ACCENT;
            case SUCCESS -> Theme.SUCCESS;
            case DANGER -> getModel().isRollover() ? Theme.DANGER_SOFT : Theme.SURFACE;
            case SECONDARY -> getModel().isRollover() ? Theme.NEUTRAL_SOFT : Theme.SURFACE;
            case GHOST -> getModel().isRollover() ? Theme.NEUTRAL_SOFT : null;
        };
    }

    private Color strokeColor() {
        return switch (kind) {
            case SECONDARY -> Theme.BORDER_STRONG;
            case DANGER -> new Color(0xFCA5A5);
            default -> null;
        };
    }

    private Color textColor() {
        return switch (kind) {
            case PRIMARY, SUCCESS -> Color.WHITE;
            case DANGER -> Theme.DANGER;
            case GHOST -> Theme.ACCENT;
            case SECONDARY -> Theme.TEXT;
        };
    }
}
