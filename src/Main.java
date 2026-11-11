import java.util.List;
import java.util.Map;

public class Main {

    private static final String URL   = "http://127.0.0.1:1234/v1/chat/completions";
    private static final String MODEL = "google/gemma-4-e4b";

    public static void main(String[] args) throws Exception {
        LLM llm = new LmStudioClient(URL, MODEL);
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

        Intention result = router.RouteMail(email);

        if (result.getStatus() == Intention.Status.ACHIEVED) {
            System.out.println("\nReply validated and saved in memory:");
            System.out.println(result.getFinalReply());
        } else {
            System.out.println("\nNo reply sent (" + result.getAttempts() + " drafts rejected).");
        }
    }
}
