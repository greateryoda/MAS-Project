import java.util.List;
import java.util.Map;

// Observer du traitement d'un email par le RoutingAgent.
// Les methodes par defaut permettent a chaque observateur (interface, journal, tests)
// de n'ecouter que les evenements qui l'interessent.
public interface WorkflowListener {
    void stageChanged(WorkflowStage stage, StageStatus status, String detail);

    void agentSelected(Agent agent, RoutingDecision decision, Map<String, Double> scores,
                       List<String> signals, boolean arbiterConsulted);

    // contexte retenu par l'agent pour cet email
    default void contextSelected(Agent agent, ContextSelection context) {}

    // collaboration : un agent contributeur a repondu (ou echoue) sur sa partie de l'email
    default void contributionProduced(Agent contributor, Contribution contribution) {}

    // un brouillon vient d'etre redige (critique : null si le CriticAgent est desactive)
    default void draftProduced(int attempt, String draft, Critique critique, long generationMillis) {}

    // decision de l'utilisateur sur ce brouillon
    default void feedbackReceived(int attempt, Feedback feedback) {}

    // fin du traitement (intention atteinte, refusee ou abandonnee)
    default void finished(Email email, Intention intention) {}

    // le traitement a echoue (LLM injoignable, aucun agent...)
    default void failed(Email email, WorkflowStage stage, String error) {}
}
