import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ActivityLog {
    public enum Outcome { ACCEPTED, EDITED, DISCARDED, NO_REPLY, ERROR }

    public record Entry(String time, String sender, String subject, String agent, Outcome outcome, int drafts) {}

    private final List<Entry> entries = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void record(Email email, String agent, Outcome outcome, int drafts) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        entries.add(0, new Entry(time, email.getSenderAddress(), email.getSubject(), agent, outcome, drafts));
        listeners.forEach(Runnable::run);
    }

    public static Outcome outcomeOf(Intention intention) {
        return switch (intention.getStatus()) {
            case ACHIEVED -> intention.getFinalReply().equals(intention.getDraft()) ? Outcome.ACCEPTED : Outcome.EDITED;
            case DISCARDED -> Outcome.DISCARDED;
            default -> Outcome.NO_REPLY;
        };
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public long count(Outcome... outcomes) {
        return entries.stream().filter(e -> List.of(outcomes).contains(e.outcome())).count();
    }

    public int total() {
        return entries.size();
    }

    public int draftsGenerated() {
        return entries.stream().mapToInt(Entry::drafts).sum();
    }
}
