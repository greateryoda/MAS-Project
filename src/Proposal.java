public class Proposal {
    private final Email email;
    private final Agent agent;
    private final RoutingDecision decision;
    private final String draft;

    public Proposal(Email email, Agent agent, RoutingDecision decision, String draft) {
        this.email = email;
        this.agent = agent;
        this.decision = decision;
        this.draft = draft;
    }

    public Email getEmail(){
        return email;
    }

    public Agent getAgent(){
        return agent;
    }

    public RoutingDecision getDecision(){
        return decision;
    }

    public String getDraft(){
        return draft;
    }
}
