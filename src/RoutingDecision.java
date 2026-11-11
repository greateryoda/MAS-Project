public class RoutingDecision {
    private final String role;
    private final double confidence;
    private final String reason;

    public RoutingDecision(String role, double confidence, String reason) {
        this.role = role;
        this.confidence = confidence;
        this.reason = reason;
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
}
