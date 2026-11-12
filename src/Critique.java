import java.util.List;

public class Critique {
    private final boolean available;
    private final int score;
    private final boolean approved;
    private final List<String> issues;
    private final String error;

    private Critique(boolean available, int score, boolean approved, List<String> issues, String error) {
        this.available = available;
        this.score = score;
        this.approved = approved;
        this.issues = issues;
        this.error = error;
    }

    public static Critique of(int score, boolean approved, List<String> issues) {
        return new Critique(true, Math.max(0, Math.min(10, score)), approved, List.copyOf(issues), null);
    }

    public static Critique unavailable(String error) {
        return new Critique(false, 0, false, List.of(), error);
    }

    public boolean isAvailable()   { return available; }
    public int getScore()          { return score; }
    public boolean isApproved()    { return approved; }
    public List<String> getIssues(){ return issues; }
    public String getError()       { return error; }

    public String summary() {
        if (!available) return "Review unavailable: " + error;
        String verdict = approved ? "approved" : "revision suggested";
        return "Score " + score + "/10, " + verdict
                + (issues.isEmpty() ? "" : ", " + issues.size() + " remark(s)");
    }

    public String remarksAsText() {
        return String.join("\n", issues);
    }
}
