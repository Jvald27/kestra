package io.kestra.core.runners;

import io.kestra.core.models.flows.State;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubflowExecutionEnd {
    private String executionId;
    private String taskRunId;
    private String taskId;
    private State.Type state;

    public String toStringState() {
        return "SubflowExecutionEnd(" +
            "executionId=" + this.getExecutionId() +
            ", taskId=" + this.getTaskId() +
            ", taskRunId=" + this.getTaskRunId() +
            ", state=" + this.getState().toString() +
            ")";
    }
}
