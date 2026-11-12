import java.util.concurrent.CompletableFuture;

import javax.swing.SwingUtilities;

public class SwingValidator implements Validator {
    private volatile ProposalPanel panel;

    public void attach(ProposalPanel panel) {
        this.panel = panel;
    }

    public Feedback review(Proposal proposal) {
        CompletableFuture<Feedback> answer = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> panel.showProposal(proposal, answer::complete));
        return answer.join();
    }

    public String writeReply(Email email) {
        CompletableFuture<String> answer = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> panel.askManualReply(email, answer::complete));
        return answer.join();
    }
}
