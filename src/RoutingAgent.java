import java.util.Map;

public class RoutingAgent{
    private Map<String, Agent> agents;
    private TemporaryContactBook contacts;

    public RoutingAgent(Map<String, Agent> agents, TemporaryContactBook contacts){
        this.agents = agents;
        this.contacts = contacts;
    }

    public Proposal RouteMail(Email email){
        Agent agent = selectAgent(email);
        String draft = agent.handle(email);
        return new Proposal(email, agent, draft);
    }

    private Agent selectAgent(Email email){
        String senderAddress = email.getSenderAddress();

        Agent agent = null;
        String role = contacts.getRole(senderAddress);
        if (role != null) {
            agent = agents.get(role);
        }
        if (agent == null) {
            agent = agents.get(roleFromDomain(senderAddress));
        }
        if (agent == null) {
            throw new IllegalStateException("No agent found for sender: " + senderAddress);
        }
        return agent;
    }

    private String getDomain(String address) {
        int at = address.lastIndexOf('@');
        if (at < 0) return "";
        return address.substring(at + 1).trim().toLowerCase();
    }

    private String roleFromDomain(String address){
        return switch (getDomain(address)) {
            case "etu.univ.fr", "etu.univ-cotedazur.fr" -> "STUDENT";
            case "univ.fr", "univ-cotedazur.fr"         -> "TEACHER";
            default                                     -> "UNKNOWN";
        };
    }
}