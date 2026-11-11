import java.io.File;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Main {

    public static void main(String[] args) throws Exception {
        // adresse du serveur et modele : config.json a la racine du projet
        JsonNode config = new ObjectMapper().readTree(new File("config.json"));
        LLM llm = new LmStudioClient(config.path("url").asText(), config.path("model").asText());
        Map<String, Agent> agents = new AgentFactory(llm).createAllAgents();
        TemporaryContactBook contacts = new TemporaryContactBook("agents/contacts.json");

        // signaux de routage (rapides), le LLM ne sert que d'arbitre en cas de doute
        List<RoutingStep> steps = List.of(
                new ThreadStep(agents),
                new ContactStep(contacts),
                new KeywordStep(agents),
                new DomainStep(contacts));
        Arbiter arbiter = new LlmClassifierStep(llm, agents);
        Validator user = new ConsoleValidator();
        RoutingAgent router = new RoutingAgent(agents, contacts, steps, arbiter, user);


        String subject = "Subject: Concernant l'examen";
        String message = "Bonjour monsieur, pouvez-vous me dire quels sont les parties pouvant tomber dans l'examen? ";
        String senderAddress = "yunus-emre.dogan@etu.univ-cotedazur.fr";
        String date = "18/10/2026";
        int threadID = 3131;

        Email email = new Email(senderAddress, subject, message, date, threadID);

        Intention result;
        try {
            result = router.RouteMail(email);
        } catch (LlmException e) {
            System.err.println("\n" + e.getMessage());
            System.exit(1);
            return;
        }

        if (result.getStatus() == Intention.Status.ACHIEVED) {
            System.out.println("\nReply validated and saved in memory:");
            System.out.println(result.getFinalReply());
        } else {
            System.out.println("\nNo reply sent (" + result.getAttempts() + " drafts rejected).");
        }
    }
}
