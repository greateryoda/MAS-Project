import java.util.List;

// Strategie de selection du contexte (Strategy) : parmi les croyances de base d'un agent,
// lesquelles meritent d'etre envoyees au LLM pour repondre a cet email ?
// Un petit modele local a une fenetre de contexte limitee et se laisse distraire par
// les informations hors sujet : on n'envoie que ce qui est utile.
public interface ContextSelector {
    // nom affiche dans le workflow et dans le journal des decisions
    String name();

    // renvoie au plus `limit` croyances, dans leur ordre d'origine
    List<Belief> select(Email email, List<Belief> beliefs, int limit);
}
