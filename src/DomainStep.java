import java.util.Set;

// On devine le role a partir du domaine de l'adresse.
public class DomainStep implements RoutingStep {
    private static final Set<String> ADMINISTRATIVE_MAILBOXES = Set.of(
            "scolarite", "secretariat", "administration", "admin", "rh", "drh",
            "inscriptions", "admissions", "registrar", "finance", "comptabilite");

    private final TemporaryContactBook contacts;

    public DomainStep(TemporaryContactBook contacts) {
        this.contacts = contacts;
    }

    public RoutingDecision decide(Email email) {
        // contact connu : l'adresse a deja vote via ContactStep, on ne la compte pas deux fois
        if (contacts.getRole(email.getSenderAddress()) != null) return null;

        String mailbox = getMailbox(email.getSenderAddress());
        if (ADMINISTRATIVE_MAILBOXES.contains(mailbox)) {
            return new RoutingDecision("ADMINISTRATION", 0.6, "administrative mailbox: " + mailbox);
        }

        String domain = getDomain(email.getSenderAddress());
        String role = switch (domain) {
            case "etu.univ.fr", "etu.univ-cotedazur.fr" -> "STUDENT";
            case "univ.fr", "univ-cotedazur.fr"         -> "TEACHER";
            default                                     -> null;
        };
        if (role == null) return null;
        return new RoutingDecision(role, 0.6, "domain: " + domain);
    }

    private String getMailbox(String address) {
        int at = address.lastIndexOf('@');
        if (at < 0) return "";
        return address.substring(0, at).trim().toLowerCase();
    }

    private String getDomain(String address) {
        int at = address.lastIndexOf('@');
        if (at < 0) return "";
        return address.substring(at + 1).trim().toLowerCase();
    }
}
