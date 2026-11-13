// Collaboration entre agents (email qui concerne plusieurs agents) :
// note redigee par un agent contributeur sur SA partie de l'email, avec ses propres regles
// et son propre contexte. L'agent principal l'integre ensuite dans une reponse unique.
// error != null : le contributeur n'a pas pu repondre (l'agent principal redige alors seul).
public record Contribution(String role, String agentName, String part, String text, long millis, String error) {

    public static Contribution of(Agent agent, String part, String text, long millis) {
        return new Contribution(agent.getRole().toUpperCase(), agent.getName(), part, text, millis, null);
    }

    public static Contribution failed(Agent agent, String part, String error, long millis) {
        return new Contribution(agent.getRole().toUpperCase(), agent.getName(), part, null, millis, error);
    }

    public boolean available() {
        return error == null && text != null && !text.isBlank();
    }

    public String summary() {
        return available() ? agentName + " answered its part (" + part + ")"
                           : agentName + " could not contribute: " + error;
    }
}
