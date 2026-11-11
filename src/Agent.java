import java.util.List;

public interface Agent{
    public String getName();
    public String handle(Email email);
    public String getRole();
    public String getDescription();
    public List<String> getKeywords();
    public boolean knowsThread(int threadID);
}