import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CriticAgent {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MIN_APPROVED_SCORE = 7;

    private final LLM llm;
    private final String systemPromptFile;
    private volatile String systemPrompt;
    private volatile boolean enabled;

    public CriticAgent(LLM llm, String systemPromptFile, boolean enabled) throws IOException {
        this.llm = llm;
        this.systemPromptFile = systemPromptFile;
        this.enabled = enabled;
        this.systemPrompt = Files.readString(Path.of(systemPromptFile));
    }

    public String getName()            { return "CriticAgent"; }
    public String getSystemPrompt()    { return systemPrompt; }
    public String getSystemPromptFile(){ return systemPromptFile; }
    public boolean isEnabled()         { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void saveSystemPrompt(String prompt) throws IOException {
        Files.writeString(Path.of(systemPromptFile), prompt);
        this.systemPrompt = prompt;
    }

    public Critique review(Email email, Agent author, String draft) {
        String message = "RULES GIVEN TO THE AGENT THAT WROTE THE DRAFT (" + author.getName() + "):\n"
                + author.getSystemPrompt()
                + "\n\nINFORMATION KNOWN BY THIS AGENT:\n" + knowledge(author.recall(email.getThreadID()))
                + "\nEMAIL RECEIVED:\nFrom: " + email.getSenderAddress()
                + "\nSubject: " + email.getSubject()
                + "\n\n" + email.getBody()
                + "\n\nDRAFT REPLY TO REVIEW:\n" + draft
                + "\n\nReview the draft. Answer ONLY with JSON like "
                + "{\"score\": 8, \"approved\": true, \"issues\": [\"...\"]}";
        try {
            return parse(llm.ask(systemPrompt, message));
        } catch (LlmException e) {
            return Critique.unavailable(e.getMessage());
        }
    }

    private Critique parse(String answer) {
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end < start) return Critique.unavailable("the critic did not answer in JSON");
        try {
            JsonNode json = MAPPER.readTree(answer.substring(start, end + 1));
            int score = json.path("score").asInt(-1);
            if (score < 0) return Critique.unavailable("no score in the critic answer");
            List<String> issues = new ArrayList<>();
            for (JsonNode issue : json.path("issues")) {
                if (!issue.asText().isBlank()) issues.add(issue.asText().trim());
            }
            boolean approved = json.path("approved").asBoolean(score >= MIN_APPROVED_SCORE)
                               && score >= MIN_APPROVED_SCORE;
            return Critique.of(score, approved, issues);
        } catch (IOException e) {
            return Critique.unavailable("the critic answer is not valid JSON");
        }
    }

    private String knowledge(List<Belief> beliefs) {
        StringBuilder sb = new StringBuilder();
        for (Belief b : beliefs) {
            sb.append("- [").append(b.getSource()).append("] ").append(b.getContent()).append("\n");
        }
        return sb.toString();
    }
}
