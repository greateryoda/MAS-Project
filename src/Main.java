import java.util.List;
import java.util.Map;

public class Main {

    private static final String URL   = "http://127.0.0.1:1234/v1/chat/completions";
    private static final String MODEL = "google/gemma-4-e4b";

    public static void main(String[] args) throws Exception {
        LLM llm = new LmStudioClient(URL, MODEL);
        Map<String, Agent> agents = new AgentFactory(llm).createAllAgents();
        TemporaryContactBook contacts = new TemporaryContactBook("agents/contacts.json");

        // ordre de la chaine de routage
        List<RoutingStep> steps = List.of(
                new ThreadStep(agents),
                new ContactStep(contacts),
                new KeywordStep(agents),
                new DomainStep(),
                new LlmClassifierStep(llm, agents));
        RoutingAgent router = new RoutingAgent(agents, contacts, steps);


        String subject = "Subject: Concernant le TP";
        String message = "Bonjour monsieur, si je rends le tp un peu en retard, y aura un probleme?";
        String senderAddress = "yunus-emre.dogan@etu.univ-cotedazur.fr";
        String date = "18/10/2026";
        int threadID = 3131;

        Email email = new Email(senderAddress, subject, message, date, threadID);

        Proposal proposal = router.RouteMail(email);

        System.out.println("Router chose: " + proposal.getAgent().getName()
                + " (" + proposal.getDecision().getReason()
                + ", confidence " + proposal.getDecision().getConfidence() + ")");
        System.out.println("----- Draft -----");
        System.out.println(proposal.getDraft());
        
    }
}
