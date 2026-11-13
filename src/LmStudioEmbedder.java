import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

// Embeddings locaux via l'endpoint /v1/embeddings (API compatible OpenAI : LM Studio, Ollama, llama.cpp).
public class LmStudioEmbedder implements Embedder {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final String url;
    private final String model;

    public LmStudioEmbedder(String url, String model) {
        this.url = url;
        this.model = model;
    }

    public String getModel() { return model; }

    public List<double[]> embed(List<String> texts) {
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            ArrayNode input = body.putArray("input");
            for (String t : texts) input.add(t);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException("Embedding error " + response.statusCode()
                        + " (is the embedding model \"" + model + "\" loaded?): " + response.body(), null);
            }
            List<double[]> vectors = new ArrayList<>();
            for (JsonNode item : MAPPER.readTree(response.body()).path("data")) {
                JsonNode values = item.path("embedding");
                double[] v = new double[values.size()];
                for (int i = 0; i < v.length; i++) v[i] = values.get(i).asDouble();
                vectors.add(v);
            }
            if (vectors.size() != texts.size()) {
                throw new LlmException("Embedding server returned " + vectors.size() + " vectors for "
                        + texts.size() + " texts", null);
            }
            return vectors;
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Could not reach the embedding server at " + url + ": " + e, e);
        }
    }
}
