import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class MainWindow extends JFrame {
    public static final String DASHBOARD = "Dashboard";
    public static final String PROCESS = "Process email";
    public static final String AGENTS = "Agents";
    public static final String LLM = "Local LLM";

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, NavItem> navItems = new LinkedHashMap<>();
    private final ConnectionStatus status;
    private final JLabel statusLabel = Theme.label("", Theme.font(Font.BOLD, 12), Theme.SIDEBAR_TEXT);
    private final JLabel modelLabel = Theme.label("", Theme.font(Font.PLAIN, 11), Theme.SIDEBAR_MUTED);
    private final StatusDot statusDot = new StatusDot();

    public MainWindow(EmailAssistant assistant, LmStudioClient llm, SwingValidator validator, List<DemoEmail> demos) {
        super("MAS Mail Assistant");
        this.status = new ConnectionStatus(llm);
        ActivityLog log = new ActivityLog();

        pages.put(DASHBOARD, new DashboardPage(assistant, status, log, this::show, this::openAgent));
        pages.put(PROCESS, new ProcessPage(assistant, validator, demos, log, status));
        pages.put(AGENTS, new AgentsPage(assistant));
        pages.put(LLM, new LlmPage(llm, status, assistant.getCritic()));
        pages.forEach((name, page) -> content.add(page, name));

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        setMinimumSize(new Dimension(1280, 780));
        setSize(1500, 920);
        setLocationRelativeTo(null);

        status.addListener(this::updateStatus);
        updateStatus();
        status.check();
        show(DASHBOARD);
    }

    public void show(String name) {
        cards.show(content, name);
        navItems.forEach((n, item) -> item.setActive(n.equals(name)));
        pages.get(name).onShow();
    }

    private void openAgent(String role) {
        show(AGENTS);
        ((AgentsPage) pages.get(AGENTS)).select(role);
    }

    private JComponent buildSidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.SIDEBAR);
        side.setPreferredSize(new Dimension(236, 0));
        side.setBorder(BorderFactory.createEmptyBorder(22, 14, 18, 14));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.X_AXIS));
        Avatar logo = new Avatar("M", Theme.ACCENT, 36);
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.add(Theme.label("MAS Mail Assistant", Theme.font(Font.BOLD, 14), Color.WHITE));
        names.add(Theme.label("Multi-agent replies, local LLM", Theme.font(Font.PLAIN, 11), Theme.SIDEBAR_MUTED));
        brand.add(logo);
        brand.add(Box.createHorizontalStrut(10));
        brand.add(names);
        brand.setAlignmentX(0f);

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.add(brand);
        nav.add(Box.createVerticalStrut(30));
        JLabel menu = Theme.label("MENU", Theme.font(Font.BOLD, 10), Theme.SIDEBAR_MUTED);
        menu.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 0));
        menu.setAlignmentX(0f);
        nav.add(menu);
        addNav(nav, DASHBOARD, NavIcon.Shape.DASHBOARD);
        addNav(nav, PROCESS, NavIcon.Shape.MAIL);
        addNav(nav, AGENTS, NavIcon.Shape.AGENTS);
        addNav(nav, LLM, NavIcon.Shape.CHIP);

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBorder(BorderFactory.createEmptyBorder(12, 10, 0, 0));
        JPanel line = new JPanel();
        line.setOpaque(false);
        line.setLayout(new BoxLayout(line, BoxLayout.X_AXIS));
        line.add(statusDot);
        line.add(Box.createHorizontalStrut(8));
        line.add(statusLabel);
        line.setAlignmentX(0f);
        modelLabel.setAlignmentX(0f);
        JLabel privacy = Theme.label("No email is ever sent automatically", Theme.font(Font.PLAIN, 11), Theme.SIDEBAR_MUTED);
        privacy.setAlignmentX(0f);
        footer.add(Theme.label("LOCAL LLM", Theme.font(Font.BOLD, 10), Theme.SIDEBAR_MUTED));
        footer.add(Box.createVerticalStrut(8));
        footer.add(line);
        footer.add(Box.createVerticalStrut(4));
        footer.add(modelLabel);
        footer.add(Box.createVerticalStrut(14));
        footer.add(privacy);
        footer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        footer.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { show(LLM); }
        });

        side.add(nav, BorderLayout.NORTH);
        side.add(footer, BorderLayout.SOUTH);
        return side;
    }

    private void addNav(JPanel nav, String name, NavIcon.Shape shape) {
        NavItem item = new NavItem(name, shape);
        item.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { show(name); }
        });
        navItems.put(name, item);
        nav.add(item);
        nav.add(Box.createVerticalStrut(4));
    }

    private void updateStatus() {
        statusLabel.setText(status.label());
        modelLabel.setText(status.getModel());
        statusDot.setColor(switch (status.getState()) {
            case ONLINE -> status.isModelAvailable() ? Theme.SUCCESS : Theme.WARNING;
            case OFFLINE -> Theme.DANGER;
            default -> Theme.SIDEBAR_MUTED;
        });
    }

    private static class StatusDot extends JComponent {
        private Color color = Theme.SIDEBAR_MUTED;

        StatusDot() {
            setPreferredSize(new Dimension(9, 9));
            setMaximumSize(new Dimension(9, 9));
        }

        void setColor(Color c) {
            color = c;
            repaint();
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(0, 0, 9, 9);
            g2.dispose();
        }
    }

    private static class NavItem extends JPanel {
        private final NavIcon icon;
        private final JLabel label;
        private boolean active;
        private boolean hover;

        NavItem(String name, NavIcon.Shape shape) {
            super(new BorderLayout());
            setOpaque(false);
            setAlignmentX(0f);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            setPreferredSize(new Dimension(200, 40));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            icon = new NavIcon(shape, Theme.SIDEBAR_TEXT);
            label = Theme.label(name, Theme.font(Font.PLAIN, 13), Theme.SIDEBAR_TEXT);
            label.setIcon(icon);
            label.setIconTextGap(12);
            label.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
            add(label, BorderLayout.CENTER);
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        void setActive(boolean active) {
            this.active = active;
            Color c = active ? Color.WHITE : Theme.SIDEBAR_TEXT;
            icon.setColor(active ? new Color(0xA5B4FC) : Theme.SIDEBAR_TEXT);
            label.setForeground(c);
            label.setFont(Theme.font(active ? Font.BOLD : Font.PLAIN, 13));
            repaint();
        }

        protected void paintComponent(Graphics g) {
            if (active || hover) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(active ? Theme.SIDEBAR_ACTIVE : Theme.SIDEBAR_HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
