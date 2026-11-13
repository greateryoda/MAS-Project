import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

public class RoutingAgent{
    public static final String FALLBACK_ROLE = "GENERIC";
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
    // collaboration : nombre maximal d'agents contributeurs en plus de l'agent principal (0 = desactivee)
    private volatile int maxCollaborators = 1;

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

    public void setMaxCollaborators(int max){
        this.maxCollaborators = Math.max(0, max);
    }

    public int getMaxCollaborators(){
        return maxCollaborators;
    }

    // le routeur transmet l'email a l'agent, puis fait l'intermediaire entre l'agent et l'utilisateur
    // jusqu'a ce que l'intention de l'agent soit atteinte (reponse validee) ou abandonnee
    public Intention RouteMail(Email email){
        try {
            return handle(email);
        } catch (RuntimeException e) {
            notify(currentStage, StageStatus.FAILED, e.getMessage());
            for (WorkflowListener l : listeners) l.failed(email, currentStage, e.getMessage());
            throw e;
        }
    }

    private Intention handle(Email email){
        notify(WorkflowStage.RECEIVED, StageStatus.COMPLETED,
               "From " + email.getSenderAddress() + "\nSubject: " + email.getSubject());

        RoutingOutcome routing = classify(email);
        RoutingDecision decision = routing.decision();
        Agent agent = agents.get(decision.getRole());
        notify(WorkflowStage.ROUTING, StageStatus.COMPLETED, routingSummary(routing));
        notify(WorkflowStage.SELECTION, StageStatus.COMPLETED,
               agent.getName() + String.format(" selected (confidence %.2f)", decision.getConfidence()));
        for (WorkflowListener l : listeners) {
            l.agentSelected(agent, decision, routing.scores(), routing.signals(), routing.arbiterConsulted());
        }

        String knownName = contacts.getName(email.getSenderAddress());
        if (knownName != null) email.setSenderName(knownName);

        Intention intention = agent.adopt(email);
        notify(WorkflowStage.TRANSMISSION, StageStatus.COMPLETED,
               "Email forwarded to " + agent.getName()
               + (decision.isCollaborative() ? " (main agent) and to " + names(decision) + " (contributing)" : "")
               + ", intention adopted (at most " + intention.getMaxAttempts() + " drafts)");
        ContextSelection context = agent.selectContext(email);
        notify(WorkflowStage.CONTEXT, StageStatus.COMPLETED, context.summary());
        for (WorkflowListener l : listeners) l.contextSelected(agent, context);

        // email qui concerne plusieurs agents : chacun repond a sa partie avant le premier brouillon ;
        // les contributions sont gardees pour les brouillons suivants (seul l'agent principal redige a nouveau)
        intention.setContributions(collaborate(email, agent, decision));

        while (intention.isActive()) {
            int attempt = intention.getAttempts() + 1;
            notify(WorkflowStage.GENERATION, StageStatus.PROCESSING,
                   "Draft " + attempt + "/" + intention.getMaxAttempts() + " requested from the local LLM");
            long start = System.nanoTime();
            String draft = agent.draft(intention);
            long generationMillis = (System.nanoTime() - start) / 1_000_000;
            notify(WorkflowStage.GENERATION, StageStatus.COMPLETED,
                   "Draft " + attempt + "/" + intention.getMaxAttempts() + " written by " + agent.getName());
            Critique critique = review(email, agent, draft, intention.getContributions());
            for (WorkflowListener l : listeners) l.draftProduced(attempt, draft, critique, generationMillis);
            Proposal proposal = new Proposal(email, agent, decision, draft,
                                             intention.getAttempts(), intention.getMaxAttempts(), critique,
                                             intention.getContributions());
            notify(WorkflowStage.PROPOSAL, StageStatus.COMPLETED,
                   "Draft " + attempt + " returned to the user");
            notify(WorkflowStage.VALIDATION, StageStatus.PROCESSING, "Waiting for the user's decision");
            Feedback feedback = validator.review(proposal);
            for (WorkflowListener l : listeners) l.feedbackReceived(attempt, feedback);
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
        for (WorkflowListener l : listeners) l.finished(email, intention);
        return intention;
    }

    // Routage seul, sans redaction : quel agent doit repondre, et pourquoi.
    // Utilise par le traitement complet et par les experiences (Evaluation).
    public RoutingOutcome classify(Email email){
        long start = System.nanoTime();
        notify(WorkflowStage.ROUTING, StageStatus.PROCESSING, "Collecting the routing signals");
        Map<String, Double> scores = new HashMap<>();
        List<String> signals = new ArrayList<>();
        boolean[] arbiterConsulted = {false};
        RoutingDecision decision = decide(email, scores, signals, arbiterConsulted);
        return new RoutingOutcome(decision, Map.copyOf(scores), List.copyOf(signals), arbiterConsulted[0],
                                  (System.nanoTime() - start) / 1_000_000);
    }

    // Collaboration : chaque agent contributeur repond a sa partie de l'email pour l'agent principal.
    // Un contributeur en echec n'arrete pas le traitement : l'agent principal redige alors seul.
    // Public : utilise aussi par les experiences (ReplyEvaluation).
    public List<Contribution> collaborate(Email email, Agent lead, RoutingDecision decision){
        if (!decision.isCollaborative()) {
            notify(WorkflowStage.COLLABORATION, StageStatus.SKIPPED, "Single agent: no other agent is needed");
            return List.of();
        }
        List<Contribution> contributions = new ArrayList<>();
        for (RoutingDecision.Collaborator c : decision.getCollaborators()) {
            Agent helper = agents.get(c.role());
            notify(WorkflowStage.COLLABORATION, StageStatus.PROCESSING,
                   helper.getName() + " answers its part for " + lead.getName() + ": " + c.part());
            long start = System.nanoTime();
            Contribution contribution;
            try {
                String text = helper.contribute(email, c.part(), lead.getName());
                contribution = Contribution.of(helper, c.part(), text, (System.nanoTime() - start) / 1_000_000);
                if (!contribution.available()) {
                    contribution = Contribution.failed(helper, c.part(), "empty answer", contribution.millis());
                }
            } catch (LlmException e) {
                contribution = Contribution.failed(helper, c.part(), e.getMessage(), (System.nanoTime() - start) / 1_000_000);
            }
            contributions.add(contribution);
            for (WorkflowListener l : listeners) l.contributionProduced(helper, contribution);
        }
        boolean any = contributions.stream().anyMatch(Contribution::available);
        StringBuilder detail = new StringBuilder();
        for (Contribution c : contributions) detail.append(c.summary()).append("\n");
        detail.append(any ? lead.getName() + " merges the contributions into one reply"
                          : lead.getName() + " answers alone");
        notify(WorkflowStage.COLLABORATION, any ? StageStatus.COMPLETED : StageStatus.FAILED, detail.toString());
        return contributions;
    }

    private Critique review(Email email, Agent agent, String draft, List<Contribution> contributions){
        if (critic == null || !critic.isEnabled()) {
            notify(WorkflowStage.CRITIC, StageStatus.SKIPPED, "CriticAgent disabled");
            return null;
        }
        notify(WorkflowStage.CRITIC, StageStatus.PROCESSING, "CriticAgent checks the draft against the agent's rules");
        Critique critique = critic.review(email, agent, draft, contributions);
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
    private RoutingDecision decide(Email email, Map<String, Double> scores, List<String> signals,
                                   boolean[] arbiterConsulted){
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

        // collaboration : possible seulement si au moins deux agents sont concernes d'apres les signaux
        // (votes ou mots-cles) ; l'arbitre confirme et dit quelle partie de l'email revient a chacun
        Set<String> pool = maxCollaborators > 0 ? concerned(email, scores) : Set.of();
        if (pool.size() < 2) pool = Set.of();
        if (!pool.isEmpty()) signals.add("several agents concerned -> collaboration possible among " + pool);
        arbiterConsulted[0] = true;
        RoutingDecision d = arbiter.choose(email, candidates, pool);
        if (d != null && d.getConfidence() >= ARBITER_MIN) {
            signals.add(String.format("%s: %s %.2f", d.getReason(), d.getRole(), d.getConfidence()));
            List<RoutingDecision.Collaborator> helpers = pool.isEmpty() ? List.of() : collaborators(d, pool);
            for (RoutingDecision.Collaborator h : helpers) {
                signals.add("collaboration: " + h.role() + " answers \"" + h.part() + "\"");
            }
            return new RoutingDecision(d.getRole(), d.getConfidence(), String.join(", ", signals), helpers);
        }

        if (!agents.containsKey(FALLBACK_ROLE)) {
            throw new IllegalStateException("No agent found and no generic agent for: " + email.getSenderAddress());
        }
        signals.add("no clear winner -> " + FALLBACK_ROLE);
        return new RoutingDecision(FALLBACK_ROLE, 0.0, String.join(", ", signals));
    }

    // agents concernes par l'email : ceux qui ont vote et ceux que les maillons reperent sans voter
    // pour eux (mots-cles d'un second agent) ; l'agent generique n'est jamais contributeur
    private Set<String> concerned(Email email, Map<String, Double> scores){
        Set<String> concerned = new TreeSet<>(scores.keySet());
        for (RoutingStep step : steps) concerned.addAll(step.concerned(email));
        concerned.retainAll(agents.keySet());
        concerned.remove(FALLBACK_ROLE);
        return concerned;
    }

    // on ne garde que des contributeurs credibles : agents concernes par l'email (pool),
    // differents de l'agent principal et de l'agent generique, avec une partie decrite, sans doublon
    private List<RoutingDecision.Collaborator> collaborators(RoutingDecision d, Set<String> pool){
        List<RoutingDecision.Collaborator> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RoutingDecision.Collaborator c : d.getCollaborators()) {
            if (kept.size() >= maxCollaborators) break;
            String role = c.role();
            if (role.equals(d.getRole()) || role.equals(FALLBACK_ROLE) || !pool.contains(role)
                || !agents.containsKey(role) || c.part().isBlank() || !seen.add(role)) continue;
            kept.add(c);
        }
        return kept;
    }

    private String names(RoutingDecision decision){
        List<String> names = new ArrayList<>();
        for (RoutingDecision.Collaborator c : decision.getCollaborators()) names.add(agents.get(c.role()).getName());
        return String.join(", ", names);
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

    private String routingSummary(RoutingOutcome routing){
        if (routing.fallback()) return "No agent is suitable enough: fallback to the generic agent";
        if (routing.decision().isCollaborative()) {
            return "Decision taken by the LLM arbiter: " + agents.get(routing.decision().getRole()).getName()
                   + " leads, " + names(routing.decision()) + " contribute(s)";
        }
        if (routing.arbiterConsulted()) return "Decision taken by the LLM arbiter (LlmClassifierStep)";
        return "Signals agree, decision taken without the LLM";
    }

    private void notify(WorkflowStage stage, StageStatus status, String detail){
        currentStage = stage;
        for (WorkflowListener l : listeners) l.stageChanged(stage, status, detail);
    }
}
