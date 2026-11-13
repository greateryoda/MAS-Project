import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public class AgentConfig{
    private final String name;
    private final List<String> contextFiles;
    private final String memoryFile;
    private final String systemPromptFile;
    private final String role;
    private final int maxRetries;
    private final String description;
    private final List<String> keywords;
    private final String contextStrategy;   // null : valeur par defaut de config.json
    private final int maxBeliefs;           // -1 : valeur par defaut
    private final int maxThreadMessages;    // -1 : valeur par defaut

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentConfig(String name, List<String> contextFiles, String memoryFile,
                        String systemPromptFile, String role, int maxRetries,
                        String description, List<String> keywords,
                        String contextStrategy, int maxBeliefs, int maxThreadMessages) {
        this.name = name;
        this.contextFiles = contextFiles;
        this.memoryFile = memoryFile;
        this.systemPromptFile = systemPromptFile;
        this.role = role;
        this.maxRetries = maxRetries;
        this.description = description;
        this.keywords = keywords;
        this.contextStrategy = contextStrategy;
        this.maxBeliefs = maxBeliefs;
        this.maxThreadMessages = maxThreadMessages;
    }

    public String getName()             { return name; }
    public List<String> getContextFiles() { return contextFiles; }
    public String getMemoryFile()       { return memoryFile; }
    public String getSystemPromptFile() { return systemPromptFile; }
    public int getMaxRetries()          { return maxRetries; }
    public String getRole()             { return role; }
    public String getDescription()      { return description; }
    public List<String> getKeywords()   { return keywords; }
    public String getContextStrategy()  { return contextStrategy; }
    public int getMaxBeliefs()          { return maxBeliefs; }
    public int getMaxThreadMessages()   { return maxThreadMessages; }

    
    public static AgentConfig load(String path) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readString(Path.of(path)));

        List<String> contextFiles = new ArrayList<>();
        for (JsonNode file : root.path("beliefs").path("contextFiles")) {
            contextFiles.add(file.asText());
        }

        List<String> keywords = new ArrayList<>();
        for (JsonNode keyword : root.path("keywords")) {
            keywords.add(keyword.asText());
        }

        return new AgentConfig(
                root.path("name").asText(),
                contextFiles,
                root.path("beliefs").path("memoryFile").asText(),
                root.path("attitude").path("systemPromptFile").asText(),
                root.path("role").asText(),
                root.path("desires").path("maxRetries").asInt(3),
                root.path("description").asText(),
                keywords,
                // optionnel : "context": {"strategy": "lexical", "maxBeliefs": 8, "maxThreadMessages": 6}
                root.path("context").hasNonNull("strategy") ? root.path("context").path("strategy").asText() : null,
                root.path("context").path("maxBeliefs").asInt(-1),
                root.path("context").path("maxThreadMessages").asInt(-1));
    }
}