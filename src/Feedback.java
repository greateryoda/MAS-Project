// Reponse de l'utilisateur a une proposition de brouillon.
public class Feedback {
    public enum Type { ACCEPT, MODIFY, REJECT }

    private final Type type;
    private final String text; // MODIFY : reponse corrigee, REJECT : raison du refus, ACCEPT : null

    private Feedback(Type type, String text) {
        this.type = type;
        this.text = text;
    }

    public static Feedback accept()              { return new Feedback(Type.ACCEPT, null); }
    public static Feedback modify(String reply)  { return new Feedback(Type.MODIFY, reply); }
    public static Feedback reject(String reason) { return new Feedback(Type.REJECT, reason); }

    public Type getType()   { return type; }
    public String getText() { return text; }
}
