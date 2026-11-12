import java.util.Scanner;

// L'utilisateur valide les brouillons dans le terminal.
public class ConsoleValidator implements Validator {
    private final Scanner in = new Scanner(System.in);

    public Feedback review(Proposal p) {
        System.out.println();
        if (p.getAttempt() == 1) {
            System.out.println("Router chose: " + p.getAgent().getName()
                    + " (" + p.getDecision().getReason()
                    + ", confidence " + String.format("%.2f", p.getDecision().getConfidence()) + ")");
        }
        System.out.println("----- Draft " + p.getAttempt() + "/" + p.getMaxAttempts() + " -----");
        System.out.println(p.getDraft());
        System.out.println("-------------------");
        if (p.getCritique() != null) {
            System.out.println("CriticAgent: " + p.getCritique().summary());
            for (String issue : p.getCritique().getIssues()) System.out.println("  - " + issue);
        }

        while (true) {
            String choice = ask("[a]ccept / [m]odify / [r]eject / [d]iscard: ").trim().toLowerCase();
            switch (choice) {
                case "a": return Feedback.accept();
                case "m": return Feedback.modify(readBlock());
                case "r": return Feedback.reject(ask("Reason: ").trim());
                case "d": return Feedback.discard();
                default:  System.out.println("Please type a, m, r or d.");
            }
        }
    }

    public String writeReply(Email email) {
        System.out.println();
        System.out.println("All drafts were rejected. Write the reply to " + email.getSenderAddress()
                + " yourself, or leave it empty to send nothing.");
        String reply = readBlock();
        return reply.isEmpty() ? null : reply;
    }

    // reponse corrigee sur plusieurs lignes, terminee par une ligne contenant seulement "."
    private String readBlock() {
        System.out.println("Type the corrected reply, end with a line containing only \".\"");
        StringBuilder sb = new StringBuilder();
        while (true) {
            String line = ask("");
            if (line.equals(".")) break;
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    private String ask(String prompt) {
        System.out.print(prompt);
        if (!in.hasNextLine()) throw new IllegalStateException("No user input available");
        return in.nextLine();
    }
}
