import java.io.File;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class App {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::start);
    }

    private static void start() {
        Theme.install();
        try {
            JsonNode config = new ObjectMapper().readTree(new File("config.json"));
            LmStudioClient llm = new LmStudioClient(config.path("url").asText(), config.path("model").asText());
            JsonNode critic = config.path("critic");
            SwingValidator validator = new SwingValidator();
            EmailAssistant assistant = new EmailAssistant(llm, validator,
                    critic.path("systemPromptFile").asText("critic/attitude_critic.txt"),
                    critic.path("enabled").asBoolean(true));
            List<DemoEmail> demos = DemoEmail.load("demo/emails.json");
            new MainWindow(assistant, llm, validator, demos).setVisible(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "The application could not start:\n" + e.getMessage()
                    + "\n\nRun it from the project root folder (where config.json is).",
                    "MAS Mail Assistant", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
