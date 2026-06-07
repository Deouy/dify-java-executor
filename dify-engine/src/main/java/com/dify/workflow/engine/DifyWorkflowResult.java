package com.dify.workflow.engine;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of workflow execution.
 */
public final class DifyWorkflowResult {
    private final String status;
    private final Map<String, Object> outputs;
    private final List<NodeExecutionRecord> nodeExecutions;
    private final String errorMessage;

    public DifyWorkflowResult(String status, Map<String, Object> outputs,
                              List<NodeExecutionRecord> nodeExecutions, String errorMessage) {
        this.status = status;
        this.outputs = outputs;
        this.nodeExecutions = nodeExecutions;
        this.errorMessage = errorMessage;
    }

    public String status() { return status; }
    public Map<String, Object> outputs() { return outputs; }
    public List<NodeExecutionRecord> nodeExecutions() { return nodeExecutions; }
    public String errorMessage() { return errorMessage; }

    public String getStatus() { return status; }
    public Map<String, Object> getOutputs() { return outputs; }
    public List<NodeExecutionRecord> getNodeExecutions() { return nodeExecutions; }
    public String getErrorMessage() { return errorMessage; }

    public static DifyWorkflowResult succeeded(Map<String, Object> outputs, List<NodeExecutionRecord> records) {
        return new DifyWorkflowResult("succeeded", outputs, records, null);
    }

    public static DifyWorkflowResult failed(String errorMessage, List<NodeExecutionRecord> records) {
        return new DifyWorkflowResult("failed", null, records, errorMessage);
    }

    public boolean isSucceeded() { return "succeeded".equals(status); }
    public boolean isFailed() { return "failed".equals(status); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyWorkflowResult)) return false;
        DifyWorkflowResult that = (DifyWorkflowResult) o;
        return Objects.equals(status, that.status)
                && Objects.equals(outputs, that.outputs)
                && Objects.equals(nodeExecutions, that.nodeExecutions)
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() { return Objects.hash(status, outputs, nodeExecutions, errorMessage); }

    @Override
    public String toString() {
        return String.format("DifyWorkflowResult[status=%s, outputs=%s, errorMessage=%s]", status, outputs, errorMessage);
    }
}
