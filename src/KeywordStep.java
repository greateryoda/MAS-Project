import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// Cherche les mots-cles de chaque agent dans le sujet et le corps de l'email.
public class KeywordStep implements RoutingStep {
    private final Map<String, Agent> agents;

    public KeywordStep(Map<String, Agent> agents) {
        this.agents = agents;
    }

    public RoutingDecision decide(Email email) {
        String bestRole = null;
        int bestHits = 0;
        boolean tie = false;

        for (Map.Entry<String, Integer> entry : hits(email).entrySet()) {
            int hits = entry.getValue();
            if (hits > bestHits) {
                bestRole = entry.getKey();
                bestHits = hits;
                tie = false;
            } else if (hits == bestHits) {
                tie = true;
            }
        }

        // aucun mot-cle, ou egalite entre deux agents : on laisse decider le maillon suivant
        if (bestRole == null || tie) return null;

        double confidence = bestHits / (bestHits + 1.0); // 1 mot -> 0.5, 2 -> 0.67, 3 -> 0.75
        return new RoutingDecision(bestRole, confidence, bestHits + " keyword(s) matched");
    }

    // collaboration : tous les agents dont au moins un mot-cle apparait sont concernes,
    // pas seulement celui qui en a le plus (ex. inscription [administration] a un workshop du projet [recherche])
    public Set<String> concerned(Email email) {
        return hits(email).keySet();
    }

    // nombre de mots-cles trouves par agent (agents sans mot-cle trouve exclus)
    private Map<String, Integer> hits(Email email) {
        String text = (email.getSubject() + " " + email.getBody()).toLowerCase();
        Map<String, Integer> hits = new LinkedHashMap<>();
        for (Map.Entry<String, Agent> entry : agents.entrySet()) {
            int n = 0;
            for (String keyword : entry.getValue().getKeywords()) {
                if (containsWord(text, keyword.toLowerCase())) n++;
            }
            if (n > 0) hits.put(entry.getKey(), n);
        }
        return hits;
    }

    // mot entier, pour eviter que "tp" soit trouve dans "http"
    private boolean containsWord(String text, String word) {
        return Pattern.compile("(?U)\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }
}
