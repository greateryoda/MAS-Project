import java.io.File;
import java.io.IOException;
import java.util.List;

// Facade : construit le systeme (registre des agents, chaine de routage, arbitre, critique).
public class EmailAssistant {
    private static final String AGENTS_DIR = "agents";
    private static final String CONTACTS_FILE = "agents/contacts.json";

    private final AgentRegistry registry;
    private final TemporaryContactBook contacts;
    private final CriticAgent critic;
    private final RoutingAgent router;

    public EmailAssistant(LLM llm, Validator validator, String criticPromptFile, boolean criticEnabled)
            throws IOException {
        this(llm, validator, criticPromptFile, criticEnabled, ContextSelectorFactory.defaults());
    }

    public EmailAssistant(LLM llm, Validator validator, String criticPromptFile, boolean criticEnabled,
                          ContextSelectorFactory selectors) throws IOException {
        this.registry = new AgentRegistry(new File(AGENTS_DIR), new AgentFactory(llm, selectors));
        this.contacts = new TemporaryContactBook(CONTACTS_FILE);
        this.critic = new CriticAgent(llm, criticPromptFile, criticEnabled);
        this.router = new RoutingAgent(registry.getAgents(), contacts, defaultSteps(registry, contacts),
                                       new LlmClassifierStep(llm, registry.getAgents()), validator, critic);
    }

    // signaux de routage rapides ; le LLM ne sert que d'arbitre en cas de doute
    public static List<RoutingStep> defaultSteps(AgentRegistry registry, TemporaryContactBook contacts) {
        return List.of(
                new ThreadStep(registry.getAgents()),
                new ContactStep(contacts),
                new KeywordStep(registry.getAgents()),
                new DomainStep(contacts));
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
    public RoutingAgent getRouter()            { return router; }
}
