import java.util.List;

public interface Agent{
    public String getName();
    public String getRole();
    public String getDescription();
    public List<String> getKeywords();
    public boolean knowsThread(int threadID);

    // desir -> intention : l'agent s'engage a repondre a cet email
    public Intention adopt(Email email);
    // etape du plan : rediger un brouillon, sans toucher a la memoire
    public String draft(Intention intention);
    // etape du plan : revoir l'intention selon la reponse de l'utilisateur
    public void reconsider(Intention intention, Feedback feedback);
}
