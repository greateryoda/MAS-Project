import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AgentRegistry {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final File agentsDir;
    private final AgentFactory factory;
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final Map<String, File> folders = new ConcurrentHashMap<>();

    public AgentRegistry(File agentsDir, AgentFactory factory) throws IOException {
        this.agentsDir = agentsDir;
        this.factory = factory;
        File[] dirs = agentsDir.listFiles(File::isDirectory);
        if (dirs == null) throw new IOException("Agents folder not found: " + agentsDir.getAbsolutePath());
        for (File dir : dirs) {
            if (new File(dir, "agent.json").exists()) register(dir);
        }
    }

    public Map<String, Agent> getAgents() {
        return agents;
    }

    public List<Agent> list() {
        List<Agent> list = new ArrayList<>(agents.values());
        list.sort(Comparator.comparing(Agent::getName));
        return list;
    }

    public Agent get(String role) {
        return agents.get(role.toUpperCase());
    }

    public AgentConfig configOf(String role) throws IOException {
        return AgentConfig.load(configFile(role).getPath());
    }

    public String readFile(String path) throws IOException {
        Path p = Path.of(path);
        return Files.exists(p) ? Files.readString(p) : "";
    }

    public void saveSystemPrompt(String role, String prompt) throws IOException {
        Files.writeString(Path.of(configOf(role).getSystemPromptFile()), prompt);
        get(role).reload();
    }

    public void saveContextFile(String role, String contextFile, String content) throws IOException {
        if (!configOf(role).getContextFiles().contains(contextFile)) {
            throw new IOException(contextFile + " is not a context file of " + get(role).getName());
        }
        Files.writeString(Path.of(contextFile), toBeliefLines(content));
        get(role).reload();
    }

    public String importContextFile(String role, File source) throws IOException {
        File folder = folders.get(role.toUpperCase());
        File target = new File(folder, source.getName().replaceAll("[^A-Za-z0-9._-]", "_"));
        if (target.exists()) throw new IOException(target.getName() + " already exists for this agent");
        String path = agentsDir.getName() + "/" + folder.getName() + "/" + target.getName();
        Files.writeString(target.toPath(), toBeliefLines(Files.readString(source.toPath())));

        ObjectNode json = readConfig(role);
        json.withObjectProperty("beliefs").withArrayProperty("contextFiles").add(path);
        writeConfig(role, json);
        get(role).reload();
        return path;
    }

    public void updateProfile(String role, String description, List<String> keywords, int maxRetries)
            throws IOException {
        ObjectNode json = readConfig(role);
        json.put("description", description);
        ArrayNode list = json.putArray("keywords");
        for (String k : keywords) list.add(k);
        json.withObjectProperty("desires").put("maxRetries", maxRetries);
        writeConfig(role, json);
        get(role).reload();
    }

    public String readMemory(String role) throws IOException {
        return readFile(configOf(role).getMemoryFile());
    }

    public int contextSize(String role) throws IOException {
        int count = 0;
        for (String file : configOf(role).getContextFiles()) {
            for (String line : readFile(file).split("\\R")) {
                if (line.contains("|")) count++;
            }
        }
        return count;
    }

    public int memorisedThreads(String role) throws IOException {
        Set<String> threads = new HashSet<>();
        for (String line : readMemory(role).split("\\R")) {
            int bar = line.indexOf('|');
            if (bar > 0) threads.add(line.substring(0, bar));
        }
        return threads.size();
    }

    public Agent createAgent(String name, String role, String description, List<String> keywords,
                             int maxRetries, String systemPrompt, String context) throws IOException {
        String key = role.trim().toUpperCase();
        if (name.isBlank() || key.isBlank()) throw new IOException("Name and role are required");
        if (!key.matches("[A-Z0-9_]+")) throw new IOException("The role may only contain letters, digits and _");
        if (agents.containsKey(key)) throw new IOException("An agent with the role " + role + " already exists");

        String slug = key.toLowerCase();
        File folder = new File(agentsDir, slug);
        if (folder.exists()) throw new IOException("The folder " + folder.getPath() + " already exists");
        if (!folder.mkdirs()) throw new IOException("Could not create " + folder.getPath());

        String base = agentsDir.getName() + "/" + slug + "/";
        Files.writeString(Path.of(base + "attitude_" + slug + ".txt"), systemPrompt);
        Files.writeString(Path.of(base + "base_beliefs_" + slug + ".txt"), toBeliefLines(context));

        ObjectNode json = MAPPER.createObjectNode();
        json.put("description", description);
        ArrayNode list = json.putArray("keywords");
        for (String k : keywords) list.add(k);
        json.put("name", name.trim());
        json.put("role", role.trim());
        ObjectNode beliefs = json.putObject("beliefs");
        beliefs.putArray("contextFiles").add(base + "base_beliefs_" + slug + ".txt");
        beliefs.put("memoryFile", base + "learnt_beliefs_" + slug + ".txt");
        json.putObject("attitude").put("systemPromptFile", base + "attitude_" + slug + ".txt");
        json.putObject("desires").put("maxRetries", maxRetries);
        MAPPER.writeValue(new File(folder, "agent.json"), json);

        return register(folder);
    }

    public static String toBeliefLines(String text) {
        String today = LocalDate.now().toString();
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            sb.append(line.contains("|") ? line.trim() : today + " | " + line.trim()).append("\n");
        }
        return sb.toString();
    }

    private Agent register(File folder) throws IOException {
        Agent agent = factory.create(folder);
        agents.put(agent.getRole().toUpperCase(), agent);
        folders.put(agent.getRole().toUpperCase(), folder);
        return agent;
    }

    private File configFile(String role) throws IOException {
        File folder = folders.get(role.toUpperCase());
        if (folder == null) throw new IOException("Unknown agent role: " + role);
        return new File(folder, "agent.json");
    }

    private ObjectNode readConfig(String role) throws IOException {
        return (ObjectNode) MAPPER.readTree(configFile(role));
    }

    private void writeConfig(String role, ObjectNode json) throws IOException {
        MAPPER.writeValue(configFile(role), json);
    }
}
