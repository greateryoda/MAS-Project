import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

public class WorkflowPanel extends Card {
    private final Map<WorkflowStage, StageRow> rows = new EnumMap<>(WorkflowStage.class);
    private final JLabel clock = Theme.caption("Idle");
    private final Timer timer;
    private long startedAt;
    private boolean running;
    private int spin;

    public WorkflowPanel() {
        super(new BorderLayout(0, 12), 16);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Theme.heading("Multi-agent workflow"));
        titles.add(Box.createVerticalStrut(2));
        titles.add(Theme.caption("Live status of each step"));
        header.add(titles, BorderLayout.WEST);
        header.add(clock, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        ScrollablePanel list = new ScrollablePanel(null);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(true);
        list.setBackground(Theme.SURFACE);
        int index = 1;
        WorkflowStage[] stages = WorkflowStage.values();
        for (WorkflowStage stage : stages) {
            StageRow row = new StageRow(stage, index++, stage == stages[0], stage == stages[stages.length - 1]);
            rows.put(stage, row);
            list.add(row);
        }
        add(Theme.scroll(list, false), BorderLayout.CENTER);

        timer = new Timer(90, e -> tick());
    }

    public void reset() {
        rows.values().forEach(StageRow::reset);
        startedAt = System.currentTimeMillis();
        running = true;
        timer.start();
        tick();
    }

    public void setAgentName(String name) {
        rows.get(WorkflowStage.CONTEXT).setActor(name);
        rows.get(WorkflowStage.GENERATION).setActor(name + " with the local LLM");
    }

    public void update(WorkflowStage stage, StageStatus status, String detail) {
        rows.get(stage).update(status, detail);
        if (stage == WorkflowStage.VALIDATION && status != StageStatus.PROCESSING) stop();
        if (status == StageStatus.FAILED && stage != WorkflowStage.CRITIC) stop();
    }

    public void stop() {
        running = false;
        timer.stop();
        tick();
    }

    private void tick() {
        spin = (spin + 30) % 360;
        double seconds = (System.currentTimeMillis() - startedAt) / 1000.0;
        clock.setText(startedAt == 0 ? "Idle" : (running ? "Running " : "Done in ") + String.format("%.1f s", seconds));
        rows.values().stream().filter(r -> r.status == StageStatus.PROCESSING).forEach(JPanel::repaint);
    }

    private class StageRow extends JPanel {
        private static final int GUTTER = 40;
        private final WorkflowStage stage;
        private final int index;
        private final boolean first;
        private final boolean last;
        private final JLabel actor;
        private final JLabel detail = Theme.label("", Theme.font(Font.PLAIN, 12), Theme.TEXT_MUTED);
        private final JLabel time = Theme.label("", Theme.font(Font.PLAIN, 11), Theme.TEXT_SOFT);
        private final Badge badge = Badge.of(StageStatus.PENDING);
        private StageStatus status = StageStatus.PENDING;
        private long since;

        StageRow(WorkflowStage stage, int index, boolean first, boolean last) {
            super(new BorderLayout());
            this.stage = stage;
            this.index = index;
            this.first = first;
            this.last = last;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2, GUTTER, 12, 2));
            setAlignmentX(0f);

            JPanel top = new JPanel(new BorderLayout(8, 0));
            top.setOpaque(false);
            top.add(Theme.label(stage.getTitle(), Theme.font(Font.BOLD, 13), Theme.TEXT), BorderLayout.CENTER);
            JPanel right = new JPanel(new BorderLayout(6, 0));
            right.setOpaque(false);
            right.add(time, BorderLayout.WEST);
            right.add(badge, BorderLayout.EAST);
            top.add(right, BorderLayout.EAST);

            actor = Theme.label(stage.getActor(), Theme.font(Font.PLAIN, 11), Theme.ACCENT);
            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            actor.setAlignmentX(0f);
            detail.setAlignmentX(0f);
            body.add(actor);
            body.add(detail);

            add(top, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
        }

        void reset() {
            status = StageStatus.PENDING;
            badge.setStatus(status);
            actor.setText(stage.getActor());
            detail.setText("");
            time.setText("");
            repaint();
        }

        void setActor(String text) {
            actor.setText(text);
        }

        void update(StageStatus newStatus, String text) {
            if (newStatus == StageStatus.PROCESSING && status != StageStatus.PROCESSING) {
                since = System.currentTimeMillis();
                time.setText("");
            }
            if ((newStatus == StageStatus.COMPLETED || newStatus == StageStatus.FAILED)
                    && status == StageStatus.PROCESSING) {
                long ms = System.currentTimeMillis() - since;
                time.setText(ms >= 100 ? String.format("%.1f s", ms / 1000.0) : "");
            }
            status = newStatus;
            badge.setStatus(newStatus);
            detail.setText(text == null || text.isBlank() ? "" : Theme.html(text, 205));
            revalidate();
            repaint();
        }

        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int d = 24, cx = 14, cy = 2 + d / 2;
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(status == StageStatus.COMPLETED ? Theme.mix(Theme.SUCCESS, Color.WHITE, 0.5) : Theme.BORDER);
            if (!last) g2.drawLine(cx, cy + d / 2 + 2, cx, getHeight());
            if (!first) {
                g2.setColor(Theme.BORDER);
                g2.drawLine(cx, 0, cx, 2);
            }
            int x = cx - d / 2, y = cy - d / 2;
            switch (status) {
                case COMPLETED -> {
                    g2.setColor(Theme.SUCCESS);
                    g2.fillOval(x, y, d, d);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawPolyline(new int[] {x + 7, x + 11, x + 17}, new int[] {y + 12, y + 16, y + 8}, 3);
                }
                case FAILED -> {
                    g2.setColor(Theme.DANGER);
                    g2.fillOval(x, y, d, d);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x + 8, y + 8, x + 16, y + 16);
                    g2.drawLine(x + 16, y + 8, x + 8, y + 16);
                }
                case PROCESSING -> {
                    g2.setColor(Theme.INFO_SOFT);
                    g2.fillOval(x, y, d, d);
                    g2.setColor(Theme.INFO);
                    g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawArc(x + 3, y + 3, d - 6, d - 6, -spin, 260);
                }
                case SKIPPED -> {
                    g2.setColor(Theme.NEUTRAL_SOFT);
                    g2.fillOval(x, y, d, d);
                    g2.setColor(Theme.TEXT_SOFT);
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x + 8, cy, x + 16, cy);
                }
                default -> {
                    g2.setColor(Theme.SURFACE);
                    g2.fillOval(x, y, d, d);
                    g2.setColor(Theme.BORDER_STRONG);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(x, y, d, d);
                    g2.setColor(Theme.TEXT_MUTED);
                    g2.setFont(Theme.font(Font.BOLD, 11));
                    FontMetrics fm = g2.getFontMetrics();
                    String n = String.valueOf(index);
                    g2.drawString(n, cx - fm.stringWidth(n) / 2, cy + fm.getAscent() / 2 - 1);
                }
            }
            g2.dispose();
        }
    }
}
