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

public class LmStudioClient implements LLM{
    private static final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final String url;
    private final String model;

    public LmStudioClient(String url, String model){
        this.url = url;
        this.model = model;
    }

    public String getUrl()   { return url; }
    public String getModel() { return model; }

    public List<String> listModels(){
        String modelsUrl = url.replace("/chat/completions", "/models");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(modelsUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException("LLM server answered " + response.statusCode() + " on " + modelsUrl, null);
            }
            List<String> models = new ArrayList<>();
            for (JsonNode m : mapper.readTree(response.body()).path("data")) {
                models.add(m.path("id").asText());
            }
            return models;
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Could not reach the LLM server at " + modelsUrl + ": " + e, e);
        }
    }

    public String ask(String systemPrompt, String message){
        try {
            return send(systemPrompt, message);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Could not reach the LLM at " + url
                    + " (is LM Studio running with its server started?): " + e, e);
        }
    }

    private String send(String systemPrompt, String message) throws Exception{
        ObjectNode body = mapper.createObjectNode(); // create a json object
        body.put("model", model);   //add the model keyword into the created object
        ArrayNode messages = body.putArray("messages"); //add the messages keyword into the object. If we modify the variable messages, we will also modify the json object

        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", message);


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new LlmException("LLM error " + response.statusCode()
                    + " (is the model \"" + model + "\" loaded in LM Studio?): " + response.body(), null);
        }

        JsonNode json = mapper.readTree(response.body());
        return json.at("/choices/0/message/content").asText();
    }
}