// Decorator : compte les appels au LLM, la taille des prompts envoyes et le temps passe.
// Ne change pas le comportement du LLM decore ; sert aux experiences (cout de chaque configuration).
public class MeteredLLM implements LLM {
    private final LLM inner;
    private int calls;
    private long promptChars;
    private long answerChars;
    private long millis;

    public MeteredLLM(LLM inner) {
        this.inner = inner;
    }

    public synchronized String ask(String systemPrompt, String message) {
        long start = System.nanoTime();
        try {
            String answer = inner.ask(systemPrompt, message);
            answerChars += answer == null ? 0 : answer.length();
            return answer;
        } finally {
            calls++;
            promptChars += systemPrompt.length() + message.length();
            millis += (System.nanoTime() - start) / 1_000_000;
        }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(calls, promptChars, answerChars, millis);
    }

    public record Snapshot(int calls, long promptChars, long answerChars, long millis) {
        public Snapshot minus(Snapshot before) {
            return new Snapshot(calls - before.calls, promptChars - before.promptChars,
                                answerChars - before.answerChars, millis - before.millis);
        }
    }
}
