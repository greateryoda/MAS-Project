import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public RoutingAgent(Map<String, Agent> agents, TemporaryContactBook contacts,
                        List<RoutingStep> steps, Arbiter arbiter, Validator validator){
        this.agents = agents;
        this.contacts = contacts;
        this.steps = steps;
        this.arbiter = arbiter;
        this.validator = validator;
    }

    // le routeur transmet l'email a l'agent, puis fait l'intermediaire entre l'agent et l'utilisateur
    // jusqu'a ce que l'intention de l'agent soit atteinte (reponse validee) ou abandonnee
    public Intention RouteMail(Email email){
        RoutingDecision decision = decide(email);
        Agent agent = agents.get(decision.getRole());
        email.setSenderName(contacts.getName(email.getSenderAddress()));

        Intention intention = agent.adopt(email);
        while (intention.isActive()) {
            String draft = agent.draft(intention);
            Proposal proposal = new Proposal(email, agent, decision, draft,
                                             intention.getAttempts(), intention.getMaxAttempts());
            agent.reconsider(intention, validator.review(proposal));
        }

        // l'agent a abandonne : l'utilisateur peut encore ecrire la reponse lui-meme
        if (intention.getStatus() == Intention.Status.DROPPED) {
            String manual = validator.writeReply(email);
            if (manual != null) agent.reconsider(intention, Feedback.modify(manual));
        }
        return intention;
    }

    // chaque maillon vote pour un agent ; on additionne les votes par agent
    private RoutingDecision decide(Email email){
        Map<String, Double> scores = new HashMap<>();
        List<String> signals = new ArrayList<>();

        for (RoutingStep step : steps) {
            vote(step.decide(email), scores, signals);
        }

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
}
