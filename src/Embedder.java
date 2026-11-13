import java.util.List;

// Calcule des embeddings (vecteurs semantiques) pour des textes, avec un modele local.
// Leve LlmException si le serveur ne repond pas.
public interface Embedder {
    List<double[]> embed(List<String> texts);
}
