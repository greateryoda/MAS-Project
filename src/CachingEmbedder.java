import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Decorator : garde en memoire les embeddings deja calcules.
// Les croyances d'un agent changent rarement : elles ne sont embarquees qu'une fois,
// seul l'email recu demande un nouvel appel au serveur.
public class CachingEmbedder implements Embedder {
    private final Embedder inner;
    private final Map<String, double[]> cache = new ConcurrentHashMap<>();

    public CachingEmbedder(Embedder inner) {
        this.inner = inner;
    }

    public List<double[]> embed(List<String> texts) {
        List<String> missing = new ArrayList<>();
        for (String t : texts) if (!cache.containsKey(t) && !missing.contains(t)) missing.add(t);
        if (!missing.isEmpty()) {
            List<double[]> vectors = inner.embed(missing);
            for (int i = 0; i < missing.size(); i++) cache.put(missing.get(i), vectors.get(i));
        }
        List<double[]> result = new ArrayList<>();
        for (String t : texts) result.add(cache.get(t));
        return result;
    }
}
