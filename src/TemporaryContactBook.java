import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TemporaryContactBook {
    private final Map<String, String> roles = new HashMap<>();

    public TemporaryContactBook(String file) throws IOException {
        load(file);
    }

    
    private void load(String file) throws IOException {
        JsonNode root = new ObjectMapper().readTree(Files.readString(Path.of(file)));
        for (JsonNode contact : root) {
            roles.put(contact.path("address").asText().trim().toLowerCase(),
                      contact.path("role").asText().toUpperCase());
        }
    }

    
    public String getRole(String address) {
        return roles.get(address.trim().toLowerCase());
    }
}
