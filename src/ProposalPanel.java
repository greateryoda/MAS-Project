import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class ProposalPanel extends Card {
    private final Badge attemptBadge = new Badge("", Theme.NEUTRAL_SOFT, Theme.TEXT_MUTED);
    private final Card banner = new Card(new BorderLayout(), 10);
    private final JLabel bannerText = Theme.label("", Theme.font(Font.PLAIN, 12.5f), Theme.TEXT);
    private final JTextArea draft = Theme.area(10);
    private final JTextArea remarks = Theme.area(2);
    private final Card criticBox = new Card(new BorderLayout(10, 6), 10);
    private final JLabel criticTitle = Theme.label("", Theme.font(Font.BOLD, 12.5f), Theme.TEXT);
    private final JLabel criticIssues = Theme.label("", Theme.font(Font.PLAIN, 12), Theme.TEXT_MUTED);
    private final PillButton useCritic = new PillButton("Use as remarks", PillButton.Kind.GHOST);
    private final PillButton viewContrib = new PillButton("Contributions", PillButton.Kind.GHOST);
    private final PillButton accept = new PillButton("Accept reply", PillButton.Kind.SUCCESS);
    private final PillButton regenerate = new PillButton("Regenerate with remarks", PillButton.Kind.SECONDARY);
    private final PillButton discard = new PillButton("Refuse", PillButton.Kind.DANGER);
    private final PillButton saveManual = new PillButton("Save my reply", PillButton.Kind.SUCCESS);
    private final PillButton sendNothing = new PillButton("Send nothing", PillButton.Kind.SECONDARY);
    private final PillButton copy = new PillButton("Copy reply", PillButton.Kind.SECONDARY);
    private final PillButton followUp = new PillButton("Next email in this thread", PillButton.Kind.GHOST);
    private final JPanel reviewActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JPanel manualActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JPanel doneActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JPanel remarksGroup;

    private Proposal current;
    private Consumer<Feedback> pendingReview;
    private Consumer<String> pendingManual;
    private Consumer<Email> followUpHandler = e -> {};
    private Email lastEmail;

    public ProposalPanel() {
        super(new BorderLayout(0, 12), 16);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Theme.heading("Proposed reply"));
        titles.add(Box.createVerticalStrut(2));
        titles.add(Theme.caption("Nothing is sent: you accept, edit, refuse or ask for a new draft"));
        header.add(titles, BorderLayout.WEST);
        // collaboration : le bandeau nomme les agents contributeurs, ce bouton (a cote du badge) montre
        // leurs notes ; pas de cadre supplementaire, la zone du brouillon garde toute sa hauteur
        JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        badges.setOpaque(false);
        badges.add(viewContrib);
        badges.add(attemptBadge);
        header.add(badges, BorderLayout.EAST);
        viewContrib.addActionListener(e -> showContributionDetails());
        viewContrib.setVisible(false);

        banner.add(bannerText, BorderLayout.CENTER);
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        top.add(banner, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        draft.setRows(9);
        draft.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { updateAcceptLabel(); }
            public void removeUpdate(DocumentEvent e)  { updateAcceptLabel(); }
            public void changedUpdate(DocumentEvent e) { updateAcceptLabel(); }
        });
        add(Theme.scroll(draft, true), BorderLayout.CENTER);

        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));

        criticBox.setColors(Theme.SURFACE_ALT, Theme.BORDER);
        JPanel criticText = new JPanel();
        criticText.setOpaque(false);
        criticText.setLayout(new BoxLayout(criticText, BoxLayout.Y_AXIS));
        criticText.add(criticTitle);
        criticText.add(Box.createVerticalStrut(3));
        criticText.add(criticIssues);
        criticBox.add(criticText, BorderLayout.CENTER);
        criticBox.add(useCritic, BorderLayout.EAST);
        criticBox.setAlignmentX(0f);
        useCritic.addActionListener(e -> {
            if (current != null && current.getCritique() != null) remarks.setText(current.getCritique().remarksAsText());
        });
        south.add(Box.createVerticalStrut(10));
        south.add(criticBox);
        south.add(Box.createVerticalStrut(10));

        remarks.setRows(2);
        remarksGroup = (JPanel) Theme.fieldGroup("Remarks for a new draft (optional)", Theme.scroll(remarks, true));
        remarksGroup.setAlignmentX(0f);
        south.add(remarksGroup);
        south.add(Box.createVerticalStrut(12));

        for (JPanel p : new JPanel[] {reviewActions, manualActions, doneActions}) {
            p.setOpaque(false);
            p.setAlignmentX(0f);
            south.add(p);
        }
        reviewActions.add(accept);
        reviewActions.add(regenerate);
        reviewActions.add(discard);
        manualActions.add(saveManual);
        manualActions.add(sendNothing);
        doneActions.add(copy);
        doneActions.add(followUp);
        add(south, BorderLayout.SOUTH);

        accept.addActionListener(e -> answer(draftChanged() ? Feedback.modify(draft.getText().trim()) : Feedback.accept()));
        regenerate.addActionListener(e -> answer(Feedback.reject(remarks.getText().trim())));
        discard.addActionListener(e -> answer(Feedback.discard()));
        saveManual.addActionListener(e -> answerManual(draft.getText().trim().isEmpty() ? null : draft.getText().trim()));
        sendNothing.addActionListener(e -> answerManual(null));
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                                          .setContents(new StringSelection(draft.getText()), null));
        followUp.addActionListener(e -> { if (lastEmail != null) followUpHandler.accept(lastEmail); });

        showIdle();
    }

    public void setFollowUpHandler(Consumer<Email> handler) {
        this.followUpHandler = handler;
    }

    public void showIdle() {
        current = null;
        attemptBadge.setVisible(false);
        setBanner("Write or load an email on the left, then click \"Process email\".", Theme.NEUTRAL_SOFT, Theme.TEXT_MUTED);
        draft.setText("");
        draft.setEditable(false);
        criticBox.setVisible(false);
        remarksGroup.setVisible(false);
        showActions(null);
    }

    public void showWaiting(String message) {
        pendingReview = null;
        attemptBadge.setVisible(current != null);
        setBanner(message, Theme.INFO_SOFT, Theme.INFO);
        draft.setEditable(false);
        remarksGroup.setVisible(false);
        showActions(null);
    }

    public void showProposal(Proposal proposal, Consumer<Feedback> onAnswer) {
        current = proposal;
        pendingReview = onAnswer;
        lastEmail = proposal.getEmail();
        attemptBadge.setVisible(true);
        attemptBadge.setStyle("Draft " + proposal.getAttempt() + "/" + proposal.getMaxAttempts(),
                              Theme.ACCENT_SOFT, Theme.ACCENT);
        // collaboration : le bandeau nomme les contributeurs et la partie traitee par chacun
        List<String> helpers = new ArrayList<>();
        for (Contribution c : proposal.getContributions()) {
            helpers.add(c.agentName() + (c.available() ? " (" + c.part() + ")" : " (unavailable)"));
        }
        String text = helpers.isEmpty()
                ? "Draft written by " + proposal.getAgent().getName()
                  + ". Edit the text if needed, then accept it, refuse it or ask for a new draft."
                : "Draft written by " + proposal.getAgent().getName() + " with the contribution of "
                  + String.join(", ", helpers) + ".";
        setBanner(text, Theme.ACCENT_SOFT, Theme.ACCENT_DARK, !helpers.isEmpty());
        draft.setText(proposal.getDraft());
        draft.setCaretPosition(0);
        draft.setEditable(true);
        remarks.setText("");
        remarksGroup.setVisible(true);
        boolean last = proposal.getAttempt() >= proposal.getMaxAttempts();
        regenerate.setText(last ? "Reject (last draft)" : "Regenerate with remarks");
        showCritique(proposal.getCritique());
        showActions(reviewActions);
        updateAcceptLabel();
    }

    public void askManualReply(Email email, Consumer<String> onAnswer) {
        pendingManual = onAnswer;
        lastEmail = email;
        setBanner("All drafts were rejected. Write the reply yourself (it will be memorised as an edited reply), "
                  + "or choose to send nothing.", Theme.WARNING_SOFT, Theme.WARNING);
        draft.setText("");
        draft.setEditable(true);
        criticBox.setVisible(false);
        remarksGroup.setVisible(false);
        showActions(manualActions);
        draft.requestFocusInWindow();
    }

    public void showOutcome(Intention intention, Agent agent) {
        pendingReview = null;
        pendingManual = null;
        draft.setEditable(false);
        remarksGroup.setVisible(false);
        switch (intention.getStatus()) {
            case ACHIEVED -> {
                draft.setText(intention.getFinalReply());
                setBanner("Reply validated and saved in the memory of " + agent.getName()
                          + ". It was not sent: copy it into your mail client.", Theme.SUCCESS_SOFT, Theme.SUCCESS);
                showActions(doneActions);
                copy.setVisible(true);
            }
            case DISCARDED -> {
                setBanner("Reply refused. Nothing was saved and nothing was sent.", Theme.NEUTRAL_SOFT, Theme.TEXT_MUTED);
                showActions(doneActions);
                copy.setVisible(false);
            }
            default -> {
                draft.setText("");
                setBanner("No reply: all drafts were rejected and no manual reply was written. Nothing was saved.",
                          Theme.NEUTRAL_SOFT, Theme.TEXT_MUTED);
                showActions(doneActions);
                copy.setVisible(false);
            }
        }
    }

    public void showError(String message) {
        pendingReview = null;
        pendingManual = null;
        draft.setEditable(false);
        criticBox.setVisible(false);
        remarksGroup.setVisible(false);
        setBanner("Processing failed: " + message, Theme.DANGER_SOFT, Theme.DANGER);
        showActions(null);
    }

    private void answer(Feedback feedback) {
        if (pendingReview == null) return;
        Consumer<Feedback> callback = pendingReview;
        pendingReview = null;
        showWaiting(switch (feedback.getType()) {
            case REJECT -> "Draft rejected. The agent writes a new draft with your remarks...";
            default -> "Saving your decision...";
        });
        callback.accept(feedback);
    }

    private void answerManual(String reply) {
        if (pendingManual == null) return;
        Consumer<String> callback = pendingManual;
        pendingManual = null;
        callback.accept(reply);
    }

    private boolean draftChanged() {
        return current != null && !draft.getText().trim().equals(current.getDraft().trim());
    }

    private void updateAcceptLabel() {
        accept.setText(draftChanged() ? "Validate edited reply" : "Accept reply");
    }


    // texte complet des contributions, a la demande (le panneau reste compact)
    private void showContributionDetails() {
        if (current == null || current.getContributions().isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Contribution c : current.getContributions()) {
            sb.append(c.agentName()).append(" — ").append(c.part()).append("\n")
              .append(c.available() ? c.text().trim() : "Unavailable: " + c.error()).append("\n\n");
        }
        JTextArea text = Theme.area(12);
        text.setText(sb.toString().trim());
        text.setEditable(false);
        text.setCaretPosition(0);
        JScrollPane scroll = Theme.scroll(text, true);
        scroll.setPreferredSize(new Dimension(520, 260));
        JOptionPane.showMessageDialog(this, scroll, "Contributions merged by " + current.getAgent().getName(),
                                      JOptionPane.PLAIN_MESSAGE);
    }

    private void showCritique(Critique critique) {
        if (critique == null) {
            criticBox.setVisible(false);
            return;
        }
        criticBox.setVisible(true);
        if (!critique.isAvailable()) {
            criticBox.setColors(Theme.SURFACE_ALT, Theme.BORDER);
            criticTitle.setText("CriticAgent: review unavailable");
            criticIssues.setText(Theme.html(critique.getError(), 340));
            useCritic.setVisible(false);
            return;
        }
        Color color = critique.isApproved() ? Theme.SUCCESS : Theme.WARNING;
        criticBox.setColors(Theme.soft(color), Theme.mix(color, Color.WHITE, 0.6));
        criticTitle.setText("CriticAgent: " + critique.getScore() + "/10, "
                            + (critique.isApproved() ? "approved" : "revision suggested"));
        criticTitle.setForeground(Theme.mix(color, Color.BLACK, 0.25));
        StringBuilder sb = new StringBuilder();
        for (String issue : critique.getIssues()) sb.append("• ").append(issue).append("\n");
        criticIssues.setText(critique.getIssues().isEmpty() ? "No remark." : Theme.html(sb.toString().trim(), 340));
        useCritic.setVisible(!critique.getIssues().isEmpty());
    }

    // tout changement de bandeau masque le bouton des contributions (seul showProposal le remontre)
    private void setBanner(String text, Color fill, Color foreground) {
        setBanner(text, fill, foreground, false);
    }

    private void setBanner(String text, Color fill, Color foreground, boolean withContributions) {
        banner.setColors(fill, Theme.mix(foreground, Color.WHITE, 0.7));
        bannerText.setForeground(Theme.mix(foreground, Color.BLACK, 0.15));
        bannerText.setText(Theme.html(text, 375));
        viewContrib.setVisible(withContributions);
    }

    private void showActions(JPanel visible) {
        reviewActions.setVisible(visible == reviewActions);
        manualActions.setVisible(visible == manualActions);
        doneActions.setVisible(visible == doneActions);
        revalidate();
        repaint();
    }

    public Dimension getMinimumSize() {
        return new Dimension(420, 300);
    }
}
