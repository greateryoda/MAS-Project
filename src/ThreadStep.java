import java.util.Map;

// Si un agent a deja traite ce fil de discussion, on lui renvoie l'email.
public class ThreadStep implements RoutingStep {
    private final Map<String, Agent> agents;

    public ThreadStep(Map<String, Agent> agents) {
        this.agents = agents;
    }

    public RoutingDecision decide(Email email) {
        for (Map.Entry<String, Agent> entry : agents.entrySet()) {
            if (entry.getValue().knowsThread(email.getThreadID())) {
                return new RoutingDecision(entry.getKey(), 0.9, "thread already handled by " + entry.getValue().getName());
            }
        }
        return null;
    }
}
