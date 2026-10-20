import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

public class AgentFactory{
    private final LLM llm;

    public AgentFactory(LLM llm) {
        this.llm = llm;
    }

    public Agent create(File agentDir) throws IOException{
        String configPath = new File(agentDir, "agent.json").getPath();
        return new BDIAgent(configPath, llm);
    }


    public Map<String, Agent> createAllAgents() throws IOException{
        File dir = new File("agents/");
        Map<String, Agent> agents = new HashMap<>();

        File[] files = dir.listFiles();

        if (files == null) throw new IOException("Agents folder not found: " + dir.getAbsolutePath());

        for (File file : files){
            if (file.isDirectory()){
                Agent agent = create(file);
                agents.put(agent.getRole().toUpperCase(), agent);
            }
        }

        return agents;
    }
}