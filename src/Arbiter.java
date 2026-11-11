import java.util.Set;

// Tranche entre les agents candidats quand les signaux de routage ne suffisent pas.
// Renvoie une decision, ou null si l'arbitre ne sait pas choisir.
public interface Arbiter {
    RoutingDecision choose(Email email, Set<String> candidates);
}
