import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BDIAgent implements Agent {
    private final String name;
    private final String role;
    private final LLM llm;
    private final BeliefBase beliefs = new BeliefBase();
    private final String systemPrompt;
    private final String memoryFile;

    public BDIAgent(String configPath, LLM llm) throws IOException {
        this.llm = llm;
        AgentConfig config = AgentConfig.load(configPath);
        this.name = config.getName();
        this.role = config.getRole();
        this.systemPrompt = Files.readString(Path.of(config.getSystemPromptFile()));
        this.memoryFile = config.getMemoryFile();
        beliefs.loadContext(config.getContextFiles());
        beliefs.loadMemory(memoryFile);
    }

    public String getName() { return name; }
    public String getRole() { return role; }

    public String handle(Email email) {
        String history = toPromptText(beliefs.get(email.getThreadID()));
        // System.out.println(history);

        String message = "KNOWN INFORMATION AND HISTORY (CONSIDER THE HISTORY WHEN YOU ANSWER):\n" + history
                    + "\n\nNEW EMAIL:\nFrom: " + email.getSenderAddress()
                    + "\nSubject: " + email.getSubject()
                    + "\n\n" + email.getBody()
                    + "\n\nWrite a reply draft to this email.";

        String answer = llm.ask(systemPrompt, message);

        Belief new_belief = new Belief("From: " + email.getSenderAddress() + " | Subject: " + email.getSubject() + " | Context: " + email.getBody(),
                                        Belief.DEFAULT_CONFIDENCE,
                                        Belief.Source.EMAIL,
                                        email.getThreadID(),
                                        email.getDate());

        beliefs.add(new_belief);
        try {
            beliefs.saveMemory(memoryFile);
        } catch (IOException e) {
            System.err.println("Could not save memory: " + e.getMessage());
        }

        return answer;
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
