import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class ProcessPage extends Page {
    private final EmailAssistant assistant;
    private final ConnectionStatus status;
    private final ActivityLog log;
    private final ComposePanel compose;
    private final WorkflowPanel workflow = new WorkflowPanel();
    private final RoutingPanel routing;
    private final ProposalPanel proposal = new ProposalPanel();
    private Agent selectedAgent;
    private String contributors = ""; // collaboration : noms des agents contributeurs
    private boolean busy;

    public ProcessPage(EmailAssistant assistant, SwingValidator validator, List<DemoEmail> demos,
                       ActivityLog log, ConnectionStatus status) {
        super("Process an email",
              "Email received → RoutingAgent → specialized agent → local LLM → CriticAgent → your validation");
        this.assistant = assistant;
        this.status = status;
        this.log = log;
        this.compose = new ComposePanel(demos);
        this.routing = new RoutingPanel(assistant);
        validator.attach(proposal);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        compose.setPreferredSize(new Dimension(340, 100));
        workflow.setPreferredSize(new Dimension(350, 100));
        routing.setPreferredSize(new Dimension(600, 352));
        proposal.setPreferredSize(new Dimension(480, 100));
        body.add(compose, constraints(0, 0, 1, 2, 0, 1, new Insets(0, 0, 0, 16)));
        body.add(routing, constraints(1, 0, 2, 1, 1, 0, new Insets(0, 0, 16, 0)));
        body.add(workflow, constraints(1, 1, 1, 1, 0, 1, new Insets(0, 0, 0, 16)));
        body.add(proposal, constraints(2, 1, 1, 1, 1, 1, new Insets(0, 0, 0, 0)));
        add(body, java.awt.BorderLayout.CENTER);

        assistant.addListener(new WorkflowListener() {
            public void stageChanged(WorkflowStage stage, StageStatus s, String detail) {
                SwingUtilities.invokeLater(() -> onStage(stage, s, detail));
            }

            public void agentSelected(Agent agent, RoutingDecision decision, Map<String, Double> scores,
                                      List<String> signals, boolean arbiter) {
                SwingUtilities.invokeLater(() -> {
                    selectedAgent = agent;
                    contributors = contributorNames(decision);
                    workflow.setAgentName(agent.getName());
                    workflow.setContributors(contributors);
                    routing.showDecision(agent, decision, scores, signals, arbiter);
                });
            }
        });

        compose.getProcessButton().addActionListener(e -> start());
        compose.getPreviewButton().addActionListener(e -> preview());
        proposal.setFollowUpHandler(compose::prepareFollowUp);
    }

    public void onShow() {
        if (!busy) routing.reset();
    }

    private void preview() {
        Email email = compose.buildEmail();
        if (email == null) return;
        new EmailPreviewDialog(SwingUtilities.getWindowAncestor(this), email, assistant.getContacts(), this::start)
                .setVisible(true);
    }

    private void start() {
        Email email = compose.buildEmail();
        if (email == null || busy) return;
        if (status.getState() == ConnectionStatus.State.OFFLINE) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "The local LLM server does not answer (" + status.getUrl() + ").\n"
                    + "Start LM Studio and its server, or process the email anyway?",
                    "Local LLM offline", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) return;
        }
        busy = true;
        selectedAgent = null;
        contributors = "";
        compose.setBusy(true);
        routing.reset();
        workflow.reset();
        proposal.showWaiting("The RoutingAgent is analysing the email...");

        new SwingWorker<Intention, Void>() {
            protected Intention doInBackground() {
                return assistant.process(email);
            }

            protected void done() {
                busy = false;
                compose.setBusy(false);
                String agentName = selectedAgent != null ? selectedAgent.getName() : "-";
                if (!contributors.isBlank()) agentName += " + " + contributors;
                try {
                    Intention intention = get();
                    proposal.showOutcome(intention, selectedAgent);
                    log.record(email, agentName, ActivityLog.outcomeOf(intention), intention.getAttempts());
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    workflow.stop();
                    proposal.showError(cause.getMessage());
                    log.record(email, agentName, ActivityLog.Outcome.ERROR, 0);
                    status.check();
                }
            }
        }.execute();
    }

    private void onStage(WorkflowStage stage, StageStatus s, String detail) {
        workflow.update(stage, s, detail);
        routing.setActive(stage);
        if (s != StageStatus.PROCESSING) return;
        String agent = selectedAgent != null ? selectedAgent.getName() : "The agent";
        switch (stage) {
            case ROUTING, COLLABORATION -> proposal.showWaiting(detail + "...");
            case GENERATION -> proposal.showWaiting(agent + " is writing a draft with the local LLM...");
            case CRITIC -> proposal.showWaiting("CriticAgent is reviewing the draft...");
            default -> { }
        }
    }

    private String contributorNames(RoutingDecision decision) {
        List<String> names = new ArrayList<>();
        for (RoutingDecision.Collaborator c : decision.getCollaborators()) {
            Agent a = assistant.getRegistry().get(c.role());
            names.add(a != null ? a.getName() : c.role());
        }
        return String.join(", ", names);
    }

    private GridBagConstraints constraints(int x, int y, int w, int h, double wx, double wy, Insets insets) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = w;
        c.gridheight = h;
        c.weightx = wx;
        c.weighty = wy;
        c.fill = GridBagConstraints.BOTH;
        c.insets = insets;
        return c;
    }
}
