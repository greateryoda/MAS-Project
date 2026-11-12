import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class RoutingAgent{
    private static final String FALLBACK_ROLE = "GENERIC";
    private static final double AGREEMENT = 0.75; // part minimale du gagnant pour decider sans le LLM
    private static final double MIN_SCORE = 0.7;  // score total minimal du gagnant : contact ou fil suffisent seuls,
                                                  // domaine ou mots-cles seuls non (ils doivent se confirmer entre eux)
    private static final double ARBITER_MIN = 0.6; // confiance minimale de l'arbitre pour que son choix soit retenu

    private Map<String, Agent> agents;
    private TemporaryContactBook contacts;
    private List<RoutingStep> steps;
    private Arbiter arbiter; // appele seulement si les signaux sont faibles ou contradictoires
    private Validator validator; // l'utilisateur, qui valide les brouillons
    private CriticAgent critic;
    private final List<WorkflowListener> listeners = new CopyOnWriteArrayList<>();
    private WorkflowStage currentStage = WorkflowStage.RECEIVED;

    public RoutingAgent(Map<String, Agent> agents, TemporaryContactBook contacts,
                        List<RoutingStep> steps, Arbiter arbiter, Validator validator){
        this(agents, contacts, steps, arbiter, validator, null);
    }

    public RoutingAgent(Map<String, Agent> agents, TemporaryContactBook contacts,
                        List<RoutingStep> steps, Arbiter arbiter, Validator validator,
                        CriticAgent critic){
        this.agents = agents;
        this.contacts = contacts;
        this.steps = steps;
        this.arbiter = arbiter;
        this.validator = validator;
        this.critic = critic;
    }

    public void addListener(WorkflowListener listener){
        listeners.add(listener);
    }

    // le routeur transmet l'email a l'agent, puis fait l'intermediaire entre l'agent et l'utilisateur
    // jusqu'a ce que l'intention de l'agent soit atteinte (reponse validee) ou abandonnee
    public Intention RouteMail(Email email){
        try {
            return handle(email);
        } catch (RuntimeException e) {
            notify(currentStage, StageStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    private Intention handle(Email email){
        notify(WorkflowStage.RECEIVED, StageStatus.COMPLETED,
               "From " + email.getSenderAddress() + "\nSubject: " + email.getSubject());

        notify(WorkflowStage.ROUTING, StageStatus.PROCESSING, "Collecting the routing signals");
        Map<String, Double> scores = new HashMap<>();
        List<String> signals = new ArrayList<>();
        RoutingDecision decision = decide(email, scores, signals);
        boolean arbiterConsulted = signals.stream().anyMatch(s -> s.contains("-> arbiter among"));
        Agent agent = agents.get(decision.getRole());
        notify(WorkflowStage.ROUTING, StageStatus.COMPLETED, routingSummary(decision, arbiterConsulted));
        notify(WorkflowStage.SELECTION, StageStatus.COMPLETED,
               agent.getName() + String.format(" selected (confidence %.2f)", decision.getConfidence()));
        for (WorkflowListener l : listeners) {
            l.agentSelected(agent, decision, Map.copyOf(scores), List.copyOf(signals), arbiterConsulted);
        }

        String knownName = contacts.getName(email.getSenderAddress());
        if (knownName != null) email.setSenderName(knownName);

        Intention intention = agent.adopt(email);
        notify(WorkflowStage.TRANSMISSION, StageStatus.COMPLETED,
               "Email forwarded to " + agent.getName() + ", intention adopted (at most "
               + intention.getMaxAttempts() + " drafts)");
        notify(WorkflowStage.CONTEXT, StageStatus.COMPLETED, describe(agent.recall(email.getThreadID())));

        while (intention.isActive()) {
            int attempt = intention.getAttempts() + 1;
            notify(WorkflowStage.GENERATION, StageStatus.PROCESSING,
                   "Draft " + attempt + "/" + intention.getMaxAttempts() + " requested from the local LLM");
            String draft = agent.draft(intention);
            notify(WorkflowStage.GENERATION, StageStatus.COMPLETED,
                   "Draft " + attempt + "/" + intention.getMaxAttempts() + " written by " + agent.getName());
            Critique critique = review(email, agent, draft);
            Proposal proposal = new Proposal(email, agent, decision, draft,
                                             intention.getAttempts(), intention.getMaxAttempts(), critique);
            notify(WorkflowStage.PROPOSAL, StageStatus.COMPLETED,
                   "Draft " + attempt + " returned to the user");
            notify(WorkflowStage.VALIDATION, StageStatus.PROCESSING, "Waiting for the user's decision");
            Feedback feedback = validator.review(proposal);
            agent.reconsider(intention, feedback);
            if (feedback.getType() == Feedback.Type.REJECT && intention.isActive()) {
                notify(WorkflowStage.VALIDATION, StageStatus.PROCESSING, "Draft rejected, a new draft is requested");
            }
        }

        // l'agent a abandonne : l'utilisateur peut encore ecrire la reponse lui-meme
        if (intention.getStatus() == Intention.Status.DROPPED) {
            notify(WorkflowStage.VALIDATION, StageStatus.PROCESSING, "All drafts rejected, waiting for a manual reply");
            String manual = validator.writeReply(email);
            if (manual != null) agent.reconsider(intention, Feedback.modify(manual));
        }

        notifyOutcome(intention, agent);
        return intention;
    }

    private Critique review(Email email, Agent agent, String draft){
        if (critic == null || !critic.isEnabled()) {
            notify(WorkflowStage.CRITIC, StageStatus.SKIPPED, "CriticAgent disabled");
            return null;
        }
        notify(WorkflowStage.CRITIC, StageStatus.PROCESSING, "CriticAgent checks the draft against the agent's rules");
        Critique critique = critic.review(email, agent, draft);
        notify(WorkflowStage.CRITIC, critique.isAvailable() ? StageStatus.COMPLETED : StageStatus.FAILED,
               critique.summary());
        return critique;
    }

    private void notifyOutcome(Intention intention, Agent agent){
        switch (intention.getStatus()) {
            case ACHIEVED -> notify(WorkflowStage.VALIDATION, StageStatus.COMPLETED,
                    "Reply validated and saved in the memory of " + agent.getName() + " (not sent automatically)");
            case DISCARDED -> notify(WorkflowStage.VALIDATION, StageStatus.FAILED,
                    "Reply refused by the user, nothing was saved");
            default -> notify(WorkflowStage.VALIDATION, StageStatus.FAILED,
                    "No reply: " + intention.getAttempts() + " draft(s) rejected");
        }
    }

    // chaque maillon vote pour un agent ; on additionne les votes par agent
    private RoutingDecision decide(Email email, Map<String, Double> scores, List<String> signals){
        for (RoutingStep step : steps) {
            vote(step.decide(email), scores, signals);
        }
        notify(WorkflowStage.SIGNALS, StageStatus.COMPLETED,
               signals.isEmpty() ? "No signal matched" : String.join("\n", signals));

        // signaux concordants et assez forts : pas besoin du LLM
        String best = best(scores);
        if (best != null && share(scores, best) >= AGREEMENT && scores.get(best) >= MIN_SCORE) {
            return result(best, scores, signals);
        }

        // signaux contradictoires : l'arbitre tranche entre les agents en conflit
        // signaux faibles (un seul agent ou aucun) : l'arbitre choisit parmi tous les agents
        // dans les deux cas, son choix l'emporte
        Set<String> candidates = scores.size() > 1 ? scores.keySet() : new HashSet<>(agents.keySet());
        signals.add((scores.size() > 1 ? "conflict" : "weak signals") + " -> arbiter among " + candidates);
        notify(WorkflowStage.ROUTING, StageStatus.PROCESSING,
               (scores.size() > 1 ? "Conflicting" : "Weak") + " signals: the LLM arbiter is consulted");

        RoutingDecision d = arbiter.choose(email, candidates);
        if (d != null && d.getConfidence() >= ARBITER_MIN) {
            signals.add(String.format("%s: %s %.2f", d.getReason(), d.getRole(), d.getConfidence()));
            return new RoutingDecision(d.getRole(), d.getConfidence(), String.join(", ", signals));
        }

        if (!agents.containsKey(FALLBACK_ROLE)) {
            throw new IllegalStateException("No agent found and no generic agent for: " + email.getSenderAddress());
        }
        signals.add("no clear winner -> " + FALLBACK_ROLE);
        return new RoutingDecision(FALLBACK_ROLE, 0.0, String.join(", ", signals));
    }

    // on ignore les votes pour un agent qui n'existe pas
    private void vote(RoutingDecision d, Map<String, Double> scores, List<String> signals){
        if (d == null || !agents.containsKey(d.getRole())) return;
        scores.merge(d.getRole(), d.getConfidence(), Double::sum);
        signals.add(String.format("%s: %s %.2f", d.getReason(), d.getRole(), d.getConfidence()));
    }

    private String best(Map<String, Double> scores){
        String best = null;
        for (String role : scores.keySet()) {
            if (best == null || scores.get(role) > scores.get(best)) best = role;
        }
        return best;
    }

    // part du score de cet agent dans le total : 1.0 = tous les signaux sont d'accord
    private double share(Map<String, Double> scores, String role){
        double total = 0;
        for (double s : scores.values()) total += s;
        return scores.get(role) / total;
    }

    private RoutingDecision result(String role, Map<String, Double> scores, List<String> signals){
        return new RoutingDecision(role, share(scores, role), String.join(", ", signals));
    }

    private String routingSummary(RoutingDecision decision, boolean arbiterConsulted){
        if (decision.getConfidence() == 0.0 && FALLBACK_ROLE.equals(decision.getRole())) {
            return "No agent is suitable enough: fallback to the generic agent";
        }
        if (arbiterConsulted) return "Decision taken by the LLM arbiter (LlmClassifierStep)";
        return "Signals agree, decision taken without the LLM";
    }

    private String describe(List<Belief> beliefs){
        int base = 0, history = 0;
        for (Belief b : beliefs) {
            if (b.getSource() == Belief.Source.BASE) base++; else history++;
        }
        return base + " context belief(s) and " + history + " message(s) from this thread's memory";
    }

    private void notify(WorkflowStage stage, StageStatus status, String detail){
        currentStage = stage;
        for (WorkflowListener l : listeners) l.stageChanged(stage, status, detail);
    }
}
