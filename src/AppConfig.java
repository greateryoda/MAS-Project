import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Lecture unique de config.json (auparavant dupliquee dans App et Main).
// Toutes les cles sauf url et model sont optionnelles.
// "collaboration" : plusieurs agents repondent ensemble a un email qui les concerne tous (active par defaut).
public record AppConfig(String url, String model,
                        boolean criticEnabled, String criticPromptFile,
                        String contextStrategy, int maxBeliefs, int maxThreadMessages,
                        String embeddingModel,
                        boolean logEnabled, String logFile, boolean logIncludeText,
                        boolean collaborationEnabled, int collaborationMaxAgents) {

    public static AppConfig load(String file) throws IOException {
        JsonNode root = new ObjectMapper().readTree(new File(file));
        JsonNode critic = root.path("critic");
        JsonNode context = root.path("context");
        JsonNode log = root.path("log");
        JsonNode collaboration = root.path("collaboration");
        return new AppConfig(
                root.path("url").asText(),
                root.path("model").asText(),
                critic.path("enabled").asBoolean(true),
                critic.path("systemPromptFile").asText("critic/attitude_critic.txt"),
                context.path("strategy").asText(ContextSelectorFactory.LEXICAL),
                context.path("maxBeliefs").asInt(8),
                context.path("maxThreadMessages").asInt(6),
                root.path("embedding").path("model").asText(""),
                log.path("enabled").asBoolean(true),
                log.path("file").asText("logs/decisions.jsonl"),
                log.path("includeText").asBoolean(false),
                collaboration.path("enabled").asBoolean(true),
                collaboration.path("maxAgents").asInt(2));
    }

    public LmStudioClient llm() {
        return new LmStudioClient(url, model);
    }

    public String embeddingUrl() {
        return url.replace("/chat/completions", "/embeddings");
    }

    public ContextSelectorFactory contextSelectors() {
        Embedder embedder = embeddingModel.isBlank() ? null
                : new CachingEmbedder(new LmStudioEmbedder(embeddingUrl(), embeddingModel));
        return new ContextSelectorFactory(contextStrategy, maxBeliefs, maxThreadMessages, embedder);
    }

    public EmailAssistant assistant(LLM llm, Validator validator) throws IOException {
        EmailAssistant assistant = new EmailAssistant(llm, validator, criticPromptFile, criticEnabled,
                                                      contextSelectors());
        // maxAgents : agent principal compris (2 = un contributeur au plus)
        assistant.getRouter().setMaxCollaborators(collaborationEnabled ? collaborationMaxAgents - 1 : 0);
        if (logEnabled) assistant.addListener(new DecisionLogger(logFile, model, logIncludeText));
        return assistant;
    }
}
