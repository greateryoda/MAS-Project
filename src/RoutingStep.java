// Un maillon de la chaine de routage (Chain of Responsibility).
// Renvoie une decision, ou null si le maillon ne sait pas decider.
public interface RoutingStep {
    RoutingDecision decide(Email email);
}
