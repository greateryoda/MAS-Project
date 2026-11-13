import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

// Journal persistant des decisions des agents (Observer du RoutingAgent).
// Une ligne JSON par email traite (format JSONL) dans logs/decisions.jsonl :
// signaux et votes du routage, explication, contexte retenu, brouillons, notes du critique,
// decisions de l'utilisateur, issue et durees. Sert a l'explicabilite et aux statistiques (Evaluation log).
//
// Confidentialite : par defaut, ni le corps de l'email, ni les brouillons, ni le contenu
// des croyances ne sont ecrits (seulement leur taille). "includeText": true les ajoute pour le debogage.
public class DecisionLogger implements WorkflowListener {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int EXCERPT = 160;

    private final Path file;
    private final String model;
    private final boolean includeText;

    private ObjectNode current; // email en cours de traitement
    private long startNanos;

    public DecisionLogger(String file, String model, boolean includeText) {
        this.file = Path.of(file);
        this.model = model;
        this.includeText = includeText;
    }

    public Path getFile() { return file; }

    public synchronized void stageChanged(WorkflowStage stage, StageStatus status, String detail) {
        if (stage == WorkflowStage.RECEIVED && status == StageStatus.COMPLETED) {
            current = MAPPER.createObjectNode();
            startNanos = System.nanoTime();
            current.put("timestamp", OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
            current.put("model", model);
        }
    }

    public synchronized void agentSelected(Agent agent, RoutingDecision decision, Map<String, Double> scores,
                                           List<String> signals, boolean arbiterConsulted) {
        if (current == null) return;
        ObjectNode routing = current.putObject("routing");
        routing.put("agent", agent.getName());
        routing.put("role", decision.getRole());
        routing.put("confidence", round(decision.getConfidence()));
        routing.put("arbiterConsulted", arbiterConsulted);
        routing.put("fallback", decision.getConfidence() == 0.0 && RoutingAgent.FALLBACK_ROLE.equals(decision.getRole()));
        ObjectNode votes = routing.putObject("scores");
        new TreeMap<>(scores).forEach((role, score) -> votes.put(role, round(score)));
        ArrayNode list = routing.putArray("signals");
        signals.forEach(list::add);
        ArrayNode collaborators = routing.putArray("collaborators");
        for (RoutingDecision.Collaborator c : decision.getCollaborators()) {
            collaborators.addObject().put("role", c.role()).put("part", c.part());
        }
        routing.put("explanation", new RoutingOutcome(decision, scores, signals, arbiterConsulted, 0).explanation());
        routing.put("millis", elapsed());
    }

    public synchronized void contextSelected(Agent agent, ContextSelection context) {
        if (current == null) return;
        ObjectNode node = current.putObject("context");
        node.put("strategy", context.strategy());
        node.put("beliefsSelected", context.base().size());
        node.put("beliefsAvailable", context.baseAvailable());
        node.put("threadMessagesSent", context.thread().size());
        node.put("threadMessagesAvailable", context.threadAvailable());
        node.put("chars", context.chars());
        if (includeText) {
            ArrayNode beliefs = node.putArray("beliefs");
            for (Belief b : context.base()) beliefs.add(excerpt(b.getContent()));
        }
    }

    public synchronized void contributionProduced(Agent contributor, Contribution contribution) {
        if (current == null) return;
        ArrayNode list = current.has("contributions") ? (ArrayNode) current.get("contributions")
                                                      : current.putArray("contributions");
        ObjectNode node = list.addObject();
        node.put("agent", contribution.agentName());
        node.put("role", contribution.role());
        node.put("part", contribution.part());
        node.put("available", contribution.available());
        node.put("millis", contribution.millis());
        node.put("chars", contribution.text() == null ? 0 : contribution.text().length());
        if (contribution.error() != null) node.put("error", contribution.error());
        if (includeText && contribution.text() != null) node.put("text", contribution.text());
    }

    public synchronized void draftProduced(int attempt, String draft, Critique critique, long generationMillis) {
        if (current == null) return;
        ObjectNode node = drafts().addObject();
        node.put("attempt", attempt);
        node.put("generationMillis", generationMillis);
        node.put("chars", draft == null ? 0 : draft.length());
        if (includeText) node.put("draft", draft);
        if (critique != null) {
            ObjectNode c = node.putObject("critique");
            c.put("available", critique.isAvailable());
            if (critique.isAvailable()) {
                c.put("score", critique.getScore());
                c.put("approved", critique.isApproved());
                ArrayNode issues = c.putArray("issues");
                critique.getIssues().forEach(issues::add);
            } else {
                c.put("error", critique.getError());
            }
        }
    }

    public synchronized void feedbackReceived(int attempt, Feedback feedback) {
        if (current == null) return;
        for (var node : drafts()) {
            if (node.path("attempt").asInt() != attempt) continue;
            ObjectNode draft = (ObjectNode) node;
            draft.put("feedback", feedback.getType().name());
            // la raison d'un refus est une consigne de l'utilisateur : utile pour apprendre ses preferences
            if (feedback.getType() == Feedback.Type.REJECT && feedback.getText() != null) {
                draft.put("reason", feedback.getText());
            }
        }
    }

    public synchronized void finished(Email email, Intention intention) {
        if (current == null) return;
        current.put("outcome", ActivityLog.outcomeOf(intention).name());
        current.put("draftCount", intention.getAttempts());
        current.put("totalMillis", elapsed());
        write(email);
    }

    public synchronized void failed(Email email, WorkflowStage stage, String error) {
        if (current == null) return;
        current.put("outcome", ActivityLog.Outcome.ERROR.name());
        current.put("failedStage", stage.name());
        current.put("error", error);
        current.put("totalMillis", elapsed());
        write(email);
    }

    private void write(Email email) {
        ObjectNode mail = MAPPER.createObjectNode();
        mail.put("threadId", email.getThreadID());
        mail.put("from", email.getSenderAddress());
        mail.put("subject", email.getSubject());
        mail.put("bodyChars", email.getBody().length());
        if (includeText) mail.put("body", email.getBody());
        current.set("email", mail);
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, MAPPER.writeValueAsString(current) + System.lineSeparator(),
                              StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Could not write the decision log " + file + ": " + e.getMessage());
        }
        current = null;
    }

    private ArrayNode drafts() {
        return current.has("attempts") ? (ArrayNode) current.get("attempts")
                                            : current.putArray("attempts");
    }

    private long elapsed() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static String excerpt(String s) {
        return s.length() <= EXCERPT ? s : s.substring(0, EXCERPT) + "...";
    }
}
