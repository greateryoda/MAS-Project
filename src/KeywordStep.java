import java.util.Map;
import java.util.regex.Pattern;

// Cherche les mots-cles de chaque agent dans le sujet et le corps de l'email.
public class KeywordStep implements RoutingStep {
    private final Map<String, Agent> agents;

    public KeywordStep(Map<String, Agent> agents) {
        this.agents = agents;
    }

    public RoutingDecision decide(Email email) {
        String text = (email.getSubject() + " " + email.getBody()).toLowerCase();

        String bestRole = null;
        int bestHits = 0;
        boolean tie = false;

        for (Map.Entry<String, Agent> entry : agents.entrySet()) {
            int hits = 0;
            for (String keyword : entry.getValue().getKeywords()) {
                if (containsWord(text, keyword.toLowerCase())) hits++;
            }
            if (hits > bestHits) {
                bestRole = entry.getKey();
                bestHits = hits;
                tie = false;
            } else if (hits == bestHits && hits > 0) {
                tie = true;
            }
        }

        // aucun mot-cle, ou egalite entre deux agents : on laisse decider le maillon suivant
        if (bestRole == null || tie) return null;

        double confidence = bestHits / (bestHits + 1.0); // 1 mot -> 0.5, 2 -> 0.67, 3 -> 0.75
        return new RoutingDecision(bestRole, confidence, bestHits + " keyword(s) matched");
    }

    // mot entier, pour eviter que "tp" soit trouve dans "http"
    private boolean containsWord(String text, String word) {
        return Pattern.compile("(?U)\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }
}
