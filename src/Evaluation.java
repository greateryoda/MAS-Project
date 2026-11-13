import java.util.Locale;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Point d'entree des experiences (a lancer depuis la racine du projet, LM Studio demarre) :
//
//   java -cp "bin;lib/*" Evaluation routing [--configs full,rules-only,...] [--dataset eval/dataset.json]
//   java -cp "bin;lib/*" Evaluation replies [--configs multi-agent,single-agent] [--limit 10] [--judge-model NAME]
//   java -cp "bin;lib/*" Evaluation log     [--file logs/decisions.jsonl]
//
// Les rapports (Markdown + CSV/JSON) sont ecrits dans eval/results/.
public class Evaluation {
    private static final String RESULTS = "eval/results";

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !List.of("routing", "replies", "log").contains(args[0])) {
            usage();
            return;
        }
        Map<String, String> opts = options(Arrays.copyOfRange(args, 1, args.length));
        AppConfig config = AppConfig.load(opts.getOrDefault("config", "config.json"));
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        switch (args[0]) {
            case "routing" -> routing(config, opts, stamp);
            case "replies" -> replies(config, opts, stamp);
            default -> log(config, opts, stamp);
        }
    }

    private static void routing(AppConfig config, Map<String, String> opts, String stamp) throws IOException {
        String dataset = opts.getOrDefault("dataset", "eval/dataset.json");
        List<LabelledEmail> emails = limit(LabelledEmail.load(dataset), opts);
        List<String> configs = list(opts.get("configs"), RoutingEvaluation.CONFIGS.keySet());

        LLM llm = config.llm();
        AgentRegistry registry = new AgentRegistry(new File("agents"), new AgentFactory(llm, config.contextSelectors()));
        TemporaryContactBook contacts = new TemporaryContactBook("agents/contacts.json");
        RoutingEvaluation evaluation = new RoutingEvaluation(registry, contacts, llm);

        List<RoutingEvaluation.ConfigResult> runs = new ArrayList<>();
        for (String c : configs) {
            System.out.println("Routing configuration " + c + " on " + emails.size() + " emails...");
            RoutingEvaluation.ConfigResult r = evaluation.run(c, emails);
            System.out.printf(Locale.ROOT, "  strict %s, lenient %s, arbiter %s, %d LLM call(s)%n",
                    RoutingEvaluation.pct(r.strictAccuracy()), RoutingEvaluation.pct(r.lenientAccuracy()),
                    RoutingEvaluation.pct(r.arbiterRate()), r.llm().calls());
            runs.add(r);
        }
        write("routing-" + stamp + ".md", RoutingEvaluation.markdown(runs, config.model(), dataset));
        write("routing-" + stamp + ".csv", RoutingEvaluation.csv(runs));
    }

    private static void replies(AppConfig config, Map<String, String> opts, String stamp) throws IOException {
        String dataset = opts.getOrDefault("dataset", "eval/dataset.json");
        List<LabelledEmail> emails = limit(LabelledEmail.load(dataset).stream().filter(LabelledEmail::reply).toList(), opts);
        List<String> configs = list(opts.get("configs"), ReplyEvaluation.CONFIGS.keySet());

        LLM llm = config.llm();
        String judgeModel = opts.getOrDefault("judge-model", config.model());
        LLM judgeLlm = judgeModel.equals(config.model()) ? llm : new LmStudioClient(config.url(), judgeModel);
        CriticAgent judge = new CriticAgent(judgeLlm, config.criticPromptFile(), true);
        AgentRegistry reference = new AgentRegistry(new File("agents"), new AgentFactory(llm, config.contextSelectors()));
        TemporaryContactBook contacts = new TemporaryContactBook("agents/contacts.json");
        ReplyEvaluation evaluation = new ReplyEvaluation(llm, config.contextSelectors(), judge, reference, contacts);

        List<ReplyEvaluation.ConfigResult> runs = new ArrayList<>();
        for (String c : configs) {
            System.out.println("Reply configuration " + c + " on " + emails.size() + " emails (draft + judge each)");
            ReplyEvaluation.ConfigResult r = evaluation.run(c, emails);
            System.out.printf(Locale.ROOT, "  mean score %.2f/10, approved %s, mean prompt %.0f chars%n",
                    r.meanScore(), RoutingEvaluation.pct(r.approvalRate()), r.meanPrompt());
            runs.add(r);
        }
        write("replies-" + stamp + ".md", ReplyEvaluation.markdown(runs, config.model(), judgeModel, dataset));
        write("replies-" + stamp + ".json", ReplyEvaluation.json(runs));
    }

    private static void log(AppConfig config, Map<String, String> opts, String stamp) throws IOException {
        String file = opts.getOrDefault("file", config.logFile());
        String report = DecisionLogReport.markdown(DecisionLogReport.read(Path.of(file)), file);
        System.out.println(report);
        write("log-" + stamp + ".md", report);
    }

    private static void write(String name, String content) throws IOException {
        Path path = Path.of(RESULTS, name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        System.out.println("Written: " + path);
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("Unexpected argument: " + args[i]);
            String key = args[i].substring(2);
            if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for --" + key);
            opts.put(key, args[++i]);
        }
        return opts;
    }

    private static List<String> list(String value, java.util.Collection<String> all) {
        return value == null ? List.copyOf(all) : Arrays.stream(value.split(",")).map(String::trim).toList();
    }

    private static List<LabelledEmail> limit(List<LabelledEmail> emails, Map<String, String> opts) {
        if (!opts.containsKey("limit")) return emails;
        return emails.subList(0, Math.min(emails.size(), Integer.parseInt(opts.get("limit"))));
    }

    private static void usage() {
        System.out.println("""
                Usage (from the project root, with the local LLM server started):
                  Evaluation routing [--configs %s] [--dataset eval/dataset.json] [--limit N]
                  Evaluation replies [--configs %s] [--limit N] [--judge-model MODEL]
                  Evaluation log     [--file logs/decisions.jsonl]
                Reports are written to eval/results/.""".formatted(
                String.join(",", RoutingEvaluation.CONFIGS.keySet()),
                String.join(",", ReplyEvaluation.CONFIGS.keySet())));
    }
}
