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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentConfig(String name, List<String> contextFiles, String memoryFile,
                        String systemPromptFile, String role, int maxRetries) {
        this.name = name;
        this.contextFiles = contextFiles;
        this.memoryFile = memoryFile;
        this.systemPromptFile = systemPromptFile;
        this.role = role;
        this.maxRetries = maxRetries;
    }

    public String getName()             { return name; }
    public List<String> getContextFiles() { return contextFiles; }
    public String getMemoryFile()       { return memoryFile; }
    public String getSystemPromptFile() { return systemPromptFile; }
    public int getMaxRetries()          { return maxRetries; }
    public String getRole()             { return role; }

    
    public static AgentConfig load(String path) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readString(Path.of(path)));

        List<String> contextFiles = new ArrayList<>();
        for (JsonNode file : root.path("beliefs").path("contextFiles")) {
            contextFiles.add(file.asText());
        }

        return new AgentConfig(
                root.path("name").asText(),
                contextFiles,
                root.path("beliefs").path("memoryFile").asText(),
                root.path("attitude").path("systemPromptFile").asText(),
                root.path("role").asText(),
                root.path("desires").path("maxRetries").asInt(3));
    }
}