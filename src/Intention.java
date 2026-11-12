import java.util.ArrayList;
import java.util.List;

// Intention BDI : l'agent s'engage a repondre a un email.
// Desir vise : une reponse validee par l'utilisateur (au plus maxRetries nouveaux essais apres un refus).
// Plan suivi :
//   1. rediger un brouillon (croyances + remarques de l'utilisateur)
//   2. le soumettre a l'utilisateur
//   3. accepte / modifie -> intention atteinte, la reponse est memorisee
//      refuse            -> retour a l'etape 1 avec la raison du refus
//      trop de refus     -> intention abandonnee, rien n'est memorise
public class Intention {
    public enum Status { ACTIVE, ACHIEVED, DROPPED, DISCARDED }

    private final Email email;
    private final int maxAttempts;
    private final List<String> rejections = new ArrayList<>(); // raisons des refus, dans l'ordre
    private int attempts = 0;
    private String draft;
    private String finalReply;
    private Status status = Status.ACTIVE;

    public Intention(Email email, int maxRetries) {
        this.email = email;
        this.maxAttempts = maxRetries + 1; // premier brouillon + essais apres refus
    }

    public void newDraft(String draft) {
        this.draft = draft;
        attempts++;
    }

    public void rejected(String reason) {
        rejections.add(reason);
        if (attempts >= maxAttempts) status = Status.DROPPED;
    }

    public void discarded() {
        status = Status.DISCARDED;
    }

    public void achieved(String reply) {
        finalReply = reply;
        status = Status.ACHIEVED;
    }

    public boolean isActive()          { return status == Status.ACTIVE; }
    public Status getStatus()          { return status; }
    public Email getEmail()            { return email; }
    public String getDraft()           { return draft; }
    public String getFinalReply()      { return finalReply; }
    public List<String> getRejections(){ return rejections; }
    public int getAttempts()           { return attempts; }
    public int getMaxAttempts()        { return maxAttempts; }
}
