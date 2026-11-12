import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

public class NewAgentDialog extends JDialog {
    private static final String PROMPT_TEMPLATE = """
            ROLE:
            You assist Dr. Martin Dupont in replying to ...

            TONE:
            - Polite, concise, professional.
            - Reply in the same language as the incoming email.

            YOU MUST:
            - Base your reply only on the provided information and the conversation history.
            - End with a clear next step.

            YOU MUST NEVER:
            - Invent dates, facts or commitments that are not in the provided information.
            - Sign as anyone other than Martin.

            OUTPUT:
            Write only the body of the reply email.
            """;

    private final AgentRegistry registry;
    private final JTextField name = Theme.field();
    private final JTextField role = Theme.field();
    private final JTextField description = Theme.field();
    private final JTextField keywords = Theme.field();
    private final JSpinner retries = new JSpinner(new SpinnerNumberModel(3, 0, 10, 1));
    private final JTextArea prompt = Theme.area(12);
    private final JTextArea context = Theme.area(6);
    private final JLabel error = Theme.label(" ", Theme.font(Font.PLAIN, 12), Theme.DANGER);
    private Agent created;

    public NewAgentDialog(Window owner, AgentRegistry registry) {
        super(owner, "New specialized agent", ModalityType.APPLICATION_MODAL);
        this.registry = registry;

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(Theme.heading("New specialized agent"));
        head.add(Box.createVerticalStrut(2));
        head.add(Theme.caption("Creates agents/<role>/ with agent.json, a system prompt and a context file. "
                               + "The agent is routable immediately."));
        root.add(head, BorderLayout.NORTH);

        prompt.setText(PROMPT_TEMPLATE);
        prompt.setFont(Theme.mono(12f));
        context.setFont(Theme.mono(12f));
        retries.setPreferredSize(new Dimension(80, 34));

        JPanel ids = new JPanel(new GridLayout(1, 3, 12, 0));
        ids.setOpaque(false);
        ids.add(Theme.fieldGroup("Name *  (e.g. ThesisAgent)", name));
        ids.add(Theme.fieldGroup("Role *  (e.g. Thesis)", role));
        ids.add(Theme.fieldGroup("Max retries", retries));

        JPanel form = new ScrollablePanel(null);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        addRow(form, ids);
        addRow(form, Theme.fieldGroup("Description (read by the LLM arbiter)", description));
        addRow(form, Theme.fieldGroup("Keywords (comma separated)", keywords));
        addRow(form, Theme.fieldGroup("System prompt", Theme.scroll(prompt, true)));
        addRow(form, Theme.fieldGroup("Context: one fact per line (\"date | fact\" or just the fact)", Theme.scroll(context, true)));
        root.add(Theme.scroll(form, false), BorderLayout.CENTER);

        PillButton cancel = new PillButton("Cancel", PillButton.Kind.SECONDARY);
        PillButton create = new PillButton("Create agent", PillButton.Kind.PRIMARY);
        cancel.addActionListener(e -> dispose());
        create.addActionListener(e -> create());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(create);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(error, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(760, 760);
        setLocationRelativeTo(owner);
    }

    public Agent getCreated() {
        return created;
    }

    private void create() {
        List<String> words = new ArrayList<>();
        for (String k : keywords.getText().split(",")) {
            if (!k.isBlank()) words.add(k.trim());
        }
        if (description.getText().isBlank()) {
            error.setText("A description is required: the arbiter uses it to choose between agents.");
            return;
        }
        if (words.isEmpty()) {
            error.setText("Give at least one keyword so that the router can recognise this agent's emails.");
            return;
        }
        try {
            created = registry.createAgent(name.getText(), role.getText(), description.getText().trim(), words,
                                           (Integer) retries.getValue(), prompt.getText(), context.getText());
            dispose();
        } catch (IOException e) {
            error.setText(e.getMessage());
        }
    }

    private void addRow(JPanel form, JComponent c) {
        c.setAlignmentX(0f);
        form.add(c);
        form.add(Box.createVerticalStrut(12));
    }
}
