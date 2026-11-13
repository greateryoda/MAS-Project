import java.util.List;

public class RoutingDecision {
    // agent contributeur : il ne repond qu'a une partie de l'email, l'agent choisi (role) fusionne
    public record Collaborator(String role, String part) {}

    private final String role;
    private final double confidence;
    private final String reason;
    private final List<Collaborator> collaborators;

    public RoutingDecision(String role, double confidence, String reason) {
        this(role, confidence, reason, List.of());
    }

    public RoutingDecision(String role, double confidence, String reason, List<Collaborator> collaborators) {
        this.role = role;
        this.confidence = confidence;
        this.reason = reason;
        this.collaborators = List.copyOf(collaborators);
    }

    public String getRole(){
        return role;
    }

    public double getConfidence(){
        return confidence;
    }

    public String getReason(){
        return reason;
    }

    // vide : un seul agent repond (cas general)
    public List<Collaborator> getCollaborators(){
        return collaborators;
    }

    public boolean isCollaborative(){
        return !collaborators.isEmpty();
    }
}
