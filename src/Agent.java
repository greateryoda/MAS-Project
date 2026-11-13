import java.io.IOException;
import java.util.List;

public interface Agent{
    public String getName();
    public String getRole();
    public String getDescription();
    public List<String> getKeywords();
    public String getSystemPrompt();
    public boolean knowsThread(int threadID);

    public List<Belief> recall(int threadID);
    // contexte effectivement transmis au LLM pour cet email (selection + derniers messages du fil)
    public ContextSelection selectContext(Email email);
    public void reload() throws IOException;

    // desir -> intention : l'agent s'engage a repondre a cet email
    public Intention adopt(Email email);
    // etape du plan : rediger un brouillon, sans toucher a la memoire
    public String draft(Intention intention);
    // etape du plan : revoir l'intention selon la reponse de l'utilisateur
    public void reconsider(Intention intention, Feedback feedback);
}
