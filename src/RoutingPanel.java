import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class RoutingPanel extends Card {
    private final AgentNetworkView network = new AgentNetworkView();
    private final JPanel info = new JPanel();
    private final EmailAssistant assistant;

    public RoutingPanel(EmailAssistant assistant) {
        super(new BorderLayout(18, 10), 16);
        this.assistant = assistant;

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(Theme.heading("Routing decision"));
        header.add(Box.createVerticalStrut(2));
        header.add(Theme.caption("Which agent answers, why, and how the agents interact"));
        add(header, BorderLayout.NORTH);

        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setPreferredSize(new Dimension(300, 270));
        add(info, BorderLayout.WEST);
        add(network, BorderLayout.CENTER);
        reset();
    }

    public void reset() {
        network.setAgents(assistant.getRegistry().list(), assistant.getCritic().isEnabled());
        info.removeAll();
        JLabel empty = Theme.wrapped("Process an email to see how the RoutingAgent combines the routing "
                + "signals and selects the specialized agent.", 270, Theme.font(Font.PLAIN, 12), Theme.TEXT_MUTED);
        empty.setAlignmentX(0f);
        info.add(Box.createVerticalStrut(6));
        info.add(empty);
        info.add(Box.createVerticalGlue());
        info.revalidate();
        info.repaint();
    }

    public void setActive(WorkflowStage stage) {
        network.setActive(stage);
    }

    public void showDecision(Agent agent, RoutingDecision decision, Map<String, Double> scores,
                             List<String> signals, boolean arbiterConsulted) {
        network.setRouting(agent.getRole(), scores, arbiterConsulted);
        info.removeAll();

        JPanel who = new JPanel();
        who.setOpaque(false);
        who.setLayout(new BoxLayout(who, BoxLayout.X_AXIS));
        who.add(Avatar.forAgent(agent.getName(), agent.getRole(), 38));
        who.add(Box.createHorizontalStrut(10));
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.add(Theme.label(agent.getName(), Theme.font(Font.BOLD, 15), Theme.TEXT));
        names.add(Theme.caption("role " + agent.getRole()));
        who.add(names);
        put(info, who);

        info.add(Box.createVerticalStrut(10));
        JPanel conf = new JPanel(new BorderLayout(8, 0));
        conf.setOpaque(false);
        conf.add(Theme.label("Confidence", Theme.font(Font.BOLD, 12), Theme.TEXT), BorderLayout.WEST);
        conf.add(new ConfidenceBar(decision.getConfidence()), BorderLayout.CENTER);
        conf.add(Theme.label(String.format("%.2f", decision.getConfidence()), Theme.font(Font.BOLD, 12), Theme.TEXT),
                 BorderLayout.EAST);
        conf.setMaximumSize(new Dimension(300, 20));
        put(info, conf);

        info.add(Box.createVerticalStrut(10));
        put(info, Theme.overline("Signals"));
        info.add(Box.createVerticalStrut(3));
        StringBuilder text = new StringBuilder();
        for (String s : signals) text.append("• ").append(s).append("\n");
        put(info, Theme.wrapped(text.toString().trim(), 250, Theme.font(Font.PLAIN, 11.5f), Theme.TEXT_MUTED));

        info.add(Box.createVerticalStrut(10));
        put(info, Theme.overline("Agents involved"));
        info.add(Box.createVerticalStrut(4));
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        chips.setOpaque(false);
        chips.add(chip("RoutingAgent", Theme.ACCENT));
        if (arbiterConsulted) chips.add(chip("LLM arbiter", Theme.WARNING));
        chips.add(chip(agent.getName(), Theme.agentColor(agent.getRole())));
        chips.add(chip("Local LLM", new Color(0x0F766E)));
        if (assistant.getCritic().isEnabled()) chips.add(chip("CriticAgent", new Color(0x7C3AED)));
        chips.add(chip("User", Theme.TEXT_MUTED));
        chips.setPreferredSize(new Dimension(300, 92));
        chips.setMaximumSize(new Dimension(300, 92));
        put(info, chips);
        info.add(Box.createVerticalGlue());

        info.revalidate();
        info.repaint();
    }

    private void put(JPanel panel, JComponent c) {
        c.setAlignmentX(0f);
        panel.add(c);
    }

    private Badge chip(String text, Color color) {
        return new Badge(text, Theme.soft(color), Theme.mix(color, Color.BLACK, 0.2));
    }

    private static class ConfidenceBar extends JComponent {
        private final double value;

        ConfidenceBar(double value) {
            this.value = Math.max(0, Math.min(1, value));
            setPreferredSize(new Dimension(120, 8));
            setBorder(BorderFactory.createEmptyBorder());
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = getHeight() / 2 - 4;
            g2.setColor(Theme.NEUTRAL_SOFT);
            g2.fillRoundRect(0, y, getWidth(), 8, 8, 8);
            g2.setColor(value >= 0.75 ? Theme.SUCCESS : value >= 0.6 ? Theme.WARNING : Theme.DANGER);
            g2.fillRoundRect(0, y, (int) (getWidth() * value), 8, 8, 8);
            g2.dispose();
        }
    }
}
