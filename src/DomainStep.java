// On devine le role a partir du domaine de l'adresse.
public class DomainStep implements RoutingStep {
    private final TemporaryContactBook contacts;

    public DomainStep(TemporaryContactBook contacts) {
        this.contacts = contacts;
    }

    public RoutingDecision decide(Email email) {
        // contact connu : l'adresse a deja vote via ContactStep, on ne la compte pas deux fois
        if (contacts.getRole(email.getSenderAddress()) != null) return null;

        String domain = getDomain(email.getSenderAddress());
        String role = switch (domain) {
            case "etu.univ.fr", "etu.univ-cotedazur.fr" -> "STUDENT";
            case "univ.fr", "univ-cotedazur.fr"         -> "TEACHER";
            default                                     -> null;
        };
        if (role == null) return null;
        return new RoutingDecision(role, 0.6, "domain: " + domain);
    }

    private String getDomain(String address) {
        int at = address.lastIndexOf('@');
        if (at < 0) return "";
        return address.substring(at + 1).trim().toLowerCase();
    }
}
