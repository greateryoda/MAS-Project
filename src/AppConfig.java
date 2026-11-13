import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Lecture unique de config.json (auparavant dupliquee dans App et Main).
// Toutes les cles sauf url et model sont optionnelles.
public record AppConfig(String url, String model,
                        boolean criticEnabled, String criticPromptFile,
                        String contextStrategy, int maxBeliefs, int maxThreadMessages,
                        String embeddingModel,
                        boolean logEnabled, String logFile, boolean logIncludeText) {

    public static AppConfig load(String file) throws IOException {
        JsonNode root = new ObjectMapper().readTree(new File(file));
        JsonNode critic = root.path("critic");
        JsonNode context = root.path("context");
        JsonNode log = root.path("log");
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
                log.path("includeText").asBoolean(false));
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
        if (logEnabled) assistant.addListener(new DecisionLogger(logFile, model, logIncludeText));
        return assistant;
    }
}
