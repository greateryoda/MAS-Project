import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class DashboardPage extends Page {
    private final EmailAssistant assistant;
    private final ConnectionStatus status;
    private final ActivityLog log;
    private final Consumer<String> navigate;
    private final Consumer<String> openAgent;
    private final JPanel tiles = new JPanel(new GridLayout(1, 4, 16, 0));
    private final JPanel agentGrid = new JPanel(new GridLayout(0, 3, 14, 14));
    private final JPanel activity = new JPanel();

    public DashboardPage(EmailAssistant assistant, ConnectionStatus status, ActivityLog log,
                         Consumer<String> navigate, Consumer<String> openAgent) {
        super("Dashboard", "Overview of the agent organisation, the local LLM and this session");
        this.assistant = assistant;
        this.status = status;
        this.log = log;
        this.navigate = navigate;
        this.openAgent = openAgent;

        PillButton process = new PillButton("Process a new email", PillButton.Kind.PRIMARY);
        process.addActionListener(e -> navigate.accept(MainWindow.PROCESS));
        addAction(process);

        tiles.setOpaque(false);
        agentGrid.setOpaque(false);
        activity.setOpaque(false);
        activity.setLayout(new BoxLayout(activity, BoxLayout.Y_AXIS));

        Card agentsCard = new Card(new BorderLayout(0, 14), 18);
        agentsCard.add(sectionHeader("Agent organisation",
                "One RoutingAgent dispatches each email to a specialized BDI agent; CriticAgent reviews the drafts"),
                BorderLayout.NORTH);
        JPanel gridHolder = new ScrollablePanel(new BorderLayout());
        gridHolder.add(agentGrid, BorderLayout.NORTH);
        agentsCard.add(Theme.scroll(gridHolder, false), BorderLayout.CENTER);

        Card activityCard = new Card(new BorderLayout(0, 12), 18);
        activityCard.add(sectionHeader("Recent activity", "Emails processed in this session"), BorderLayout.NORTH);
        JPanel activityHolder = new ScrollablePanel(new BorderLayout());
        activityHolder.add(activity, BorderLayout.NORTH);
        activityCard.add(Theme.scroll(activityHolder, false), BorderLayout.CENTER);

        Card routingCard = new Card(new BorderLayout(0, 10), 18);
        routingCard.add(sectionHeader("Routing strategy", "Each signal votes for an agent"), BorderLayout.NORTH);
        routingCard.add(Theme.wrapped(
                "• Thread (0.9): an agent already answered this thread\n"
                + "• Contact (1.0): sender found in contacts.json\n"
                + "• Keywords (0.5 to 0.75): agent keywords in the email\n"
                + "• Domain or mailbox (0.6): e.g. etu.univ.fr, scolarite@\n"
                + "If the winner has less than 75% of the votes or a score below 0.7, "
                + "the local LLM arbitrates; otherwise GenericAgent answers.",
                300, Theme.font(Font.PLAIN, 12), Theme.TEXT_MUTED), BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(0, 16));
        right.setOpaque(false);
        right.add(activityCard, BorderLayout.CENTER);
        right.add(routingCard, BorderLayout.SOUTH);
        right.setPreferredSize(new Dimension(350, 100));

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2; c.weightx = 1; c.insets = new Insets(0, 0, 16, 0);
        body.add(tiles, c);
        c.gridy = 1; c.gridwidth = 1; c.weighty = 1; c.insets = new Insets(0, 0, 0, 16);
        body.add(agentsCard, c);
        c.gridx = 1; c.weightx = 0; c.insets = new Insets(0, 0, 0, 0);
        body.add(right, c);
        add(body, BorderLayout.CENTER);

        status.addListener(this::refreshTiles);
        log.addListener(this::refresh);
        refresh();
    }

    public void onShow() {
        refresh();
    }

    private void refresh() {
        refreshTiles();
        refreshAgents();
        refreshActivity();
        revalidate();
        repaint();
    }

    private void refreshTiles() {
        tiles.removeAll();
        int agents = assistant.getRegistry().list().size();
        tiles.add(tile("Specialized agents", String.valueOf(agents),
                "+ RoutingAgent and CriticAgent", Theme.ACCENT));
        Color llmColor = switch (status.getState()) {
            case ONLINE -> status.isModelAvailable() ? Theme.SUCCESS : Theme.WARNING;
            case OFFLINE -> Theme.DANGER;
            default -> Theme.TEXT_SOFT;
        };
        tiles.add(tile("Local LLM", status.label(), status.getModel(), llmColor));
        tiles.add(tile("Emails processed", String.valueOf(log.total()),
                log.draftsGenerated() + " draft(s) generated", Theme.INFO));
        tiles.add(tile("Replies validated",
                String.valueOf(log.count(ActivityLog.Outcome.ACCEPTED, ActivityLog.Outcome.EDITED)),
                log.count(ActivityLog.Outcome.EDITED) + " edited, " + log.count(ActivityLog.Outcome.DISCARDED) + " refused",
                Theme.SUCCESS));
        tiles.revalidate();
        tiles.repaint();
    }

    private void refreshAgents() {
        agentGrid.removeAll();
        for (Agent agent : assistant.getRegistry().list()) agentGrid.add(agentCard(agent));
        agentGrid.add(criticCard());
    }

    private void refreshActivity() {
        activity.removeAll();
        if (log.entries().isEmpty()) {
            activity.add(Theme.wrapped("No email processed yet. Open \"Process email\" and load a demonstration email.",
                                       280, Theme.font(Font.PLAIN, 12), Theme.TEXT_MUTED));
        }
        for (ActivityLog.Entry e : log.entries()) {
            JPanel row = new JPanel(new BorderLayout(10, 2));
            row.setOpaque(false);
            row.setAlignmentX(0f);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(Theme.label(shorten(e.subject(), 34), Theme.font(Font.BOLD, 12.5f), Theme.TEXT));
            text.add(Theme.caption(e.time() + "  ·  " + e.agent()));
            row.add(text, BorderLayout.CENTER);
            row.add(outcomeBadge(e.outcome()), BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            activity.add(row);
            activity.add(Box.createVerticalStrut(10));
        }
        activity.revalidate();
    }

    private JComponent tile(String label, String value, String caption, Color color) {
        Card card = new Card(new BorderLayout(0, 6), 16);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Theme.overline(label), BorderLayout.WEST);
        top.add(new Avatar("", color, 10), BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);
        card.add(Theme.label(value, Theme.font(Font.BOLD, 24), Theme.TEXT), BorderLayout.CENTER);
        card.add(Theme.caption(caption), BorderLayout.SOUTH);
        return card;
    }

    private JComponent agentCard(Agent agent) {
        Card card = new Card(new BorderLayout(0, 10), 14);
        card.setColors(Theme.SURFACE_ALT, Theme.BORDER);
        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        head.add(Avatar.forAgent(agent.getName(), agent.getRole(), 36), BorderLayout.WEST);
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.add(Theme.label(agent.getName(), Theme.font(Font.BOLD, 13.5f), Theme.TEXT));
        names.add(Theme.caption("role " + agent.getRole()));
        head.add(names, BorderLayout.CENTER);
        card.add(head, BorderLayout.NORTH);
        card.add(Theme.wrapped(shorten(agent.getDescription(), 120), 175, Theme.font(Font.PLAIN, 12), Theme.TEXT_MUTED),
                 BorderLayout.CENTER);
        String facts;
        try {
            facts = assistant.getRegistry().contextSize(agent.getRole()) + " facts  ·  "
                    + assistant.getRegistry().memorisedThreads(agent.getRole()) + " thread(s) memorised  ·  "
                    + agent.getKeywords().size() + " keywords";
        } catch (IOException e) {
            facts = "Configuration unreadable: " + e.getMessage();
        }
        card.add(Theme.label(facts, Theme.font(Font.PLAIN, 11), Theme.TEXT_SOFT), BorderLayout.SOUTH);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setToolTipText("Open " + agent.getName() + " in the agent manager");
        card.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { openAgent.accept(agent.getRole()); }
        });
        return card;
    }

    private JComponent criticCard() {
        CriticAgent critic = assistant.getCritic();
        Color violet = new Color(0x7C3AED);
        Card card = new Card(new BorderLayout(0, 10), 14);
        card.setColors(Theme.soft(violet), Theme.mix(violet, Color.WHITE, 0.75));
        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        head.add(new Avatar("CR", violet, 36), BorderLayout.WEST);
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.add(Theme.label(critic.getName(), Theme.font(Font.BOLD, 13.5f), Theme.TEXT));
        names.add(Theme.caption("support agent, not routable"));
        head.add(names, BorderLayout.CENTER);
        card.add(head, BorderLayout.NORTH);
        card.add(Theme.wrapped("Reviews every draft against the rules and context of the agent that wrote it, "
                + "and gives a score and remarks before you decide.", 175, Theme.font(Font.PLAIN, 12), Theme.TEXT_MUTED),
                BorderLayout.CENTER);
        card.add(Theme.label(critic.isEnabled() ? "Enabled" : "Disabled (see Local LLM page)",
                Theme.font(Font.BOLD, 11), critic.isEnabled() ? violet : Theme.TEXT_SOFT), BorderLayout.SOUTH);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { navigate.accept(MainWindow.LLM); }
        });
        return card;
    }

    private Badge outcomeBadge(ActivityLog.Outcome outcome) {
        return switch (outcome) {
            case ACCEPTED -> new Badge("Accepted", Theme.SUCCESS_SOFT, Theme.SUCCESS);
            case EDITED -> new Badge("Edited", Theme.INFO_SOFT, Theme.INFO);
            case DISCARDED -> new Badge("Refused", Theme.NEUTRAL_SOFT, Theme.TEXT_MUTED);
            case NO_REPLY -> new Badge("No reply", Theme.WARNING_SOFT, Theme.WARNING);
            case ERROR -> new Badge("Error", Theme.DANGER_SOFT, Theme.DANGER);
        };
    }

    private JComponent sectionHeader(String title, String caption) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel t = Theme.heading(title);
        JLabel c = Theme.caption(caption);
        p.add(t);
        p.add(Box.createVerticalStrut(2));
        p.add(c);
        return p;
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
