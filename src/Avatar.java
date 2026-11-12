import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

public class Avatar extends JComponent {
    private String text;
    private Color color;
    private final int size;

    public Avatar(String text, Color color, int size) {
        this.text = text;
        this.color = color;
        this.size = size;
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
    }

    public static Avatar forAgent(String name, String role, int size) {
        return new Avatar(Theme.initials(name), Theme.agentColor(role), size);
    }

    public void set(String text, Color color) {
        this.text = text;
        this.color = color;
        repaint();
    }

    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Theme.soft(color));
        g2.fillRoundRect(0, 0, size, size, size / 2, size / 2);
        g2.setColor(color);
        g2.setFont(Theme.font(Font.BOLD, size * 0.38f));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, (size - fm.stringWidth(text)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
