import java.util.Locale;
import java.util.List;
import java.util.Map;

// Resultat complet du routage : decision + tout ce qui permet de l'expliquer
// (votes par agent, signaux observes, recours a l'arbitre LLM).
public record RoutingOutcome(RoutingDecision decision, Map<String, Double> scores,
                             List<String> signals, boolean arbiterConsulted, long millis) {

    public boolean fallback() {
        return decision.getConfidence() == 0.0 && RoutingAgent.FALLBACK_ROLE.equals(decision.getRole());
    }

    // explication lisible du choix, pour l'utilisateur et le journal
    public String explanation() {
        String role = decision.getRole();
        if (fallback()) return "No agent was suitable enough: fallback to " + role;
        if (arbiterConsulted) {
            return "Signals were " + (scores.size() > 1 ? "conflicting " + scores.keySet() : "too weak")
                    + ": the LLM arbiter chose " + role
                    + String.format(Locale.ROOT, " (confidence %.2f)", decision.getConfidence());
        }
        double total = scores.values().stream().mapToDouble(Double::doubleValue).sum();
        return String.format(Locale.ROOT, "%s chosen without the LLM: %.0f%% of the votes (score %.2f of %.2f)",
                role, 100 * decision.getConfidence(), scores.getOrDefault(role, 0.0), total);
    }
}
