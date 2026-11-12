import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

public class LlmPage extends Page {
    private final LmStudioClient llm;
    private final ConnectionStatus status;
    private final CriticAgent critic;
    private final Badge statusBadge = new Badge("", Theme.NEUTRAL_SOFT, Theme.TEXT_MUTED);
    private final JLabel statusDetail = Theme.label("", Theme.font(Font.PLAIN, 12), Theme.TEXT_MUTED);
    private final JLabel models = Theme.label("", Theme.font(Font.PLAIN, 12.5f), Theme.TEXT);
    private final JTextArea testAnswer = Theme.area(4);
    private final PillButton test = new PillButton("Send a test prompt", PillButton.Kind.SECONDARY);
    private final JTextArea config = Theme.area(8);
    private final JCheckBox criticEnabled = new JCheckBox("Review every draft with CriticAgent before showing it");
    private final JTextArea criticPrompt = Theme.area(14);
    private final JLabel criticFeedback = Theme.label(" ", Theme.font(Font.BOLD, 12), Theme.SUCCESS);

    public LlmPage(LmStudioClient llm, ConnectionStatus status, CriticAgent critic) {
        super("Local LLM", "Every generation goes through the local LM Studio server: no remote or paid API is used");
        this.llm = llm;
        this.status = status;
        this.critic = critic;

        PillButton check = new PillButton("Test connection", PillButton.Kind.PRIMARY);
        check.addActionListener(e -> status.check());
        addAction(check);

        JPanel left = new JPanel(new BorderLayout(0, 16));
        left.setOpaque(false);
        left.add(serverCard(), BorderLayout.CENTER);
        left.add(configCard(), BorderLayout.SOUTH);

        JPanel body = new JPanel(new GridLayout(1, 2, 16, 0));
        body.setOpaque(false);
        body.add(left);
        body.add(criticCard());
        add(body, BorderLayout.CENTER);

        status.addListener(this::updateStatus);
        updateStatus();
    }

    public void onShow() {
        loadConfig();
    }

    private JComponent serverCard() {
        Card card = new Card(new BorderLayout(0, 14), 18);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Theme.heading("LM Studio server"), BorderLayout.WEST);
        head.add(statusBadge, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        addInfo(rows, "Chat endpoint", llm.getUrl());
        addInfo(rows, "Configured model", llm.getModel());
        addInfo(rows, "Protocol", "OpenAI-compatible HTTP API");
        statusDetail.setAlignmentX(0f);
        rows.add(statusDetail);
        rows.add(Box.createVerticalStrut(12));
        JLabel overline = Theme.overline("Models available on the server");
        overline.setAlignmentX(0f);
        rows.add(overline);
        rows.add(Box.createVerticalStrut(4));
        models.setAlignmentX(0f);
        rows.add(models);
        rows.add(Box.createVerticalStrut(14));

        testAnswer.setEditable(false);
        testAnswer.setBackground(Theme.SURFACE_ALT);
        testAnswer.setText("The answer of the model will appear here.");
        test.addActionListener(e -> sendTestPrompt());
        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        testRow.setOpaque(false);
        testRow.add(test);
        testRow.setAlignmentX(0f);
        testRow.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 40));
        rows.add(testRow);
        rows.add(Box.createVerticalStrut(8));
        JComponent answer = Theme.scroll(testAnswer, true);
        answer.setAlignmentX(0f);
        rows.add(answer);
        card.add(rows, BorderLayout.CENTER);
        return card;
    }

    private JComponent configCard() {
        Card card = new Card(new BorderLayout(0, 10), 18);
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(Theme.heading("config.json"));
        head.add(Box.createVerticalStrut(2));
        head.add(Theme.caption("To use another model or port: edit this file and restart the application (no recompilation)."));
        card.add(head, BorderLayout.NORTH);
        config.setEditable(false);
        config.setFont(Theme.mono(12f));
        config.setBackground(Theme.SURFACE_ALT);
        card.add(Theme.scroll(config, true), BorderLayout.CENTER);
        loadConfig();
        return card;
    }

    private JComponent criticCard() {
        Card card = new Card(new BorderLayout(0, 12), 18);
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel title = Theme.heading("CriticAgent");
        title.setAlignmentX(0f);
        head.add(title);
        head.add(Box.createVerticalStrut(2));
        JLabel caption = Theme.wrapped("Support agent that evaluates each draft (relevance, rules, invented facts, "
                + "confidentiality, form) with the same local LLM. Its score and remarks are shown next to the draft; "
                + "you can reuse them as remarks for a new draft.", 360, Theme.font(Font.PLAIN, 12), Theme.TEXT_MUTED);
        caption.setAlignmentX(0f);
        head.add(caption);
        head.add(Box.createVerticalStrut(10));
        criticEnabled.setSelected(critic.isEnabled());
        criticEnabled.setOpaque(false);
        criticEnabled.setFont(Theme.font(Font.BOLD, 13));
        criticEnabled.setForeground(Theme.TEXT);
        criticEnabled.setAlignmentX(0f);
        criticEnabled.addActionListener(e -> critic.setEnabled(criticEnabled.isSelected()));
        head.add(criticEnabled);
        card.add(head, BorderLayout.NORTH);

        criticPrompt.setFont(Theme.mono(12f));
        criticPrompt.setText(critic.getSystemPrompt());
        criticPrompt.setCaretPosition(0);
        card.add(Theme.scroll(criticPrompt, true), BorderLayout.CENTER);

        PillButton save = new PillButton("Save critic prompt", PillButton.Kind.PRIMARY);
        save.addActionListener(e -> {
            try {
                critic.saveSystemPrompt(criticPrompt.getText());
                criticFeedback.setForeground(Theme.SUCCESS);
                criticFeedback.setText("Saved in " + critic.getSystemPromptFile() + " ✓");
            } catch (IOException ex) {
                criticFeedback.setForeground(Theme.DANGER);
                criticFeedback.setText(ex.getMessage());
            }
        });
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setOpaque(false);
        south.add(save, BorderLayout.WEST);
        south.add(criticFeedback, BorderLayout.CENTER);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private void updateStatus() {
        switch (status.getState()) {
            case ONLINE -> statusBadge.setStyle(status.label(), status.isModelAvailable() ? Theme.SUCCESS_SOFT : Theme.WARNING_SOFT,
                                                status.isModelAvailable() ? Theme.SUCCESS : Theme.WARNING);
            case OFFLINE -> statusBadge.setStyle("Offline", Theme.DANGER_SOFT, Theme.DANGER);
            case CHECKING -> statusBadge.setStyle("Checking...", Theme.INFO_SOFT, Theme.INFO);
            default -> statusBadge.setStyle("Not checked", Theme.NEUTRAL_SOFT, Theme.TEXT_MUTED);
        }
        String detail = switch (status.getState()) {
            case OFFLINE -> status.getError() + "\nStart LM Studio, load the model and start the server "
                            + "(Developer tab, port 1234).";
            case ONLINE -> status.isModelAvailable() ? "The server answers and the configured model is available."
                           : "The server answers but \"" + status.getModel() + "\" is not in its model list: "
                             + "download it in LM Studio or change \"model\" in config.json.";
            default -> "";
        };
        statusDetail.setText(detail.isEmpty() ? " " : Theme.html(detail, 360));
        statusDetail.setForeground(status.getState() == ConnectionStatus.State.OFFLINE ? Theme.DANGER : Theme.TEXT_MUTED);
        StringBuilder sb = new StringBuilder();
        for (String m : status.getModels()) {
            sb.append(m.equals(status.getModel()) ? "✓ " : "• ").append(m).append("\n");
        }
        models.setText(sb.length() == 0 ? "-" : Theme.html(sb.toString().trim(), 360));
    }

    private void sendTestPrompt() {
        test.setEnabled(false);
        testAnswer.setText("Waiting for " + llm.getModel() + "...");
        long start = System.currentTimeMillis();
        new SwingWorker<String, Void>() {
            protected String doInBackground() {
                return llm.ask("You are a helpful assistant. Answer in one short sentence.",
                               "Say hello to the user of the MAS Mail Assistant.");
            }

            protected void done() {
                test.setEnabled(true);
                try {
                    testAnswer.setText(get().trim() + "\n\n(" + (System.currentTimeMillis() - start) + " ms)");
                } catch (Exception e) {
                    testAnswer.setText("Failed: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                }
                status.check();
            }
        }.execute();
    }

    private void loadConfig() {
        try {
            config.setText(Files.readString(Path.of("config.json")));
        } catch (IOException e) {
            config.setText("config.json not found: " + e.getMessage());
        }
    }

    private void addInfo(JPanel rows, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(0f);
        JLabel l = Theme.label(label, Theme.font(Font.BOLD, 12), Theme.TEXT_MUTED);
        l.setPreferredSize(new java.awt.Dimension(130, 20));
        row.add(l, BorderLayout.WEST);
        row.add(Theme.label(value, Theme.font(Font.PLAIN, 12.5f), Theme.TEXT), BorderLayout.CENTER);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 26));
        rows.add(row);
        rows.add(Box.createVerticalStrut(6));
    }
}
