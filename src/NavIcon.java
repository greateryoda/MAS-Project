import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;

public class NavIcon implements Icon {
    public enum Shape { DASHBOARD, MAIL, AGENTS, CHIP }

    private final Shape shape;
    private Color color;

    public NavIcon(Shape shape, Color color) {
        this.shape = shape;
        this.color = color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public int getIconWidth()  { return 18; }
    public int getIconHeight() { return 18; }

    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.translate(x, y);
        switch (shape) {
            case DASHBOARD -> {
                g2.drawRoundRect(1, 1, 7, 7, 3, 3);
                g2.drawRoundRect(10, 1, 7, 7, 3, 3);
                g2.drawRoundRect(1, 10, 7, 7, 3, 3);
                g2.drawRoundRect(10, 10, 7, 7, 3, 3);
            }
            case MAIL -> {
                g2.drawRoundRect(1, 3, 16, 12, 3, 3);
                g2.drawPolyline(new int[] {2, 9, 16}, new int[] {4, 10, 4}, 3);
            }
            case AGENTS -> {
                g2.drawOval(3, 1, 6, 6);
                g2.drawArc(0, 9, 12, 10, 0, 180);
                g2.drawOval(11, 3, 5, 5);
                g2.drawArc(10, 10, 8, 8, 0, 180);
            }
            case CHIP -> {
                g2.drawRoundRect(4, 4, 10, 10, 3, 3);
                for (int i = 6; i <= 12; i += 3) {
                    g2.drawLine(i, 1, i, 4);
                    g2.drawLine(i, 14, i, 17);
                    g2.drawLine(1, i, 4, i);
                    g2.drawLine(14, i, 17, i);
                }
            }
        }
        g2.dispose();
    }
}
