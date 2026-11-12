import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;

public class ConnectionStatus {
    public enum State { UNKNOWN, CHECKING, ONLINE, OFFLINE }

    private final LmStudioClient llm;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile State state = State.UNKNOWN;
    private volatile List<String> models = List.of();
    private volatile String error = "";

    public ConnectionStatus(LmStudioClient llm) {
        this.llm = llm;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void check() {
        if (state == State.CHECKING) return;
        state = State.CHECKING;
        fire();
        Thread worker = new Thread(() -> {
            try {
                models = llm.listModels();
                error = "";
                state = State.ONLINE;
            } catch (LlmException e) {
                models = List.of();
                error = e.getMessage();
                state = State.OFFLINE;
            }
            fire();
        }, "llm-status");
        worker.setDaemon(true);
        worker.start();
    }

    public State getState()          { return state; }
    public List<String> getModels()  { return models; }
    public String getError()         { return error; }
    public String getUrl()           { return llm.getUrl(); }
    public String getModel()         { return llm.getModel(); }

    public boolean isModelAvailable() {
        return models.contains(llm.getModel());
    }

    public String label() {
        return switch (state) {
            case ONLINE -> isModelAvailable() ? "Online" : "Online, model not found";
            case OFFLINE -> "Offline";
            case CHECKING -> "Checking...";
            default -> "Not checked";
        };
    }

    private void fire() {
        SwingUtilities.invokeLater(() -> listeners.forEach(Runnable::run));
    }
}
