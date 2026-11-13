import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Arbitre : on demande au LLM local lequel des agents candidats convient le mieux.
// Si la collaboration est possible (au moins deux agents concernes), il peut aussi designer des agents
// contributeurs parmi eux quand l'email contient des demandes distinctes relevant d'autres agents.
public class LlmClassifierStep implements Arbiter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LLM llm;
    private final Map<String, Agent> agents;

    public LlmClassifierStep(LLM llm, Map<String, Agent> agents) {
        this.llm = llm;
        this.agents = agents;
    }

    public RoutingDecision choose(Email email, Set<String> candidates) {
        return choose(email, candidates, Set.of());
    }

    public RoutingDecision choose(Email email, Set<String> candidates, Set<String> contributors) {
        boolean allowCollaboration = !contributors.isEmpty();
        StringBuilder list = new StringBuilder();
        for (String role : candidates) {
            list.append("- ").append(role).append(": ")
                .append(agents.get(role).getDescription()).append("\n");
        }
        if (allowCollaboration) {
            list.append("\nAgents that may contribute a part of the reply:\n");
            for (String role : contributors) {
                list.append("- ").append(role).append(": ")
                    .append(agents.get(role).getDescription()).append("\n");
            }
        }

        String systemPrompt = "You classify emails. Answer ONLY with JSON like "
                + "{\"role\": \"ROLE\", \"confidence\": 0.8} and nothing else.";
        String question = "\n\nWhich agent should answer this email?";
        if (allowCollaboration) {
            systemPrompt = "You classify emails. Answer ONLY with JSON like "
                    + "{\"role\": \"ROLE\", \"confidence\": 0.8, \"collaborators\": []} and nothing else.";
            question = "\n\nWhich agent should answer this email? \"role\" is the main agent: it writes the reply."
                    + "\nIf, and only if, the email also contains a distinct request that clearly belongs to another"
                    + " agent that may contribute, add that agent to \"collaborators\" with the part it must answer,"
                    + " for example:"
                    + "\n{\"role\": \"ROLE_A\", \"confidence\": 0.8, \"collaborators\": "
                    + "[{\"role\": \"ROLE_B\", \"part\": \"short description of the request it answers\"}]}"
                    + "\nOtherwise leave \"collaborators\" empty.";
        }
        String message = "Available agents:\n" + list
                + "\nEMAIL:\nFrom: " + email.getSenderAddress()
                + "\nSubject: " + email.getSubject()
                + "\n\n" + email.getBody()
                + question;

        String answer = llm.ask(systemPrompt, message);

        try {
            // le modele peut ajouter du texte autour du JSON, on ne garde que {...}
            int start = answer.indexOf('{');
            int end = answer.lastIndexOf('}');
            if (start < 0 || end < start) return null;

            JsonNode json = MAPPER.readTree(answer.substring(start, end + 1));
            String role = json.path("role").asText().toUpperCase(Locale.ROOT);
            double confidence = json.path("confidence").asDouble(0);
            if (!candidates.contains(role)) return null;
            List<RoutingDecision.Collaborator> collaborators = new ArrayList<>();
            if (allowCollaboration) {
                for (JsonNode c : json.path("collaborators")) {
                    collaborators.add(new RoutingDecision.Collaborator(
                            c.path("role").asText().trim().toUpperCase(Locale.ROOT), c.path("part").asText().trim()));
                }
            }
            // les collaborateurs proposes sont valides par le RoutingAgent (agents concernes, doublons, limite)
            return new RoutingDecision(role, confidence, "LLM classification", collaborators);
        } catch (Exception e) {
            return null;
        }
    }
}
