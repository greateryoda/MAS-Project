import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

public class AgentsPage extends Page {
    private static final String OVERVIEW = "Overview";
    private static final String PROMPT = "System prompt";
    private static final String CONTEXT = "Context";
    private static final String MEMORY = "Memory";

    private final EmailAssistant assistant;
    private final AgentRegistry registry;
    private final DefaultListModel<Agent> listModel = new DefaultListModel<>();
    private final JList<Agent> list = new JList<>(listModel);

    private final Avatar avatar = new Avatar("", Theme.ACCENT, 46);
    private final JLabel name = Theme.label("", Theme.font(Font.BOLD, 18), Theme.TEXT);
    private final Badge role = new Badge("", Theme.ACCENT_SOFT, Theme.ACCENT);
    private final JLabel files = Theme.label("", Theme.font(Font.PLAIN, 11.5f), Theme.TEXT_MUTED);
    private final JLabel feedback = Theme.label(" ", Theme.font(Font.BOLD, 12), Theme.SUCCESS);
    private final CardLayout tabsLayout = new CardLayout();
    private final JPanel tabs = new JPanel(tabsLayout);
    private final Map<String, PillButton> tabButtons = new LinkedHashMap<>();

    private final JTextArea description = Theme.area(3);
    private final JTextField keywords = Theme.field();
    private final JSpinner retries = new JSpinner(new SpinnerNumberModel(3, 0, 10, 1));
    private final JLabel contacts = Theme.label("", Theme.font(Font.PLAIN, 12), Theme.TEXT_MUTED);
    private final JTextArea prompt = Theme.area(18);
    private final JComboBox<String> contextFiles = new JComboBox<>();
    private final JTextArea context = Theme.area(18);
    private final JTextArea memory = Theme.area(18);
    private final JLabel promptFile = Theme.caption("");

    public AgentsPage(EmailAssistant assistant) {
        super("Agents", "View and edit the specialized agents: role, system prompt, context and memory");
        this.assistant = assistant;
        this.registry = assistant.getRegistry();

        PillButton create = new PillButton("+ New agent", PillButton.Kind.PRIMARY);
        create.addActionListener(e -> createAgent());
        addAction(create);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new AgentRenderer());
        list.setBackground(Theme.SURFACE);
        list.setFixedCellHeight(58);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showAgent(list.getSelectedValue());
        });
        Card left = new Card(new BorderLayout(0, 10), 12);
        left.add(Theme.overline("  Specialized agents"), BorderLayout.NORTH);
        JScrollPane listScroll = Theme.scroll(list, false);
        listScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        left.add(listScroll, BorderLayout.CENTER);
        left.add(Theme.wrapped("Each agent lives in agents/<name>/ (agent.json, system prompt, context). "
                + "Changes are saved to these files and applied immediately.", 210,
                Theme.font(Font.PLAIN, 11), Theme.TEXT_SOFT), BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(270, 100));

        Card detail = new Card(new BorderLayout(0, 14), 20);
        JPanel head = new JPanel(new BorderLayout(14, 0));
        head.setOpaque(false);
        head.add(avatar, BorderLayout.WEST);
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameRow.setOpaque(false);
        nameRow.add(name);
        nameRow.add(Box.createHorizontalStrut(10));
        nameRow.add(role);
        nameRow.setAlignmentX(0f);
        files.setAlignmentX(0f);
        names.add(nameRow);
        names.add(Box.createVerticalStrut(4));
        names.add(files);
        head.add(names, BorderLayout.CENTER);
        head.add(feedback, BorderLayout.EAST);

        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tabBar.setOpaque(false);
        for (String t : new String[] {OVERVIEW, PROMPT, CONTEXT, MEMORY}) {
            PillButton b = new PillButton(t, PillButton.Kind.GHOST);
            b.addActionListener(e -> selectTab(t));
            tabButtons.put(t, b);
            tabBar.add(b);
        }
        JPanel top = new JPanel(new BorderLayout(0, 14));
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(tabBar, BorderLayout.SOUTH);
        detail.add(top, BorderLayout.NORTH);

        tabs.setOpaque(false);
        tabs.add(overviewTab(), OVERVIEW);
        tabs.add(promptTab(), PROMPT);
        tabs.add(contextTab(), CONTEXT);
        tabs.add(memoryTab(), MEMORY);
        detail.add(tabs, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout(16, 0));
        body.setOpaque(false);
        body.add(left, BorderLayout.WEST);
        body.add(detail, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        reloadList(null);
        selectTab(OVERVIEW);
    }

    public void onShow() {
        Agent selected = list.getSelectedValue();
        reloadList(selected == null ? null : selected.getRole());
    }

    public void select(String agentRole) {
        reloadList(agentRole);
        selectTab(OVERVIEW);
    }

    private JComponent overviewTab() {
        JPanel p = column();
        prompt.setFont(Theme.mono(12.5f));
        context.setFont(Theme.mono(12.5f));
        memory.setFont(Theme.mono(12f));
        retries.setFont(Theme.font(Font.PLAIN, 13));
        ((JSpinner.DefaultEditor) retries.getEditor()).getTextField().setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        retries.setMaximumSize(new Dimension(90, 36));
        retries.setPreferredSize(new Dimension(90, 36));

        addRow(p, Theme.fieldGroup("Description (used by the LLM arbiter to choose between agents)", Theme.scroll(description, true)));
        addRow(p, Theme.fieldGroup("Keywords (comma separated, used by KeywordStep)", keywords));
        addRow(p, Theme.fieldGroup("Maximum number of new drafts after a rejection (desire: maxRetries)", retries));
        addRow(p, Theme.fieldGroup("Known contacts routed to this agent (agents/contacts.json)", contacts));
        PillButton save = new PillButton("Save profile", PillButton.Kind.PRIMARY);
        save.addActionListener(e -> saveProfile());
        addRow(p, actions(save));
        JPanel holder = new ScrollablePanel(new BorderLayout());
        holder.add(p, BorderLayout.NORTH);
        return Theme.scroll(holder, false);
    }

    private JComponent promptTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.add(Theme.wrapped("The system prompt defines the role, the tone, what the agent must do and must never do. "
                + "It is applied to the next draft as soon as it is saved.", 560,
                Theme.font(Font.PLAIN, 12), Theme.TEXT_MUTED), BorderLayout.NORTH);
        p.add(Theme.scroll(prompt, true), BorderLayout.CENTER);
        PillButton save = new PillButton("Save system prompt", PillButton.Kind.PRIMARY);
        PillButton revert = new PillButton("Revert", PillButton.Kind.SECONDARY);
        save.addActionListener(e -> run("System prompt saved", () -> registry.saveSystemPrompt(current().getRole(), prompt.getText())));
        revert.addActionListener(e -> prompt.setText(current().getSystemPrompt()));
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(actions(save, revert), BorderLayout.WEST);
        south.add(promptFile, BorderLayout.EAST);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private JComponent contextTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        contextFiles.setFont(Theme.font(Font.PLAIN, 13));
        contextFiles.setBackground(Theme.SURFACE);
        contextFiles.addActionListener(e -> loadContextFile());
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(Theme.wrapped("Context = facts only this agent knows (one per line, format \"date | fact\"; a line "
                + "without a date gets today's date). They are sent to the LLM with every draft of this agent.",
                640, Theme.font(Font.PLAIN, 12), Theme.TEXT_MUTED), BorderLayout.NORTH);
        top.add(contextFiles, BorderLayout.SOUTH);
        p.add(top, BorderLayout.NORTH);
        p.add(Theme.scroll(context, true), BorderLayout.CENTER);
        PillButton save = new PillButton("Save context", PillButton.Kind.PRIMARY);
        PillButton importFile = new PillButton("Import a text file...", PillButton.Kind.SECONDARY);
        save.addActionListener(e -> run("Context saved", () -> {
            registry.saveContextFile(current().getRole(), (String) contextFiles.getSelectedItem(), context.getText());
            loadContextFile();
        }));
        importFile.addActionListener(e -> importContext());
        p.add(actions(save, importFile), BorderLayout.SOUTH);
        return p;
    }

    private JComponent memoryTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.add(Theme.wrapped("Conversation memory: emails received and replies validated by the user, grouped by thread. "
                + "Rejected drafts are never memorised. The whole thread is sent back to the agent when a new email "
                + "of the same thread arrives.", 560, Theme.font(Font.PLAIN, 12), Theme.TEXT_MUTED), BorderLayout.NORTH);
        memory.setEditable(false);
        memory.setBackground(Theme.SURFACE_ALT);
        p.add(Theme.scroll(memory, true), BorderLayout.CENTER);
        return p;
    }

    private void reloadList(String selectRole) {
        listModel.clear();
        List<Agent> agents = registry.list();
        agents.forEach(listModel::addElement);
        int index = 0;
        for (int i = 0; i < agents.size(); i++) {
            if (selectRole != null && agents.get(i).getRole().equalsIgnoreCase(selectRole)) index = i;
        }
        if (!agents.isEmpty()) list.setSelectedIndex(index);
    }

    private Agent current() {
        return list.getSelectedValue();
    }

    private void showAgent(Agent agent) {
        if (agent == null) return;
        feedback.setText(" ");
        avatar.set(Theme.initials(agent.getName()), Theme.agentColor(agent.getRole()));
        name.setText(agent.getName());
        role.setStyle("role " + agent.getRole(), Theme.soft(Theme.agentColor(agent.getRole())), Theme.agentColor(agent.getRole()));
        description.setText(agent.getDescription());
        keywords.setText(String.join(", ", agent.getKeywords()));
        prompt.setText(agent.getSystemPrompt());
        prompt.setCaretPosition(0);
        List<String> known = assistant.getContacts().addressesWithRole(agent.getRole());
        contacts.setText(known.isEmpty() ? "None: this agent is reached through keywords, thread memory or the arbiter."
                                         : Theme.html(String.join("\n", known), 520));
        try {
            AgentConfig config = registry.configOf(agent.getRole());
            retries.setValue(config.getMaxRetries());
            files.setText(config.getSystemPromptFile() + "  ·  " + config.getContextFiles().size()
                          + " context file(s)  ·  memory: " + config.getMemoryFile());
            promptFile.setText(config.getSystemPromptFile());
            contextFiles.removeAllItems();
            config.getContextFiles().forEach(contextFiles::addItem);
            loadContextFile();
            memory.setText(formatMemory(registry.readMemory(agent.getRole())));
            memory.setCaretPosition(0);
        } catch (IOException e) {
            error(e.getMessage());
        }
    }

    private void loadContextFile() {
        Object file = contextFiles.getSelectedItem();
        if (file == null) {
            context.setText("");
            return;
        }
        try {
            context.setText(registry.readFile(file.toString()));
            context.setCaretPosition(0);
        } catch (IOException e) {
            error(e.getMessage());
        }
    }

    private void saveProfile() {
        List<String> words = new ArrayList<>();
        for (String k : keywords.getText().split(",")) {
            if (!k.isBlank()) words.add(k.trim());
        }
        run("Profile saved", () -> registry.updateProfile(current().getRole(), description.getText().trim(), words,
                                                          (Integer) retries.getValue()));
    }

    private void importContext() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setDialogTitle("Associate a text file with " + current().getName());
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        String role = current().getRole();
        run("File imported as context", () -> {
            String path = registry.importContextFile(role, chooser.getSelectedFile());
            showAgent(current());
            contextFiles.setSelectedItem(path);
        });
    }

    private void createAgent() {
        NewAgentDialog dialog = new NewAgentDialog(SwingUtilities.getWindowAncestor(this), registry);
        dialog.setVisible(true);
        Agent created = dialog.getCreated();
        if (created != null) {
            reloadList(created.getRole());
            feedback.setForeground(Theme.SUCCESS);
            feedback.setText(created.getName() + " created and available for routing");
        }
    }

    private void selectTab(String tab) {
        tabsLayout.show(tabs, tab);
        tabButtons.forEach((t, b) -> b.setFont(Theme.font(t.equals(tab) ? Font.BOLD : Font.PLAIN, 13)));
        tabButtons.forEach((t, b) -> b.setForeground(t.equals(tab) ? Theme.ACCENT : Theme.TEXT_MUTED));
        if (tab.equals(MEMORY) && current() != null) {
            try {
                memory.setText(formatMemory(registry.readMemory(current().getRole())));
                memory.setCaretPosition(0);
            } catch (IOException e) {
                error(e.getMessage());
            }
        }
    }

    private interface Action {
        void run() throws IOException;
    }

    private void run(String success, Action action) {
        if (current() == null) return;
        try {
            action.run();
            feedback.setForeground(Theme.SUCCESS);
            feedback.setText(success + " ✓");
            list.repaint();
        } catch (IOException e) {
            error(e.getMessage());
        }
    }

    private void error(String message) {
        feedback.setForeground(Theme.DANGER);
        feedback.setText(Theme.html(message, 260));
    }

    private static String formatMemory(String raw) {
        Map<String, List<String>> threads = new TreeMap<>();
        for (String line : raw.split("\\R")) {
            String[] p = line.split("\\|", 5);
            if (p.length < 5) continue;
            threads.computeIfAbsent(p[0], k -> new ArrayList<>()).add("[" + p[1] + ", " + p[3] + "] " + p[4]);
        }
        if (threads.isEmpty()) return "No validated reply yet.";
        StringBuilder sb = new StringBuilder();
        threads.forEach((thread, lines) -> {
            sb.append("Thread #").append(thread).append("\n");
            lines.forEach(l -> sb.append("  ").append(l).append("\n"));
            sb.append("\n");
        });
        return sb.toString().trim();
    }

    private JPanel column() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private void addRow(JPanel p, JComponent c) {
        c.setAlignmentX(0f);
        p.add(c);
        p.add(Box.createVerticalStrut(14));
    }

    private JPanel actions(JComponent... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        for (JComponent b : buttons) {
            row.add(b);
            row.add(Box.createHorizontalStrut(8));
        }
        return row;
    }

    private static class AgentRenderer extends JPanel implements ListCellRenderer<Agent> {
        private final Avatar avatar = new Avatar("", Theme.ACCENT, 34);
        private final JLabel title = Theme.label("", Theme.font(Font.BOLD, 13), Theme.TEXT);
        private final JLabel sub = Theme.caption("");

        AgentRenderer() {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(title);
            text.add(sub);
            add(avatar, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        public Component getListCellRendererComponent(JList<? extends Agent> list, Agent agent, int index,
                                                      boolean selected, boolean focus) {
            avatar.set(Theme.initials(agent.getName()), Theme.agentColor(agent.getRole()));
            title.setText(agent.getName());
            sub.setText("role " + agent.getRole() + "  ·  " + agent.getKeywords().size() + " keywords");
            setBackground(selected ? Theme.ACCENT_SOFT : Theme.SURFACE);
            return this;
        }
    }
}
