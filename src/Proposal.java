public class Proposal {
    private final Email email;
    private final Agent agent;
    private final String draft;

    public Proposal(Email email, Agent agent, String draft) {
        this.email = email;
        this.agent = agent;
        this.draft = draft;
    }

    public Email getEmail(){
        return email;
    }

    public Agent getAgent(){
        return agent;
    }

    public String getDraft(){
        return draft;
    }
}
