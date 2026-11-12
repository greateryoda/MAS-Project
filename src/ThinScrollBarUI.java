import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

public class ThinScrollBarUI extends BasicScrollBarUI {

    protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
        g.setColor(c.getParent() != null ? c.getParent().getBackground() : Theme.SURFACE);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
        if (r.isEmpty() || !scrollbar.isEnabled()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isThumbRollover() ? Theme.TEXT_SOFT : Theme.BORDER_STRONG);
        g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6);
        g2.dispose();
    }

    protected JButton createDecreaseButton(int orientation) {
        return emptyButton();
    }

    protected JButton createIncreaseButton(int orientation) {
        return emptyButton();
    }

    private JButton emptyButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        return b;
    }
}
