import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TemporaryContactBook {
    private final Map<String, String> roles = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();

    public TemporaryContactBook(String file) throws IOException {
        load(file);
    }

    
    private void load(String file) throws IOException {
        JsonNode root = new ObjectMapper().readTree(Files.readString(Path.of(file)));
        for (JsonNode contact : root) {
            String address = contact.path("address").asText().trim().toLowerCase();
            roles.put(address, contact.path("role").asText().toUpperCase());
            names.put(address, contact.path("name").asText());
        }
    }

    
    public String getRole(String address) {
        return roles.get(address.trim().toLowerCase());
    }

    public String getName(String address) {
        return names.get(address.trim().toLowerCase());
    }
}
