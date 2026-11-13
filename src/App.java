import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class App {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::start);
    }

    private static void start() {
        Theme.install();
        try {
            AppConfig config = AppConfig.load("config.json");
            LmStudioClient llm = config.llm();
            SwingValidator validator = new SwingValidator();
            EmailAssistant assistant = config.assistant(llm, validator);
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
