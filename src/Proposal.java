import java.util.List;

// Brouillon renvoye par le routeur a l'utilisateur pour validation.
public class Proposal {
    private final Email email;
    private final Agent agent;
    private final RoutingDecision decision;
    private final String draft;
    private final int attempt;
    private final int maxAttempts;
    private final Critique critique;
    private final List<Contribution> contributions;

    public Proposal(Email email, Agent agent, RoutingDecision decision, String draft,
                    int attempt, int maxAttempts, Critique critique) {
        this(email, agent, decision, draft, attempt, maxAttempts, critique, List.of());
    }

    public Proposal(Email email, Agent agent, RoutingDecision decision, String draft,
                    int attempt, int maxAttempts, Critique critique, List<Contribution> contributions) {
        this.email = email;
        this.agent = agent;
        this.decision = decision;
        this.draft = draft;
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
        this.critique = critique;
        this.contributions = List.copyOf(contributions);
    }

    public Email getEmail()                { return email; }
    public Agent getAgent()                { return agent; }
    public RoutingDecision getDecision()   { return decision; }
    public String getDraft()               { return draft; }
    public int getAttempt()                { return attempt; }
    public int getMaxAttempts()            { return maxAttempts; }
    public Critique getCritique()          { return critique; }
    // collaboration : notes des agents contributeurs fusionnees dans ce brouillon (vide sinon)
    public List<Contribution> getContributions() { return contributions; }
}
