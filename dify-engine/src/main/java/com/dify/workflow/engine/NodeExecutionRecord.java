package com.dify.workflow.engine;

import java.util.Map;
import java.util.Objects;

/**
 * Record of a node execution.
 */
public final class NodeExecutionRecord {
    private final String nodeId;
    private final String nodeType;
    private final String status;
    private final Map<String, Object> inputs;
    private final Map<String, Object> outputs;
    private final Long startTime;
    private final Long endTime;
    private final String errorMessage;

    public NodeExecutionRecord(String nodeId, String nodeType, String status,
                              Map<String, Object> inputs, Map<String, Object> outputs,
                              Long startTime, Long endTime, String errorMessage) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.status = status;
        this.inputs = inputs;
        this.outputs = outputs;
        this.startTime = startTime;
        this.endTime = endTime;
        this.errorMessage = errorMessage;
    }

    public String nodeId() { return nodeId; }
    public String nodeType() { return nodeType; }
    public String status() { return status; }
    public Map<String, Object> inputs() { return inputs; }
    public Map<String, Object> outputs() { return outputs; }
    public Long startTime() { return startTime; }
    public Long endTime() { return endTime; }
    public String errorMessage() { return errorMessage; }

    public String getNodeId() { return nodeId; }
    public String getNodeType() { return nodeType; }
    public String getStatus() { return status; }
    public Map<String, Object> getInputs() { return inputs; }
    public Map<String, Object> getOutputs() { return outputs; }
    public Long getStartTime() { return startTime; }
    public Long getEndTime() { return endTime; }
    public String getErrorMessage() { return errorMessage; }

    public long getDurationMs() {
        if (startTime == null || endTime == null) {
            return 0;
        }
        return endTime - startTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeExecutionRecord)) return false;
        NodeExecutionRecord that = (NodeExecutionRecord) o;
        return Objects.equals(nodeId, that.nodeId)
                && Objects.equals(nodeType, that.nodeType)
                && Objects.equals(status, that.status)
                && Objects.equals(inputs, that.inputs)
                && Objects.equals(outputs, that.outputs)
                && Objects.equals(startTime, that.startTime)
                && Objects.equals(endTime, that.endTime)
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, nodeType, status, inputs, outputs, startTime, endTime, errorMessage);
    }

    @Override
    public String toString() {
        return String.format("NodeExecutionRecord[nodeId=%s, nodeType=%s, status=%s, errorMessage=%s]",
                nodeId, nodeType, status, errorMessage);
    }
}
