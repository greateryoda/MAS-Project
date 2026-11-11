// Le LLM n'a pas pu repondre (serveur eteint, modele non charge, ...).
// Exception non controlee : elle remonte jusqu'a Main, qui arrete le programme.
public class LlmException extends RuntimeException {
    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
