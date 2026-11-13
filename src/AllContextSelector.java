import java.util.List;

// Tout le contexte, sans selection : comportement d'origine, sert de reference dans les experiences.
public class AllContextSelector implements ContextSelector {
    public String name() {
        return "all";
    }

    public List<Belief> select(Email email, List<Belief> beliefs, int limit) {
        return List.copyOf(beliefs);
    }
}
