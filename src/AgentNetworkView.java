import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

public class AgentNetworkView extends JComponent {
    private static final Stroke THIN = new BasicStroke(1.2f);
    private static final Stroke BOLD = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke DASHED = new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                                                         10f, new float[] {5f, 4f}, 0f);

    private List<String[]> agents = new ArrayList<>();
    private Map<String, Double> scores = new HashMap<>();
    private String selected;
    private boolean criticEnabled = true;
    private boolean arbiter;
    private WorkflowStage active;

    public AgentNetworkView() {
        setPreferredSize(new Dimension(520, 230));
        setMinimumSize(new Dimension(380, 200));
    }

    public void setAgents(List<Agent> list, boolean criticEnabled) {
        agents = new ArrayList<>();
        for (Agent a : list) agents.add(new String[] {a.getRole().toUpperCase(), a.getName()});
        this.criticEnabled = criticEnabled;
        scores = new HashMap<>();
        selected = null;
        arbiter = false;
        active = null;
        repaint();
    }

    public void setRouting(String selectedRole, Map<String, Double> scores, boolean arbiter) {
        this.selected = selectedRole.toUpperCase();
        this.scores = new HashMap<>(scores);
        this.arbiter = arbiter;
        repaint();
    }

    public void setActive(WorkflowStage stage) {
        this.active = stage;
        repaint();
    }

    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        Rectangle email = box(w * 0.09, h * 0.26, 84, 30);
        Rectangle user = box(w * 0.09, h * 0.76, 84, 30);
        Rectangle router = box(w * 0.33, h * 0.36, 118, 34);
        Rectangle critic = box(w * 0.33, h * 0.80, 104, 30);
        Rectangle llm = box(w * 0.91, h * 0.50, 86, 34);

        int agentWidth = (int) Math.min(168, Math.max(120, w * 0.26));
        int rowH = Math.min(30, (h - 10) / Math.max(1, agents.size()));
        int top = (h - rowH * agents.size()) / 2;
        Map<String, Rectangle> agentBoxes = new HashMap<>();
        for (int i = 0; i < agents.size(); i++) {
            agentBoxes.put(agents.get(i)[0], box(w * 0.635, top + rowH * i + rowH / 2.0, agentWidth, rowH - 6));
        }
        boolean routed = selected != null;
        Rectangle chosen = routed ? agentBoxes.get(selected) : null;

        edge(g2, email, router, active != null ? Theme.ACCENT : Theme.BORDER_STRONG, active != null ? BOLD : THIN, true);
        for (String[] a : agents) {
            Rectangle r = agentBoxes.get(a[0]);
            boolean isChosen = a[0].equals(selected);
            boolean voted = scores.containsKey(a[0]);
            Color c = isChosen ? Theme.ACCENT : voted ? Theme.mix(Theme.ACCENT, Color.WHITE, 0.45) : Theme.BORDER;
            edge(g2, router, r, c, isChosen ? BOLD : voted ? DASHED : THIN, isChosen);
        }
        if (chosen != null) {
            edge(g2, chosen, llm, Theme.ACCENT, BOLD, true);
            edge(g2, router, user, Theme.ACCENT, BOLD, true);
        } else {
            edge(g2, router, user, Theme.BORDER, THIN, true);
        }
        Color criticEdge = criticEnabled && routed ? Theme.mix(Theme.ACCENT, Color.WHITE, 0.3) : Theme.BORDER;
        edge(g2, router, critic, criticEdge, criticEnabled && routed ? BOLD : THIN, false);
        if (criticEnabled) curve(g2, critic, llm, routed ? Theme.mix(Theme.ACCENT, Color.WHITE, 0.45) : Theme.BORDER, h);
        if (arbiter) arc(g2, router, llm);

        node(g2, email, "Email", null, Theme.TEXT_MUTED, active == WorkflowStage.RECEIVED, false);
        node(g2, user, "User", null, Theme.TEXT_MUTED, active == WorkflowStage.VALIDATION, false);
        node(g2, router, "RoutingAgent", "mediator", Theme.ACCENT, isRouterStage(), true);
        node(g2, critic, "CriticAgent", criticEnabled ? null : "disabled",
             criticEnabled ? new Color(0x7C3AED) : Theme.TEXT_SOFT, active == WorkflowStage.CRITIC, criticEnabled);
        node(g2, llm, "Local LLM", "LM Studio", new Color(0x0F766E), active == WorkflowStage.GENERATION, true);
        for (String[] a : agents) {
            boolean isChosen = a[0].equals(selected);
            Double score = scores.get(a[0]);
            String sub = score != null ? String.format("vote %.2f", score) : null;
            Color c = isChosen || score != null ? Theme.agentColor(a[0]) : Theme.TEXT_SOFT;
            node(g2, agentBoxes.get(a[0]), a[1], sub, c,
                 isChosen && (active == WorkflowStage.CONTEXT || active == WorkflowStage.GENERATION), isChosen);
        }
        g2.dispose();
    }

    private boolean isRouterStage() {
        return active == WorkflowStage.ROUTING || active == WorkflowStage.SIGNALS
               || active == WorkflowStage.SELECTION || active == WorkflowStage.TRANSMISSION
               || active == WorkflowStage.PROPOSAL;
    }

    private Rectangle box(double cx, double cy, int width, int height) {
        return new Rectangle((int) (cx - width / 2.0), (int) (cy - height / 2.0), width, height);
    }

    private void node(Graphics2D g2, Rectangle r, String title, String sub, Color color, boolean active, boolean strong) {
        if (active) {
            g2.setColor(Theme.mix(color, Color.WHITE, 0.75));
            g2.fillRoundRect(r.x - 4, r.y - 4, r.width + 8, r.height + 8, 14, 14);
        }
        g2.setColor(strong ? Theme.soft(color) : Theme.SURFACE);
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        g2.setColor(strong ? color : Theme.BORDER_STRONG);
        g2.setStroke(new BasicStroke(strong ? 1.6f : 1f));
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);

        g2.setColor(strong ? Theme.mix(color, Color.BLACK, 0.25) : Theme.TEXT_MUTED);
        g2.setFont(Theme.font(Font.BOLD, sub == null ? 11.5f : 11f));
        FontMetrics fm = g2.getFontMetrics();
        String t = fit(title, fm, r.width - 10);
        if (sub == null) {
            g2.drawString(t, r.x + (r.width - fm.stringWidth(t)) / 2, r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2);
            return;
        }
        int ty = r.y + r.height / 2 - 1;
        g2.drawString(t, r.x + (r.width - fm.stringWidth(t)) / 2, ty);
        g2.setFont(Theme.font(Font.PLAIN, 9.5f));
        FontMetrics sm = g2.getFontMetrics();
        g2.setColor(Theme.TEXT_MUTED);
        g2.drawString(sub, r.x + (r.width - sm.stringWidth(sub)) / 2, ty + sm.getAscent() + 1);
    }

    private String fit(String text, FontMetrics fm, int width) {
        if (fm.stringWidth(text) <= width) return text;
        String t = text;
        while (t.length() > 3 && fm.stringWidth(t + "...") > width) t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    private void edge(Graphics2D g2, Rectangle a, Rectangle b, Color color, Stroke stroke, boolean arrow) {
        int x1, y1, x2, y2;
        if (Math.abs(a.getCenterX() - b.getCenterX()) < 20) {
            x1 = x2 = (int) a.getCenterX();
            boolean down = b.y > a.y;
            y1 = down ? a.y + a.height : a.y;
            y2 = down ? b.y : b.y + b.height;
        } else {
            boolean right = b.x > a.x;
            x1 = right ? a.x + a.width : a.x;
            x2 = right ? b.x : b.x + b.width;
            y1 = (int) a.getCenterY();
            y2 = (int) b.getCenterY();
        }
        g2.setColor(color);
        g2.setStroke(stroke);
        Path2D p = new Path2D.Double();
        p.moveTo(x1, y1);
        if (x1 == x2) {
            p.lineTo(x2, y2);
        } else {
            double mid = (x1 + x2) / 2.0;
            p.curveTo(mid, y1, mid, y2, x2, y2);
        }
        g2.draw(p);
        if (arrow) arrowHead(g2, x1 == x2 ? x2 : x2 - Integer.signum(x2 - x1) * 6, x1 == x2 ? y2 - Integer.signum(y2 - y1) * 6 : y2, x2, y2, color);
    }

    private void curve(Graphics2D g2, Rectangle critic, Rectangle llm, Color color, int h) {
        g2.setColor(color);
        g2.setStroke(DASHED);
        Path2D p = new Path2D.Double();
        double sx = critic.x + critic.width, sy = critic.getCenterY();
        double ex = llm.getCenterX(), ey = llm.y + llm.height;
        p.moveTo(sx, sy);
        p.curveTo(sx + 120, h - 4, ex, h - 4, ex, ey);
        g2.draw(p);
    }

    private void arc(Graphics2D g2, Rectangle router, Rectangle llm) {
        g2.setColor(new Color(0xD97706));
        g2.setStroke(DASHED);
        Path2D p = new Path2D.Double();
        double sx = router.getCenterX(), sy = router.y;
        double ex = llm.getCenterX(), ey = llm.y;
        p.moveTo(sx, sy);
        p.curveTo(sx, 2, ex, 2, ex, ey);
        g2.draw(p);
        g2.setFont(Theme.font(Font.BOLD, 9.5f));
        g2.drawString("LLM arbiter consulted", (int) ((sx + ex) / 2) - 50, 14);
    }

    private void arrowHead(Graphics2D g2, int fromX, int fromY, int x, int y, Color color) {
        double angle = Math.atan2(y - fromY, x - fromX);
        int size = 7;
        Path2D head = new Path2D.Double();
        head.moveTo(x, y);
        head.lineTo(x - size * Math.cos(angle - 0.45), y - size * Math.sin(angle - 0.45));
        head.lineTo(x - size * Math.cos(angle + 0.45), y - size * Math.sin(angle + 0.45));
        head.closePath();
        g2.setColor(color);
        g2.fill(head);
    }
}
