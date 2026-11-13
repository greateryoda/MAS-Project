import java.util.Locale;

// Cree la strategie de selection du contexte demandee par un agent (champ "context" de agent.json),
// avec les valeurs par defaut de config.json. Ajouter une strategie = une classe + une ligne ici.
public class ContextSelectorFactory {
    public static final String ALL = "all";
    public static final String LEXICAL = "lexical";
    public static final String EMBEDDING = "embedding";

    private final String defaultStrategy;
    private final int defaultMaxBeliefs;
    private final int defaultMaxThreadMessages;
    private final Embedder embedder; // null si aucun modele d'embedding n'est configure
    private final boolean forced;    // experiences : impose la strategie a tous les agents

    public ContextSelectorFactory(String defaultStrategy, int defaultMaxBeliefs, int defaultMaxThreadMessages,
                                  Embedder embedder) {
        this(defaultStrategy, defaultMaxBeliefs, defaultMaxThreadMessages, embedder, false);
    }

    private ContextSelectorFactory(String defaultStrategy, int defaultMaxBeliefs, int defaultMaxThreadMessages,
                                   Embedder embedder, boolean forced) {
        this.defaultStrategy = defaultStrategy;
        this.defaultMaxBeliefs = defaultMaxBeliefs;
        this.defaultMaxThreadMessages = defaultMaxThreadMessages;
        this.embedder = embedder;
        this.forced = forced;
    }

    public static ContextSelectorFactory defaults() {
        return new ContextSelectorFactory(LEXICAL, 8, 6, null);
    }

    // meme fabrique, mais la strategie donnee remplace celle de chaque agent
    public ContextSelectorFactory forcing(String strategy) {
        return new ContextSelectorFactory(strategy, defaultMaxBeliefs, defaultMaxThreadMessages, embedder, true);
    }

    public int defaultMaxBeliefs()        { return defaultMaxBeliefs; }
    public int defaultMaxThreadMessages() { return defaultMaxThreadMessages; }

    public ContextSelector create(String requested) {
        String strategy = forced || requested == null || requested.isBlank() ? defaultStrategy : requested;
        return switch (strategy.trim().toLowerCase(Locale.ROOT)) {
            case ALL -> new AllContextSelector();
            case LEXICAL -> new LexicalContextSelector();
            case EMBEDDING -> {
                if (embedder == null) {
                    System.err.println("No embedding model configured (config.json \"embedding\"), using lexical selection");
                    yield new LexicalContextSelector();
                }
                yield new EmbeddingContextSelector(embedder, new LexicalContextSelector());
            }
            default -> throw new IllegalArgumentException("Unknown context strategy: " + strategy
                    + " (expected " + ALL + ", " + LEXICAL + " or " + EMBEDDING + ")");
        };
    }
}
