// L'expediteur est dans le carnet de contacts : on connait son role.
public class ContactStep implements RoutingStep {
    private final TemporaryContactBook contacts;

    public ContactStep(TemporaryContactBook contacts) {
        this.contacts = contacts;
    }

    public RoutingDecision decide(Email email) {
        String role = contacts.getRole(email.getSenderAddress());
        if (role == null) return null;
        return new RoutingDecision(role, 1.0, "known contact");
    }
}
