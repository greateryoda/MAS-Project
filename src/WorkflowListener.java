import java.util.List;
import java.util.Map;

public interface WorkflowListener {
    void stageChanged(WorkflowStage stage, StageStatus status, String detail);

    void agentSelected(Agent agent, RoutingDecision decision, Map<String, Double> scores,
                       List<String> signals, boolean arbiterConsulted);
}
