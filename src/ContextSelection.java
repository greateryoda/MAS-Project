import java.util.ArrayList;
import java.util.List;

// Contexte effectivement transmis au LLM pour un email : croyances de base retenues
// et derniers messages du fil, avec le nombre d'elements disponibles (pour l'explicabilite).
public record ContextSelection(String strategy,
                               List<Belief> base, int baseAvailable,
                               List<Belief> thread, int threadAvailable) {

    public List<Belief> all() {
        List<Belief> all = new ArrayList<>(base);
        all.addAll(thread);
        return all;
    }

    // taille du contexte en caracteres : indicateur simple du cout pour le LLM
    public int chars() {
        int n = 0;
        for (Belief b : all()) n += b.getContent().length();
        return n;
    }

    public String summary() {
        return base.size() + "/" + baseAvailable + " context belief(s) selected (" + strategy + "), "
                + thread.size() + "/" + threadAvailable + " message(s) from this thread's memory, "
                + chars() + " chars";
    }
}
