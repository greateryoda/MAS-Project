import java.io.File;
import java.io.IOException;

// Factory : construit un agent a partir de son dossier dans agents/ (agent.json + fichiers).
public class AgentFactory{
    private final LLM llm;
    private final ContextSelectorFactory selectors;

    public AgentFactory(LLM llm) {
        this(llm, ContextSelectorFactory.defaults());
    }

    public AgentFactory(LLM llm, ContextSelectorFactory selectors) {
        this.llm = llm;
        this.selectors = selectors;
    }

    public Agent create(File agentDir) throws IOException{
        return createFromConfig(new File(agentDir, "agent.json").getPath());
    }

    public Agent createFromConfig(String configPath) throws IOException{
        return new BDIAgent(configPath, llm, selectors);
    }
}
