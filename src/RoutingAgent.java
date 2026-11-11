import java.util.List;
import java.util.Map;

public class RoutingAgent{
    private static final String FALLBACK_ROLE = "GENERIC";
    private static final double THRESHOLD = 0.5; // arbitraire

    private Map<String, Agent> agents;
    private TemporaryContactBook contacts;
    private List<RoutingStep> steps;

    public RoutingAgent(Map<String, Agent> agents, TemporaryContactBook contacts, List<RoutingStep> steps){
        this.agents = agents;
        this.contacts = contacts;
        this.steps = steps;
    }

    public Proposal RouteMail(Email email){
        RoutingDecision decision = decide(email);
        Agent agent = agents.get(decision.getRole());
        email.setSenderName(contacts.getName(email.getSenderAddress()));
        String draft = agent.handle(email);
        return new Proposal(email, agent, decision, draft);
    }

    // on essaie les maillons dans l'ordre ; le premier assez sur et dont l'agent existe gagne
    private RoutingDecision decide(Email email){
        for (RoutingStep step : steps) {
            RoutingDecision d = step.decide(email);
            if (d != null && d.getConfidence() >= THRESHOLD && agents.containsKey(d.getRole())) {
                return d;
            }
        }
        if (!agents.containsKey(FALLBACK_ROLE)) {
            throw new IllegalStateException("No agent found and no generic agent for: " + email.getSenderAddress());
        }
        return new RoutingDecision(FALLBACK_ROLE, 0.0, "no step was confident enough");
    }
}
