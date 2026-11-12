import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record DemoEmail(String label, String address, String name, String subject, String body, int threadId) {

    public static List<DemoEmail> load(String file) throws IOException {
        List<DemoEmail> list = new ArrayList<>();
        File f = new File(file);
        if (!f.exists()) return list;
        for (JsonNode n : new ObjectMapper().readTree(f)) {
            list.add(new DemoEmail(n.path("label").asText(), n.path("address").asText(), n.path("name").asText(),
                                   n.path("subject").asText(), n.path("body").asText(), n.path("threadId").asInt()));
        }
        return list;
    }

    public Email toEmail() {
        Email email = new Email(address, subject, body, today(), threadId);
        if (!name.isBlank()) email.setSenderName(name);
        return email;
    }

    public static String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    public String toString() {
        return label;
    }
}
