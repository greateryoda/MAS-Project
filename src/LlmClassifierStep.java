import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Arbitre : on demande au LLM local lequel des agents candidats convient le mieux.
public class LlmClassifierStep implements Arbiter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LLM llm;
    private final Map<String, Agent> agents;

    public LlmClassifierStep(LLM llm, Map<String, Agent> agents) {
        this.llm = llm;
        this.agents = agents;
    }

    public RoutingDecision choose(Email email, Set<String> candidates) {
        StringBuilder list = new StringBuilder();
        for (String role : candidates) {
            list.append("- ").append(role).append(": ")
                .append(agents.get(role).getDescription()).append("\n");
        }

        String systemPrompt = "You classify emails. Answer ONLY with JSON like "
                + "{\"role\": \"ROLE\", \"confidence\": 0.8} and nothing else.";
        String message = "Available agents:\n" + list
                + "\nEMAIL:\nFrom: " + email.getSenderAddress()
                + "\nSubject: " + email.getSubject()
                + "\n\n" + email.getBody()
                + "\n\nWhich agent should answer this email?";

        String answer = llm.ask(systemPrompt, message);

        try {
            // le modele peut ajouter du texte autour du JSON, on ne garde que {...}
            int start = answer.indexOf('{');
            int end = answer.lastIndexOf('}');
            if (start < 0 || end < start) return null;

            JsonNode json = MAPPER.readTree(answer.substring(start, end + 1));
            String role = json.path("role").asText().toUpperCase();
            double confidence = json.path("confidence").asDouble(0);
            if (!candidates.contains(role)) return null;
            return new RoutingDecision(role, confidence, "LLM classification");
        } catch (Exception e) {
            return null;
        }
    }
}
