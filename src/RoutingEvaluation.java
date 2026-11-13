import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

// Experience 1 : qualite du routage sur un jeu d'emails etiquetes, pour plusieurs configurations
// de la chaine de routage. Seul le routage est execute (RoutingAgent.classify), pas la redaction.
public class RoutingEvaluation {

    // configurations comparees : chacune retire ou isole une partie de la strategie
    public static final Map<String, String> CONFIGS = new LinkedHashMap<>();
    static {
        CONFIGS.put("full", "all signals (thread, contact, keywords, domain) + LLM arbiter when in doubt");
        CONFIGS.put("rules-only", "all signals, no LLM: doubtful emails go to the GenericAgent");
        CONFIGS.put("llm-only", "no signal: the LLM classifies every email among all agents");
        CONFIGS.put("no-keywords", "sender signals only (thread, contact, domain) + LLM arbiter");
        CONFIGS.put("content-only", "keywords only (sender ignored) + LLM arbiter");
        CONFIGS.put("no-collaboration", "like full, but the arbiter may not ask several agents to collaborate");
    }

    // collaborators : agents contributeurs choisis en plus de l'agent principal (vide le plus souvent)
    public record Result(LabelledEmail email, String predicted, boolean arbiter, boolean fallback,
                         long millis, String explanation, List<String> collaborators) {
        public boolean strict()  { return email.expected().equals(predicted); }
        public boolean lenient() { return email.accepts(predicted); }
        public boolean collaborative() { return !collaborators.isEmpty(); }
        // collaboration pertinente : chaque contributeur fait partie des agents acceptables de l'email
        public boolean relevantCollaboration() {
            return collaborative() && collaborators.stream().allMatch(email::accepts);
        }
    }

    public record ConfigResult(String config, List<Result> results, MeteredLLM.Snapshot llm) {
        public double strictAccuracy()  { return rate(results.stream().filter(Result::strict).count()); }
        public double lenientAccuracy() { return rate(results.stream().filter(Result::lenient).count()); }
        public double arbiterRate()     { return rate(results.stream().filter(Result::arbiter).count()); }
        public double fallbackRate()    { return rate(results.stream().filter(Result::fallback).count()); }
        public double meanMillis()      { return results.stream().mapToLong(Result::millis).average().orElse(0); }
        public double collaborationRate() { return rate(results.stream().filter(Result::collaborative).count()); }
        // part des collaborations dont tous les contributeurs sont pertinents (NaN : aucune collaboration)
        public double collaborationPrecision() {
            long n = results.stream().filter(Result::collaborative).count();
            return n == 0 ? Double.NaN : results.stream().filter(Result::relevantCollaboration).count() / (double) n;
        }
        // emails ambigus (plusieurs agents acceptables) pour lesquels plusieurs agents ont collabore
        public double ambiguousCoverage() {
            List<Result> ambiguous = results.stream().filter(r -> r.email().acceptable().size() > 1).toList();
            return ambiguous.isEmpty() ? Double.NaN
                    : ambiguous.stream().filter(Result::relevantCollaboration).count() / (double) ambiguous.size();
        }

        public double accuracy(String category) {
            List<Result> subset = results.stream().filter(r -> r.email().category().equals(category)).toList();
            return subset.isEmpty() ? Double.NaN
                    : subset.stream().filter(Result::lenient).count() / (double) subset.size();
        }

        // matrice de confusion : attendu -> (predit -> nombre)
        public Map<String, Map<String, Integer>> confusion() {
            Map<String, Map<String, Integer>> m = new LinkedHashMap<>();
            for (Result r : results) {
                m.computeIfAbsent(r.email().expected(), k -> new LinkedHashMap<>()).merge(r.predicted(), 1, Integer::sum);
            }
            return m;
        }

        // precision / rappel / F1 par agent (exactitude stricte)
        public double[] prf(String role) {
            long tp = results.stream().filter(r -> r.predicted().equals(role) && r.email().expected().equals(role)).count();
            long fp = results.stream().filter(r -> r.predicted().equals(role) && !r.email().expected().equals(role)).count();
            long fn = results.stream().filter(r -> !r.predicted().equals(role) && r.email().expected().equals(role)).count();
            double p = tp + fp == 0 ? 0 : tp / (double) (tp + fp);
            double rc = tp + fn == 0 ? 0 : tp / (double) (tp + fn);
            double f = p + rc == 0 ? 0 : 2 * p * rc / (p + rc);
            return new double[] {p, rc, f};
        }

        private double rate(long n) {
            return results.isEmpty() ? 0 : n / (double) results.size();
        }
    }

    private final AgentRegistry registry;
    private final TemporaryContactBook contacts;
    private final LLM llm;

    public RoutingEvaluation(AgentRegistry registry, TemporaryContactBook contacts, LLM llm) {
        this.registry = registry;
        this.contacts = contacts;
        this.llm = llm;
    }

    public RoutingAgent router(String config, LLM arbiterLlm) {
        Map<String, Agent> agents = registry.getAgents();
        Arbiter llmArbiter = new LlmClassifierStep(arbiterLlm, agents);
        Arbiter noArbiter = (email, candidates) -> null;
        List<RoutingStep> steps = switch (config) {
            case "full", "rules-only", "no-collaboration" -> EmailAssistant.defaultSteps(registry, contacts);
            case "llm-only" -> List.of();
            case "no-keywords" -> List.of(new ThreadStep(agents), new ContactStep(contacts), new DomainStep(contacts));
            case "content-only" -> List.of(new KeywordStep(agents));
            default -> throw new IllegalArgumentException("Unknown routing configuration: " + config
                                                          + " (expected one of " + CONFIGS.keySet() + ")");
        };
        RoutingAgent router = new RoutingAgent(agents, contacts, steps,
                                               config.equals("rules-only") ? noArbiter : llmArbiter, null);
        if (config.equals("no-collaboration")) router.setMaxCollaborators(0);
        return router;
    }

    public ConfigResult run(String config, List<LabelledEmail> dataset) {
        MeteredLLM meter = new MeteredLLM(llm);
        RoutingAgent router = router(config, meter);
        List<Result> results = new ArrayList<>();
        for (LabelledEmail item : dataset) {
            try {
                RoutingOutcome o = router.classify(item.toEmail());
                List<String> helpers = o.decision().getCollaborators().stream()
                        .map(RoutingDecision.Collaborator::role).toList();
                results.add(new Result(item, o.decision().getRole(), o.arbiterConsulted(), o.fallback(),
                                       o.millis(), o.explanation(), helpers));
            } catch (RuntimeException e) {
                results.add(new Result(item, "ERROR", false, false, 0, e.getMessage(), List.of()));
            }
        }
        return new ConfigResult(config, results, meter.snapshot());
    }

    // ---------- rapport ----------

    public static String markdown(List<ConfigResult> runs, String model, String dataset) {
        StringBuilder md = new StringBuilder();
        md.append("# Routing evaluation\n\n");
        md.append("- Model (arbiter): `").append(model).append("`\n");
        md.append("- Dataset: `").append(dataset).append("` (")
          .append(runs.isEmpty() ? 0 : runs.get(0).results().size()).append(" emails)\n");
        md.append("- Strict = the expected agent; lenient = any agent listed as acceptable for an ambiguous email\n\n");

        md.append("## Summary\n\n");
        md.append("| Configuration | Strict acc. | Lenient acc. | Arbiter calls | Fallback | Collaboration | LLM calls | Mean ms/email |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        for (ConfigResult r : runs) {
            md.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %s | %s | %d | %.0f |\n", r.config(),
                    pct(r.strictAccuracy()), pct(r.lenientAccuracy()), pct(r.arbiterRate()),
                    pct(r.fallbackRate()), pct(r.collaborationRate()), r.llm().calls(), r.meanMillis()));
        }
        md.append("\n");
        for (ConfigResult r : runs) md.append("- **").append(r.config()).append("**: ")
                                     .append(CONFIGS.getOrDefault(r.config(), "")).append("\n");

        // collaboration entre agents (piste 6) : quand plusieurs agents repondent ensemble
        md.append("\n## Collaboration between agents\n\n");
        md.append("Relevant = every contributing agent is listed as acceptable for the email. "
                + "Ambiguous coverage = share of the emails with several acceptable agents answered by a relevant collaboration.\n\n");
        md.append("| Configuration | Collaborations | Relevant | Ambiguous coverage |\n|---|---|---|---|\n");
        for (ConfigResult r : runs) {
            long n = r.results().stream().filter(Result::collaborative).count();
            md.append(String.format(Locale.ROOT, "| %s | %d | %s | %s |\n", r.config(), n,
                    pct(r.collaborationPrecision()), pct(r.ambiguousCoverage())));
        }
        for (ConfigResult r : runs) {
            List<Result> collab = r.results().stream().filter(Result::collaborative).toList();
            if (collab.isEmpty()) continue;
            md.append("\n").append(r.config()).append(":\n\n");
            for (Result x : collab) {
                md.append("- `").append(x.email().id()).append("` ").append(x.email().subject()).append(": **")
                  .append(x.predicted()).append("** + ").append(String.join(", ", x.collaborators()))
                  .append(x.relevantCollaboration() ? "" : " (not relevant)").append("\n");
            }
        }

        TreeSet<String> categories = new TreeSet<>();
        runs.forEach(r -> r.results().forEach(x -> categories.add(x.email().category())));
        md.append("\n## Lenient accuracy by email category\n\n| Configuration |");
        categories.forEach(c -> md.append(" ").append(c).append(" |"));
        md.append("\n|---|");
        categories.forEach(c -> md.append("---|"));
        md.append("\n");
        for (ConfigResult r : runs) {
            md.append("| ").append(r.config()).append(" |");
            categories.forEach(c -> md.append(" ").append(pct(r.accuracy(c))).append(" |"));
            md.append("\n");
        }

        for (ConfigResult r : runs) {
            md.append("\n## ").append(r.config()).append("\n\n");
            TreeSet<String> roles = new TreeSet<>();
            r.results().forEach(x -> { roles.add(x.email().expected()); roles.add(x.predicted()); });
            md.append("Confusion matrix (rows: expected, columns: predicted)\n\n| expected \\ predicted |");
            roles.forEach(p -> md.append(" ").append(p).append(" |"));
            md.append("\n|---|");
            roles.forEach(p -> md.append("---|"));
            md.append("\n");
            Map<String, Map<String, Integer>> confusion = r.confusion();
            for (String e : roles) {
                if (!confusion.containsKey(e)) continue;
                md.append("| **").append(e).append("** |");
                for (String p : roles) {
                    int n = confusion.get(e).getOrDefault(p, 0);
                    md.append(" ").append(n == 0 ? "." : String.valueOf(n)).append(" |");
                }
                md.append("\n");
            }
            md.append("\n| Agent | Precision | Recall | F1 |\n|---|---|---|---|\n");
            for (String role : roles) {
                if (!confusion.containsKey(role)) continue;
                double[] prf = r.prf(role);
                md.append(String.format(Locale.ROOT, "| %s | %s | %s | %.2f |\n", role, pct(prf[0]), pct(prf[1]), prf[2]));
            }
            List<Result> errors = r.results().stream().filter(x -> !x.lenient()).toList();
            md.append("\nErrors (").append(errors.size()).append(")\n\n");
            for (Result x : errors) {
                md.append("- `").append(x.email().id()).append("` [").append(x.email().category()).append("] ")
                  .append(x.email().subject()).append(" — expected **").append(x.email().expected())
                  .append("**, got **").append(x.predicted()).append("**: ").append(x.explanation()).append("\n");
            }
        }
        return md.toString();
    }

    public static String csv(List<ConfigResult> runs) {
        StringBuilder csv = new StringBuilder("config,id,category,expected,predicted,strict,lenient,arbiter,fallback,millis,collaborators\n");
        for (ConfigResult r : runs) {
            for (Result x : r.results()) {
                csv.append(String.join(",", r.config(), x.email().id(), x.email().category(), x.email().expected(),
                        x.predicted(), String.valueOf(x.strict()), String.valueOf(x.lenient()),
                        String.valueOf(x.arbiter()), String.valueOf(x.fallback()), String.valueOf(x.millis()),
                        String.join("+", x.collaborators())))
                   .append("\n");
            }
        }
        return csv.toString();
    }

    static String pct(double v) {
        return Double.isNaN(v) ? "-" : String.format(Locale.ROOT, "%.0f%%", 100 * v);
    }
}
