import java.util.Locale;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Statistiques sur le journal des decisions (logs/decisions.jsonl) produit en usage reel :
// repartition du routage, recours a l'arbitre, acceptation des brouillons par agent,
// calibration du CriticAgent (sa note predit-elle la decision de l'utilisateur ?) et raisons de refus.
public class DecisionLogReport {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<JsonNode> read(Path file) throws IOException {
        List<JsonNode> entries = new ArrayList<>();
        if (!Files.exists(file)) return entries;
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank()) continue;
            try {
                entries.add(MAPPER.readTree(line));
            } catch (IOException e) {
                System.err.println("Skipping an invalid log line: " + e.getMessage());
            }
        }
        return entries;
    }

    public static String markdown(List<JsonNode> entries, String file) {
        StringBuilder md = new StringBuilder("# Decision log report\n\n");
        md.append("- Log: `").append(file).append("` (").append(entries.size()).append(" emails)\n");
        if (entries.isEmpty()) return md.append("\nNo entry yet: process emails in the application first.\n").toString();

        long arbiter = entries.stream().filter(e -> e.path("routing").path("arbiterConsulted").asBoolean()).count();
        long fallback = entries.stream().filter(e -> e.path("routing").path("fallback").asBoolean()).count();
        long errors = entries.stream().filter(e -> "ERROR".equals(e.path("outcome").asText())).count();
        md.append("- LLM arbiter consulted: ").append(pct(arbiter, entries.size())).append("\n");
        md.append("- Fallback to the generic agent: ").append(pct(fallback, entries.size())).append("\n");
        md.append("- Errors: ").append(errors).append("\n");
        md.append(String.format(Locale.ROOT, "- Mean routing time: %.0f ms, mean total time (including the user): %.0f ms%n",
                mean(entries, e -> e.path("routing").path("millis").asDouble(Double.NaN)),
                mean(entries, e -> e.path("totalMillis").asDouble(Double.NaN))));
        md.append(String.format(Locale.ROOT, "- Mean context sent: %.0f chars (%.1f beliefs selected out of %.1f)%n",
                mean(entries, e -> e.path("context").path("chars").asDouble(Double.NaN)),
                mean(entries, e -> e.path("context").path("beliefsSelected").asDouble(Double.NaN)),
                mean(entries, e -> e.path("context").path("beliefsAvailable").asDouble(Double.NaN))));

        md.append("\n## Per agent\n\n| Agent | Emails | Accepted 1st draft | Accepted / edited | Refused / no reply | Mean drafts | Mean critic score (1st draft) |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        Map<String, List<JsonNode>> byAgent = new TreeMap<>();
        for (JsonNode e : entries) byAgent.computeIfAbsent(e.path("routing").path("agent").asText("-"), k -> new ArrayList<>()).add(e);
        for (Map.Entry<String, List<JsonNode>> g : byAgent.entrySet()) {
            List<JsonNode> list = g.getValue();
            long first = list.stream().filter(e -> "ACCEPT".equals(e.path("attempts").path(0).path("feedback").asText())).count();
            long ok = list.stream().filter(e -> List.of("ACCEPTED", "EDITED").contains(e.path("outcome").asText())).count();
            long ko = list.stream().filter(e -> List.of("DISCARDED", "NO_REPLY").contains(e.path("outcome").asText())).count();
            md.append(String.format(Locale.ROOT, "| %s | %d | %s | %s | %s | %.1f | %.1f |%n", g.getKey(), list.size(),
                    pct(first, list.size()), pct(ok, list.size()), pct(ko, list.size()),
                    mean(list, e -> e.path("draftCount").asDouble(Double.NaN)),
                    mean(list, e -> e.path("attempts").path(0).path("critique").path("score").asDouble(Double.NaN))));
        }

        // calibration : note moyenne du critique selon la decision de l'utilisateur sur le meme brouillon
        Map<String, List<Double>> byFeedback = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();
        for (JsonNode e : entries) {
            for (JsonNode d : e.path("attempts")) {
                JsonNode score = d.path("critique").path("score");
                String feedback = d.path("feedback").asText("");
                if (score.isNumber() && !feedback.isEmpty()) {
                    byFeedback.computeIfAbsent(feedback, k -> new ArrayList<>()).add(score.asDouble());
                }
                if (!d.path("reason").asText("").isBlank()) {
                    reasons.add(e.path("routing").path("agent").asText("-") + ": " + d.path("reason").asText());
                }
            }
        }
        md.append("\n## CriticAgent calibration\n\nMean critic score of the drafts, by the decision the user then took. "
                + "A useful critic gives higher scores to accepted drafts.\n\n| User decision | Drafts | Mean critic score |\n|---|---|---|\n");
        byFeedback.forEach((k, v) -> md.append(String.format(Locale.ROOT, "| %s | %d | %.2f |%n", k, v.size(),
                v.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN))));
        if (byFeedback.isEmpty()) md.append("| - | 0 | - |\n");

        md.append("\n## Rejection reasons given by the user\n\n");
        if (reasons.isEmpty()) md.append("None.\n");
        reasons.forEach(r -> md.append("- ").append(r).append("\n"));
        return md.toString();
    }

    private interface Field { double of(JsonNode e); }

    private static double mean(List<JsonNode> entries, Field f) {
        return entries.stream().mapToDouble(f::of).filter(v -> !Double.isNaN(v)).average().orElse(Double.NaN);
    }

    private static String pct(long n, int total) {
        return total == 0 ? "-" : String.format(Locale.ROOT, "%.0f%% (%d)", 100.0 * n / total, n);
    }
}
