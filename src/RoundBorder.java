import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.border.AbstractBorder;

public class RoundBorder extends AbstractBorder {
    private final Color color;
    private final int radius;
    private final int padding;

    public RoundBorder(Color color, int radius, int padding) {
        this.color = color;
        this.radius = radius;
        this.padding = padding;
    }

    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(c.isEnabled() ? color : Theme.BORDER);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        g2.dispose();
    }

    public Insets getBorderInsets(Component c) {
        return new Insets(padding, padding, padding, padding);
    }

    public Insets getBorderInsets(Component c, Insets insets) {
        insets.set(padding, padding, padding, padding);
        return insets;
    }
}
