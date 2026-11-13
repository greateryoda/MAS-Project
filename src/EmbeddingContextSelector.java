import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Selection semantique (RAG local) : similarite cosinus entre l'embedding de l'email
// et celui de chaque croyance. Reconnait les reformulations et les synonymes, y compris
// entre francais et anglais, contrairement a la selection lexicale.
// Si le serveur d'embeddings ne repond pas, on se rabat sur une autre strategie
// (la redaction ne doit pas echouer a cause du contexte).
public class EmbeddingContextSelector implements ContextSelector {
    private final Embedder embedder;
    private final ContextSelector fallback;
    private volatile boolean degraded = false;

    public EmbeddingContextSelector(Embedder embedder, ContextSelector fallback) {
        this.embedder = embedder;
        this.fallback = fallback;
    }

    public String name() {
        return degraded ? "embedding unavailable -> " + fallback.name() : "embedding";
    }

    public List<Belief> select(Email email, List<Belief> beliefs, int limit) {
        if (beliefs.isEmpty() || limit <= 0) return List.of();
        double[] scores;
        try {
            scores = scores(email, beliefs);
            degraded = false;
        } catch (LlmException e) {
            if (!degraded) System.err.println("Embedding selection unavailable, using " + fallback.name()
                                              + ": " + e.getMessage());
            degraded = true;
            return fallback.select(email, beliefs, limit);
        }

        List<Integer> ranked = new ArrayList<>();
        for (int i = 0; i < beliefs.size(); i++) ranked.add(i);
        ranked.sort(Comparator.comparingDouble(i -> -scores[i]));
        List<Integer> kept = new ArrayList<>(ranked.subList(0, Math.min(limit, ranked.size())));
        kept.sort(null);
        List<Belief> result = new ArrayList<>();
        for (int i : kept) result.add(beliefs.get(i));
        return result;
    }

    private double[] scores(Email email, List<Belief> beliefs) {
        List<String> texts = new ArrayList<>();
        texts.add(email.getSubject() + "\n" + email.getBody());
        for (Belief b : beliefs) texts.add(b.getContent());
        List<double[]> vectors = embedder.embed(texts);
        double[] query = vectors.get(0);
        double[] scores = new double[beliefs.size()];
        for (int i = 0; i < scores.length; i++) scores[i] = cosine(query, vectors.get(i + 1));
        return scores;
    }

    static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / Math.sqrt(na * nb);
    }
}
