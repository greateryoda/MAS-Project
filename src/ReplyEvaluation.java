import java.util.Locale;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

// Experience 2 : qualite des reponses. Teste l'hypothese du projet :
// des agents specialises (prompt + contexte propres) font-ils mieux qu'un LLM unique ?
// Chaque brouillon est note par un juge (le prompt du CriticAgent) avec les regles et
// TOUT le contexte de l'agent attendu, quelle que soit la configuration qui l'a ecrit.
public class ReplyEvaluation {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    public static final String SINGLE_AGENT_PROMPT = "eval/single_agent_prompt.txt";

    public static final Map<String, String> CONFIGS = new LinkedHashMap<>();
    static {
        CONFIGS.put("multi-agent", "routing + specialised agent, context selected with the configured strategy");
        CONFIGS.put("multi-agent-all-context", "routing + specialised agent, all of its context sent (no selection)");
        CONFIGS.put("oracle-routing", "the expected agent answers directly (isolates drafting from routing errors)");
        CONFIGS.put("single-agent", "baseline: one generic prompt, the context of every agent, no routing");
        CONFIGS.put("multi-agent-no-collab", "like multi-agent, but a single agent answers even when the email concerns several");
    }

    // contributors : agents contributeurs (collaboration), vide si un seul agent a repondu
    public record Result(LabelledEmail email, String config, String agentRole, String draft,
                         int contextChars, long promptChars, long millis, Critique judge, List<String> contributors) {
        public boolean routedCorrectly() { return agentRole == null || email.accepts(agentRole); }
    }

    public record ConfigResult(String config, List<Result> results) {
        List<Result> judged() { return results.stream().filter(r -> r.judge() != null && r.judge().isAvailable()).toList(); }
        public double meanScore()     { return judged().stream().mapToInt(r -> r.judge().getScore()).average().orElse(Double.NaN); }
        public double approvalRate()  { return judged().isEmpty() ? Double.NaN : judged().stream().filter(r -> r.judge().isApproved()).count() / (double) judged().size(); }
        public double meanPrompt()    { return results.stream().mapToLong(Result::promptChars).average().orElse(0); }
        public double meanContext()   { return results.stream().mapToInt(Result::contextChars).average().orElse(0); }
        public double meanMillis()    { return results.stream().mapToLong(Result::millis).average().orElse(0); }
        public long errors()          { return results.stream().filter(r -> r.draft() == null).count(); }
        public long unjudged()        { return results.size() - judged().size(); }
        public long collaborations()  { return results.stream().filter(r -> !r.contributors().isEmpty()).count(); }
    }

    private final LLM llm;
    private final ContextSelectorFactory selectors;
    private final CriticAgent judge;
    private final AgentRegistry reference; // regles et contexte complet de l'agent attendu, pour le juge
    private final TemporaryContactBook contacts;

    public ReplyEvaluation(LLM llm, ContextSelectorFactory selectors, CriticAgent judge,
                           AgentRegistry reference, TemporaryContactBook contacts) {
        this.llm = llm;
        this.selectors = selectors;
        this.judge = judge;
        this.reference = reference;
        this.contacts = contacts;
    }

    public ConfigResult run(String config, List<LabelledEmail> dataset) throws IOException {
        MeteredLLM drafting = new MeteredLLM(llm);
        AgentRegistry registry = null;
        RoutingAgent router = null;
        Agent single = null;
        switch (config) {
            case "multi-agent", "oracle-routing", "multi-agent-no-collab" ->
                    registry = new AgentRegistry(new File("agents"), new AgentFactory(drafting, selectors));
            case "multi-agent-all-context" ->
                    registry = new AgentRegistry(new File("agents"),
                            new AgentFactory(drafting, selectors.forcing(ContextSelectorFactory.ALL)));
            case "single-agent" -> single = singleAgent(drafting);
            default -> throw new IllegalArgumentException("Unknown reply configuration: " + config
                                                          + " (expected one of " + CONFIGS.keySet() + ")");
        }
        if (config.startsWith("multi-agent")) {
            // l'arbitre utilise le LLM non mesure : on ne compte que le cout de la redaction
            router = new RoutingAgent(registry.getAgents(), contacts, EmailAssistant.defaultSteps(registry, contacts),
                                      new LlmClassifierStep(llm, registry.getAgents()), null);
            if (config.equals("multi-agent-no-collab")) router.setMaxCollaborators(0);
        }

        List<Result> results = new ArrayList<>();
        for (LabelledEmail item : dataset) {
            Email email = item.toEmail();
            String role = null;
            Agent agent;
            RoutingDecision decision = null;
            try {
                if (single != null) {
                    agent = single;
                } else if (router != null) {
                    decision = router.classify(email).decision();
                    role = decision.getRole();
                    agent = registry.get(role);
                } else {
                    role = item.expected();
                    agent = registry.get(role);
                }
                int contextChars = agent.selectContext(email).chars();
                MeteredLLM.Snapshot before = drafting.snapshot();
                Intention intention = agent.adopt(email);
                // collaboration : les contributions sont comptees dans le cout de la redaction
                List<Contribution> contributions = decision == null ? List.of()
                        : router.collaborate(email, agent, decision);
                intention.setContributions(contributions);
                String draft = agent.draft(intention);
                MeteredLLM.Snapshot cost = drafting.snapshot().minus(before);
                List<String> contributors = contributions.stream().filter(Contribution::available)
                        .map(Contribution::role).toList();
                results.add(new Result(item, config, role, draft, contextChars, cost.promptChars(), cost.millis(),
                                       judge(email, item, draft), contributors));
            } catch (RuntimeException e) {
                System.err.println("  " + item.id() + ": " + e.getMessage());
                results.add(new Result(item, config, role, null, 0, 0, 0, Critique.unavailable(e.getMessage()), List.of()));
            }
            System.out.print(".");
        }
        System.out.println();
        return new ConfigResult(config, results);
    }

    // juge : regles de l'agent attendu ; faits connus = contexte complet de l'agent attendu et, pour un
    // email ambigu, des autres agents acceptables (sinon une information correcte apportee par un
    // contributeur serait comptee comme inventee, et l'oubli d'une partie de l'email ne serait pas vu)
    private Critique judge(Email email, LabelledEmail item, String draft) {
        Agent expected = reference.get(item.expected());
        if (expected == null) return Critique.unavailable("unknown expected agent " + item.expected());
        List<Belief> knowledge = new ArrayList<>(expected.recall(Belief.NO_THREAD));
        for (String role : item.acceptable()) {
            Agent other = reference.get(role);
            if (other != null && other != expected) knowledge.addAll(other.recall(Belief.NO_THREAD));
        }
        return judge.review(email, expected.getName(), expected.getSystemPrompt(), knowledge, draft);
    }

    // agent unique de reference : construit avec le meme framework (BDIAgent), mais avec un prompt
    // generique et le contexte de TOUS les agents. Le fichier de config est genere pour suivre
    // automatiquement les agents ajoutes depuis l'interface.
    private Agent singleAgent(LLM drafting) throws IOException {
        Set<String> files = new LinkedHashSet<>();
        for (Agent a : reference.list()) files.addAll(reference.configOf(a.getRole()).getContextFiles());
        ObjectNode json = MAPPER.createObjectNode();
        json.put("name", "SingleAgent");
        json.put("role", "Single");
        json.put("description", "Baseline: answers every email alone");
        json.putArray("keywords");
        ObjectNode beliefs = json.putObject("beliefs");
        ArrayNode list = beliefs.putArray("contextFiles");
        files.forEach(list::add);
        beliefs.put("memoryFile", "build/eval/learnt_beliefs_single.txt");
        json.putObject("attitude").put("systemPromptFile", SINGLE_AGENT_PROMPT);
        json.putObject("desires").put("maxRetries", 0);
        json.putObject("context").put("strategy", ContextSelectorFactory.ALL);
        Path config = Path.of("build/eval/single_agent.json");
        Files.createDirectories(config.getParent());
        MAPPER.writeValue(config.toFile(), json);
        return new AgentFactory(drafting, selectors.forcing(ContextSelectorFactory.ALL))
                .createFromConfig(config.toString());
    }

    // ---------- rapport ----------

    public static String markdown(List<ConfigResult> runs, String model, String judgeModel, String dataset) {
        StringBuilder md = new StringBuilder("# Reply evaluation\n\n");
        md.append("- Drafting model: `").append(model).append("`\n");
        md.append("- Judge model: `").append(judgeModel).append("` (CriticAgent prompt, rules and full context of the expected agent)\n");
        md.append("- Dataset: `").append(dataset).append("` (")
          .append(runs.isEmpty() ? 0 : runs.get(0).results().size()).append(" emails marked \"reply\")\n\n");
        md.append("| Configuration | Mean score /10 | Approved (>=7) | Context chars | Prompt chars | Mean ms/draft | Errors | Not judged |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        for (ConfigResult r : runs) {
            md.append(String.format(Locale.ROOT, "| %s | %.2f | %s | %.0f | %.0f | %.0f | %d | %d |\n", r.config(), r.meanScore(),
                    RoutingEvaluation.pct(r.approvalRate()), r.meanContext(), r.meanPrompt(), r.meanMillis(),
                    r.errors(), r.unjudged()));
        }
        md.append("\n");
        for (ConfigResult r : runs) md.append("- **").append(r.config()).append("**: ")
                                     .append(CONFIGS.getOrDefault(r.config(), "")).append("\n");
        md.append("\nCaveat: the judge is itself a local LLM. Use a different (ideally larger) judge model with "
                + "`--judge-model`, and read a sample of the drafts (replies-*.json) before drawing conclusions.\n");

        md.append("\n## Score per email\n\n| Email | Category |");
        runs.forEach(r -> md.append(" ").append(r.config()).append(" |"));
        md.append("\n|---|---|");
        runs.forEach(r -> md.append("---|"));
        md.append("\n");
        if (!runs.isEmpty()) {
            for (int i = 0; i < runs.get(0).results().size(); i++) {
                LabelledEmail e = runs.get(0).results().get(i).email();
                md.append("| `").append(e.id()).append("` ").append(e.subject()).append(" | ").append(e.category()).append(" |");
                for (ConfigResult r : runs) {
                    Result x = r.results().get(i);
                    String cell = x.judge() != null && x.judge().isAvailable() ? String.valueOf(x.judge().getScore()) : "-";
                    if (!x.routedCorrectly()) cell += " (" + x.agentRole() + ")";
                    if (!x.contributors().isEmpty()) cell += " +" + String.join("+", x.contributors());
                    md.append(" ").append(cell).append(" |");
                }
                md.append("\n");
            }
        }
        md.append("\nA role in parentheses means the email was routed to a wrong agent; "
                + "\"+ROLE\" means that agent contributed to the reply (collaboration).\n");
        return md.toString();
    }

    // brouillons complets et remarques du juge, pour l'analyse qualitative
    public static String json(List<ConfigResult> runs) throws IOException {
        ArrayNode all = MAPPER.createArrayNode();
        for (ConfigResult r : runs) {
            for (Result x : r.results()) {
                ObjectNode n = all.addObject();
                n.put("config", r.config());
                n.put("id", x.email().id());
                n.put("expected", x.email().expected());
                n.put("routedTo", x.agentRole());
                ArrayNode contributors = n.putArray("contributors");
                x.contributors().forEach(contributors::add);
                n.put("subject", x.email().subject());
                n.put("draft", x.draft());
                n.put("contextChars", x.contextChars());
                n.put("promptChars", x.promptChars());
                n.put("millis", x.millis());
                if (x.judge() != null && x.judge().isAvailable()) {
                    n.put("score", x.judge().getScore());
                    n.put("approved", x.judge().isApproved());
                    ArrayNode issues = n.putArray("issues");
                    x.judge().getIssues().forEach(issues::add);
                } else if (x.judge() != null) {
                    n.put("judgeError", x.judge().getError());
                }
            }
        }
        return MAPPER.writeValueAsString(all);
    }
}
