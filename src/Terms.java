import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Decoupe un texte (francais ou anglais) en termes comparables :
// minuscules, sans accents, sans mots vides, tronques a 4 lettres.
// La troncature est un "stemming" volontairement grossier mais sans dependance :
// examen / exam -> "exam", submission / submit -> "subm", reunion / réunion -> "reun".
public final class Terms {
    private static final int STEM = 4;
    private static final Set<String> STOPWORDS = Set.copyOf(List.of(
            // anglais
            "the", "and", "for", "you", "your", "are", "with", "this", "that", "have", "has", "had", "from",
            "will", "could", "would", "should", "can", "please", "thanks", "thank", "hello", "dear", "best",
            "regards", "about", "what", "which", "when", "where", "there", "their", "our", "not", "but", "all",
            "any", "also", "was", "were", "been", "into", "its", "then", "than", "them", "they", "him", "her",
            "his", "she", "may", "might", "does", "did", "just", "some", "more", "very", "like", "know", "need",
            "how", "who", "why", "yes", "let", "much", "many", "one", "two", "each", "other", "here", "only",
            "after", "before", "again", "still", "would", "kind", "kindly", "advance", "hi",
            // francais
            "les", "des", "pour", "une", "est", "que", "qui", "dans", "sur", "avec", "vous", "nous", "pas",
            "par", "mon", "mes", "votre", "vos", "notre", "nos", "cordialement", "bonjour", "merci", "aux",
            "ces", "cette", "son", "ses", "leur", "leurs", "mais", "donc", "car", "tres", "plus", "comme",
            "etre", "avoir", "fait", "faire", "peut", "pouvez", "pourriez", "est-ce", "aussi", "bien",
            "avance", "madame", "monsieur", "elle", "ils", "elles", "sont", "suis", "avez", "ete"));

    private Terms() {}

    public static List<String> of(String text) {
        List<String> terms = new ArrayList<>();
        if (text == null) return terms;
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        for (String token : plain.split("[^\\p{L}\\p{N}]+")) {
            if (!keep(token)) continue;
            terms.add(token.length() > STEM && !hasDigit(token) ? token.substring(0, STEM) : token);
        }
        return terms;
    }

    // mots de 3 lettres ou plus, ou identifiants courts avec chiffre (TP2, D2, WP3)
    private static boolean keep(String token) {
        if (token.isEmpty() || STOPWORDS.contains(token)) return false;
        return token.length() >= 3 || (token.length() >= 2 && hasDigit(token));
    }

    private static boolean hasDigit(String token) {
        for (char c : token.toCharArray()) if (Character.isDigit(c)) return true;
        return false;
    }
}
