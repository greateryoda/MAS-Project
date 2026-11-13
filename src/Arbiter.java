import java.util.Set;

// Tranche entre les agents candidats quand les signaux de routage ne suffisent pas.
// Renvoie une decision, ou null si l'arbitre ne sait pas choisir.
public interface Arbiter {
    RoutingDecision choose(Email email, Set<String> candidates);

    // collaboration possible (contributors non vide) : l'arbitre peut aussi designer, parmi
    // "contributors", des agents qui repondent chacun a une partie de l'email
    // (RoutingDecision.getCollaborators). Par defaut, un arbitre ne propose pas de collaboration.
    default RoutingDecision choose(Email email, Set<String> candidates, Set<String> contributors) {
        return choose(email, candidates);
    }
}
