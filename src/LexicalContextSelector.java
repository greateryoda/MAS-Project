import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Selection lexicale par BM25 : on classe les croyances selon les termes qu'elles partagent
// avec l'email (sujet + corps), en donnant plus de poids aux termes rares dans le contexte de l'agent.
// Aucune dependance, aucun appel au LLM : rapide et deterministe.
// Limite : ne reconnait pas les synonymes ("salle" / "room") -> voir EmbeddingContextSelector.
public class LexicalContextSelector implements ContextSelector {
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    public String name() {
        return "lexical";
    }

    public List<Belief> select(Email email, List<Belief> beliefs, int limit) {
        if (beliefs.isEmpty() || limit <= 0) return List.of();
        double[] scores = scores(email.getSubject() + " " + email.getBody(), beliefs);

        List<Integer> ranked = new ArrayList<>();
        for (int i = 0; i < beliefs.size(); i++) if (scores[i] > 0) ranked.add(i);
        // meilleur score d'abord ; a egalite, la croyance la plus recente (plus bas dans le fichier)
        ranked.sort(Comparator.<Integer>comparingDouble(i -> -scores[i]).thenComparing(i -> -i));

        List<Integer> kept = new ArrayList<>(ranked.subList(0, Math.min(limit, ranked.size())));
        kept.sort(null); // ordre d'origine, plus lisible pour le LLM
        List<Belief> result = new ArrayList<>();
        for (int i : kept) result.add(beliefs.get(i));
        return result;
    }

    // score BM25 de chaque croyance pour la requete
    static double[] scores(String query, List<Belief> beliefs) {
        Set<String> queryTerms = new HashSet<>(Terms.of(query));
        List<List<String>> docs = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        double totalLength = 0;
        for (Belief b : beliefs) {
            List<String> terms = Terms.of(b.getContent());
            docs.add(terms);
            totalLength += terms.size();
            for (String t : new HashSet<>(terms)) df.merge(t, 1, Integer::sum);
        }
        double avgLength = Math.max(1, totalLength / docs.size());
        int n = docs.size();

        double[] scores = new double[n];
        for (int i = 0; i < n; i++) {
            List<String> doc = docs.get(i);
            Map<String, Integer> tf = new HashMap<>();
            for (String t : doc) tf.merge(t, 1, Integer::sum);
            double score = 0;
            for (String q : queryTerms) {
                Integer f = tf.get(q);
                if (f == null) continue;
                int d = df.get(q);
                double idf = Math.log(1 + (n - d + 0.5) / (d + 0.5));
                score += idf * f * (K1 + 1) / (f + K1 * (1 - B + B * doc.size() / avgLength));
            }
            scores[i] = score;
        }
        return scores;
    }
}
