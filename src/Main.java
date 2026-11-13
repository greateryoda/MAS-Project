// Version terminal : traite un email d'exemple, validation dans la console.
public class Main {

    public static void main(String[] args) throws Exception {
        // adresse du serveur, modele, contexte, journal : config.json a la racine du projet
        AppConfig config = AppConfig.load("config.json");
        EmailAssistant assistant = config.assistant(config.llm(), new ConsoleValidator());

        String subject = "Subject: Concernant l'examen";
        String message = "Bonjour monsieur, pouvez-vous me dire quels sont les parties pouvant tomber dans l'examen? ";
        String senderAddress = "yunus-emre.dogan@etu.univ-cotedazur.fr";
        String date = "18/10/2026";
        int threadID = 3131;

        Email email = new Email(senderAddress, subject, message, date, threadID);

        Intention result;
        try {
            result = assistant.process(email);
        } catch (LlmException e) {
            System.err.println("\n" + e.getMessage());
            System.exit(1);
            return;
        }

        if (result.getStatus() == Intention.Status.ACHIEVED) {
            System.out.println("\nReply validated and saved in memory:");
            System.out.println(result.getFinalReply());
        } else {
            System.out.println("\nNo reply sent (" + result.getAttempts() + " drafts rejected).");
        }
    }
}
