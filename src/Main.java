import java.util.Map;

public class Main {

    private static final String URL   = "http://127.0.0.1:1234/v1/chat/completions";
    private static final String MODEL = "google/gemma-4-e4b";

    public static void main(String[] args) throws Exception {
        LLM llm = new LmStudioClient(URL, MODEL);
        Map<String, Agent> agents = new AgentFactory(llm).createAllAgents();
        RoutingAgent router = new RoutingAgent(agents, new TemporaryContactBook("agents/contacts.json"));


        String subject = "Subject";
        String message = "Message";
        String senderAddress = "yunus-emre.dogan@etu.univ-cotedazur.fr";
        String date = "18/10/2026";
        int threadID = 3131;

        Email email = new Email(senderAddress, subject, message, date, threadID);

        Agent chosenAgent = router.RouteMail(email);

        System.out.println("Router chose: " + chosenAgent.getName());
        System.out.println("----- Draft -----");
        System.out.println(chosenAgent.handle(email));
        
    }
}
