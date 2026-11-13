import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Email du jeu de test, avec l'agent attendu (verite terrain).
// "acceptable" : autres agents juges corrects pour un email ambigu (exactitude "souple").
// "reply" : l'email fait partie du sous-ensemble utilise pour evaluer les reponses.
public record LabelledEmail(String id, String category, String address, String name,
                            String subject, String body, int threadId,
                            String expected, Set<String> acceptable, boolean reply) {

    public static List<LabelledEmail> load(String file) throws IOException {
        List<LabelledEmail> list = new ArrayList<>();
        for (JsonNode n : new ObjectMapper().readTree(new File(file))) {
            String expected = n.path("expected").asText().toUpperCase(Locale.ROOT);
            Set<String> acceptable = new LinkedHashSet<>();
            acceptable.add(expected);
            for (JsonNode a : n.path("acceptable")) acceptable.add(a.asText().toUpperCase(Locale.ROOT));
            list.add(new LabelledEmail(n.path("id").asText(), n.path("category").asText("clear"),
                    n.path("address").asText(), n.path("name").asText(""),
                    n.path("subject").asText(), n.path("body").asText(), n.path("threadId").asInt(),
                    expected, Set.copyOf(acceptable), n.path("reply").asBoolean(false)));
        }
        return list;
    }

    public Email toEmail() {
        Email email = new Email(address, subject, body, DemoEmail.today(), threadId);
        if (!name.isBlank()) email.setSenderName(name);
        return email;
    }

    public boolean accepts(String role) {
        return acceptable.contains(role);
    }
}
