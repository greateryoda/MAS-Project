import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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

    public String ask(String systemPrompt, String message){
        try {
            return send(systemPrompt, message);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
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
            return "Error" + response.statusCode() + ": " + response.body();
        }

        JsonNode json = mapper.readTree(response.body());
        return json.at("/choices/0/message/content").asText();
    }
}