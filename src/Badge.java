import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JLabel;

public class Badge extends JLabel {
    private Color fill;

    public Badge(String text, Color fill, Color foreground) {
        super(text);
        setFont(Theme.font(Font.BOLD, 11));
        setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
        setStyle(text, fill, foreground);
    }

    public static Badge of(StageStatus status) {
        Badge b = new Badge("", Theme.NEUTRAL_SOFT, Theme.TEXT_MUTED);
        b.setStatus(status);
        return b;
    }

    public void setStatus(StageStatus status) {
        switch (status) {
            case PROCESSING -> setStyle(status.getLabel(), Theme.INFO_SOFT, Theme.INFO);
            case COMPLETED -> setStyle(status.getLabel(), Theme.SUCCESS_SOFT, Theme.SUCCESS);
            case FAILED -> setStyle(status.getLabel(), Theme.DANGER_SOFT, Theme.DANGER);
            case SKIPPED -> setStyle(status.getLabel(), Theme.NEUTRAL_SOFT, Theme.TEXT_SOFT);
            default -> setStyle(status.getLabel(), Theme.NEUTRAL_SOFT, Theme.TEXT_MUTED);
        }
    }

    public void setStyle(String text, Color fill, Color foreground) {
        setText(text);
        this.fill = fill;
        setForeground(foreground);
        repaint();
    }

    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}
