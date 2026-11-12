public enum WorkflowStage {
    RECEIVED("Email received", "User inbox"),
    ROUTING("Routing analysis", "RoutingAgent"),
    SIGNALS("Routing signals", "Thread, contact, keyword and domain steps"),
    SELECTION("Agent selection", "RoutingAgent"),
    TRANSMISSION("Transmission to the agent", "RoutingAgent"),
    CONTEXT("Context and memory", "Specialized agent"),
    GENERATION("Reply generation", "Local LLM"),
    CRITIC("Quality review", "CriticAgent"),
    PROPOSAL("Proposal returned to the user", "RoutingAgent"),
    VALIDATION("User validation", "User");

    private final String title;
    private final String actor;

    WorkflowStage(String title, String actor) {
        this.title = title;
        this.actor = actor;
    }

    public String getTitle() { return title; }
    public String getActor() { return actor; }
}
