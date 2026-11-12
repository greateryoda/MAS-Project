import java.io.File;
import java.io.IOException;
import java.util.List;

public class EmailAssistant {
    private static final String AGENTS_DIR = "agents";
    private static final String CONTACTS_FILE = "agents/contacts.json";

    private final AgentRegistry registry;
    private final TemporaryContactBook contacts;
    private final CriticAgent critic;
    private final RoutingAgent router;

    public EmailAssistant(LLM llm, Validator validator, String criticPromptFile, boolean criticEnabled)
            throws IOException {
        this.registry = new AgentRegistry(new File(AGENTS_DIR), new AgentFactory(llm));
        this.contacts = new TemporaryContactBook(CONTACTS_FILE);
        this.critic = new CriticAgent(llm, criticPromptFile, criticEnabled);

        List<RoutingStep> steps = List.of(
                new ThreadStep(registry.getAgents()),
                new ContactStep(contacts),
                new KeywordStep(registry.getAgents()),
                new DomainStep(contacts));
        Arbiter arbiter = new LlmClassifierStep(llm, registry.getAgents());
        this.router = new RoutingAgent(registry.getAgents(), contacts, steps, arbiter, validator, critic);
    }

    public Intention process(Email email) {
        return router.RouteMail(email);
    }

    public void addListener(WorkflowListener listener) {
        router.addListener(listener);
    }

    public AgentRegistry getRegistry()         { return registry; }
    public TemporaryContactBook getContacts()  { return contacts; }
    public CriticAgent getCritic()             { return critic; }
}
