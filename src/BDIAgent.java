import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BDIAgent implements Agent {
    private final String configPath;
    private final LLM llm;
    private final BeliefBase beliefs = new BeliefBase();
    private final String memoryFile;
    private final String name;
    private final String role;
    private volatile String systemPrompt;
    private volatile String description;
    private volatile List<String> keywords;
    private volatile int maxRetries;
    private final ContextSelectorFactory selectors;
    private volatile ContextSelector selector;
    private volatile int maxBeliefs;
    private volatile int maxThreadMessages;

    public BDIAgent(String configPath, LLM llm) throws IOException {
        this(configPath, llm, ContextSelectorFactory.defaults());
    }

    public BDIAgent(String configPath, LLM llm, ContextSelectorFactory selectors) throws IOException {
        this.configPath = configPath;
        this.llm = llm;
        this.selectors = selectors;
        AgentConfig config = AgentConfig.load(configPath);
        this.name = config.getName();
        this.role = config.getRole();
        this.memoryFile = config.getMemoryFile();
        applyConfig(config);
        beliefs.loadMemory(memoryFile);
    }

    public synchronized void reload() throws IOException {
        applyConfig(AgentConfig.load(configPath));
    }

    private void applyConfig(AgentConfig config) throws IOException {
        this.systemPrompt = Files.readString(Path.of(config.getSystemPromptFile()));
        this.description = config.getDescription();
        this.keywords = List.copyOf(config.getKeywords());
        this.maxRetries = config.getMaxRetries();
        this.selector = selectors.create(config.getContextStrategy());
        this.maxBeliefs = config.getMaxBeliefs() > 0 ? config.getMaxBeliefs() : selectors.defaultMaxBeliefs();
        this.maxThreadMessages = config.getMaxThreadMessages() > 0
                ? config.getMaxThreadMessages() : selectors.defaultMaxThreadMessages();
        beliefs.loadContext(config.getContextFiles());
    }

    public String getName() { return name; }
    public String getRole() { return role; }
    public String getDescription() { return description; }
    public List<String> getKeywords() { return keywords; }
    public String getSystemPrompt() { return systemPrompt; }

    public synchronized List<Belief> recall(int threadID) {
        return beliefs.get(threadID);
    }

    // on ne transmet au LLM que les croyances de base utiles a cet email (strategie configurable)
    // et les derniers messages du fil : le fil recent est toujours pertinent, l'historique ancien beaucoup moins
    public ContextSelection selectContext(Email email) {
        List<Belief> base;
        List<Belief> thread;
        synchronized (this) {
            base = beliefs.base();
            thread = beliefs.thread(email.getThreadID());
        }
        ContextSelector current = selector;
        List<Belief> chosen = current.select(email, base, maxBeliefs);
        List<Belief> recent = thread.size() > maxThreadMessages
                ? thread.subList(thread.size() - maxThreadMessages, thread.size()) : thread;
        return new ContextSelection(current.name(), List.copyOf(chosen), base.size(),
                                    List.copyOf(recent), thread.size());
    }

    // l'agent a deja traite un email de ce fil
    public synchronized boolean knowsThread(int threadID) {
        return threadID != Belief.NO_THREAD && beliefs.contains(threadID);
    }

    public Intention adopt(Email email) {
        return new Intention(email, maxRetries);
    }

    public String draft(Intention intention) {
        Email email = intention.getEmail();
        String history = toPromptText(selectContext(email).all());

        String sender = email.getSenderAddress();
        if (email.getSenderName() != null) {
            sender = email.getSenderName() + " <" + sender + ">";
        } else {
            sender += " (name unknown: use a neutral greeting, never a placeholder like [Name])";
        }

        String message = "KNOWN INFORMATION AND HISTORY (CONSIDER THE HISTORY WHEN YOU ANSWER):\n" + history
                    + "\n\nNEW EMAIL:\nFrom: " + sender
                    + "\nSubject: " + email.getSubject()
                    + "\n\n" + email.getBody()
                    + "\n\nWrite a reply draft to this email.";

        // collaboration : d'autres agents specialises ont repondu a une partie de l'email,
        // l'agent principal fusionne leurs notes dans une reponse unique, avec son propre ton
        List<Contribution> notes = intention.getContributions().stream().filter(Contribution::available).toList();
        if (!notes.isEmpty()) {
            message += "\n\nCONTRIBUTIONS FROM OTHER SPECIALIZED AGENTS (each one knows its own domain; "
                    + "use their information for the part they answered, do not contradict it and do not add "
                    + "facts they did not give):\n";
            for (Contribution c : notes) {
                message += "- " + c.agentName() + ", about \"" + c.part() + "\":\n" + c.text().trim() + "\n";
            }
            message += "Write ONE reply that answers the whole email: your own part with your known information, "
                    + "and the other parts with these contributions.";
        }

        // apres un refus : on montre le brouillon precedent et les remarques de l'utilisateur
        if (!intention.getRejections().isEmpty()) {
            message += "\n\nYOUR PREVIOUS DRAFT:\n" + intention.getDraft()
                    + "\n\nThe user rejected your drafts with these remarks:\n";
            for (String reason : intention.getRejections()) {
                message += "- " + (reason.isBlank() ? "(no reason given)" : reason) + "\n";
            }
            message += "Write a new draft that takes all these remarks into account.";
        }

        String answer = llm.ask(systemPrompt, message);
        intention.newDraft(answer);
        return answer;
    }

    // collaboration : l'agent ne redige pas l'email, il fournit a l'agent principal une note
    // sur sa partie, avec ses regles (system prompt) et le contexte qu'il juge pertinent
    public String contribute(Email email, String part, String leadName) {
        String known = toPromptText(selectContext(email).all());
        String message = "COLLABORATION REQUEST from " + leadName + ", who writes the reply to the email below.\n"
                + "Your part: " + part + "\n\n"
                + "KNOWN INFORMATION AND HISTORY:\n" + known
                + "\nEMAIL:\nFrom: " + email.getSenderAddress()
                + "\nSubject: " + email.getSubject()
                + "\n\n" + email.getBody()
                + "\n\nAnswer ONLY your part, for " + leadName + " (not for the sender): a short factual note of "
                + "2 to 5 sentences, no greeting and no signature. Respect your rules. Use only the known "
                + "information; if something is not known, say that it must be confirmed.";
        return llm.ask(systemPrompt, message);
    }

    public void reconsider(Intention intention, Feedback feedback) {
        switch (feedback.getType()) {
            case ACCEPT -> { intention.achieved(intention.getDraft()); commit(intention); }
            case MODIFY -> { intention.achieved(feedback.getText());   commit(intention); }
            case REJECT -> intention.rejected(feedback.getText());
            case DISCARD -> intention.discarded();
        }
    }

    // seulement quand l'utilisateur a valide : on memorise l'email recu et la reponse envoyee
    private synchronized void commit(Intention intention) {
        Email email = intention.getEmail();
        beliefs.add(new Belief("From: " + email.getSenderAddress() + " | Subject: " + email.getSubject()
                                + " | Context: " + email.getBody(),
                               Belief.DEFAULT_CONFIDENCE, Belief.Source.EMAIL,
                               email.getThreadID(), email.getDate()));
        beliefs.add(new Belief("To: " + email.getSenderAddress() + " | Reply: " + intention.getFinalReply(),
                               Belief.DEFAULT_CONFIDENCE, Belief.Source.REPLY,
                               email.getThreadID(), email.getDate()));
        try {
            beliefs.saveMemory(memoryFile);
        } catch (IOException e) {
            System.err.println("Could not save memory: " + e.getMessage());
        }
    }

    private String toPromptText(List<Belief> list) {
        StringBuilder sb = new StringBuilder();
        for (Belief b : list) {
            sb.append("- [").append(b.getSource()).append(", ").append(b.getDate())
            .append("] ").append(b.getContent()).append("\n");
        }
        return sb.toString();
    }
}
