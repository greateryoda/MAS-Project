import java.util.Set;

// Un maillon de la chaine de routage (Chain of Responsibility).
// Renvoie une decision, ou null si le maillon ne sait pas decider.
public interface RoutingStep {
    RoutingDecision decide(Email email);

    // agents concernes par l'email selon ce maillon, meme s'il ne vote pas pour eux
    // (sert a la collaboration : un agent ne peut contribuer que s'il y a un indice qu'il est concerne).
    // Par defaut : l'agent pour lequel le maillon vote.
    default Set<String> concerned(Email email) {
        RoutingDecision d = decide(email);
        return d == null ? Set.of() : Set.of(d.getRole());
    }
}
