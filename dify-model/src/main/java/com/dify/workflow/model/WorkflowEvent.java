package com.dify.workflow.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工作流执行事件数据结构。
 * 参考 Dify 的 GraphEngineEvent / QueueEvent 设计。
 */
public final class WorkflowEvent {

    private WorkflowEvent() {}

    /** 工作流开始事件 */
    public static final class Started {
        private final String workflowName;
        private final Map<String, Object> inputs;
        private final long startTime;

        public Started(String workflowName, Map<String, Object> inputs, long startTime) {
            this.workflowName = workflowName;
            this.inputs = inputs;
            this.startTime = startTime;
        }

        public String workflowName() { return workflowName; }
        public Map<String, Object> inputs() { return inputs; }
        public long startTime() { return startTime; }

        public String getWorkflowName() { return workflowName; }
        public Map<String, Object> getInputs() { return inputs; }
        public long getStartTime() { return startTime; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Started)) return false;
            Started that = (Started) o;
            return startTime == that.startTime
                    && Objects.equals(workflowName, that.workflowName)
                    && Objects.equals(inputs, that.inputs);
        }

        @Override
        public int hashCode() { return Objects.hash(workflowName, inputs, startTime); }

        @Override
        public String toString() {
            return String.format("Started[workflowName=%s, inputs=%s, startTime=%d]", workflowName, inputs, startTime);
        }
    }

    /** 工作流成功完成事件 */
    public static final class Succeeded {
        private final Map<String, Object> outputs;
        private final long endTime;
        private final long elapsedMs;

        public Succeeded(Map<String, Object> outputs, long endTime, long elapsedMs) {
            this.outputs = outputs;
            this.endTime = endTime;
            this.elapsedMs = elapsedMs;
        }

        public Map<String, Object> outputs() { return outputs; }
        public long endTime() { return endTime; }
        public long elapsedMs() { return elapsedMs; }

        public Map<String, Object> getOutputs() { return outputs; }
        public long getEndTime() { return endTime; }
        public long getElapsedMs() { return elapsedMs; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Succeeded)) return false;
            Succeeded that = (Succeeded) o;
            return endTime == that.endTime
                    && elapsedMs == that.elapsedMs
                    && Objects.equals(outputs, that.outputs);
        }

        @Override
        public int hashCode() { return Objects.hash(outputs, endTime, elapsedMs); }

        @Override
        public String toString() {
            return String.format("Succeeded[outputs=%s, endTime=%d, elapsedMs=%d]", outputs, endTime, elapsedMs);
        }
    }

    /** 工作流失败事件 */
    public static final class Failed {
        private final String error;
        private final int exceptionsCount;
        private final long endTime;
        private final long elapsedMs;

        public Failed(String error, int exceptionsCount, long endTime, long elapsedMs) {
            this.error = error;
            this.exceptionsCount = exceptionsCount;
            this.endTime = endTime;
            this.elapsedMs = elapsedMs;
        }

        public String error() { return error; }
        public int exceptionsCount() { return exceptionsCount; }
        public long endTime() { return endTime; }
        public long elapsedMs() { return elapsedMs; }

        public String getError() { return error; }
        public int getExceptionsCount() { return exceptionsCount; }
        public long getEndTime() { return endTime; }
        public long getElapsedMs() { return elapsedMs; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Failed)) return false;
            Failed that = (Failed) o;
            return exceptionsCount == that.exceptionsCount
                    && endTime == that.endTime
                    && elapsedMs == that.elapsedMs
                    && Objects.equals(error, that.error);
        }

        @Override
        public int hashCode() { return Objects.hash(error, exceptionsCount, endTime, elapsedMs); }

        @Override
        public String toString() {
            return String.format("Failed[error=%s, exceptionsCount=%d, endTime=%d, elapsedMs=%d]", error, exceptionsCount, endTime, elapsedMs);
        }
    }

    /** 节点开始执行事件 */
    public static final class NodeStarted {
        private final String nodeId;
        private final String nodeType;
        private final String nodeTitle;
        private final long startTime;
        private final String inIterationId;
        private final String inLoopId;

        public NodeStarted(String nodeId, String nodeType, String nodeTitle, long startTime,
                          String inIterationId, String inLoopId) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.nodeTitle = nodeTitle;
            this.startTime = startTime;
            this.inIterationId = inIterationId;
            this.inLoopId = inLoopId;
        }

        public String nodeId() { return nodeId; }
        public String nodeType() { return nodeType; }
        public String nodeTitle() { return nodeTitle; }
        public long startTime() { return startTime; }
        public String inIterationId() { return inIterationId; }
        public String inLoopId() { return inLoopId; }

        public String getNodeId() { return nodeId; }
        public String getNodeType() { return nodeType; }
        public String getNodeTitle() { return nodeTitle; }
        public long getStartTime() { return startTime; }
        public String getInIterationId() { return inIterationId; }
        public String getInLoopId() { return inLoopId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NodeStarted)) return false;
            NodeStarted that = (NodeStarted) o;
            return startTime == that.startTime
                    && Objects.equals(nodeId, that.nodeId)
                    && Objects.equals(nodeType, that.nodeType)
                    && Objects.equals(nodeTitle, that.nodeTitle)
                    && Objects.equals(inIterationId, that.inIterationId)
                    && Objects.equals(inLoopId, that.inLoopId);
        }

        @Override
        public int hashCode() { return Objects.hash(nodeId, nodeType, nodeTitle, startTime, inIterationId, inLoopId); }

        @Override
        public String toString() {
            return String.format("NodeStarted[nodeId=%s, nodeType=%s, nodeTitle=%s, startTime=%d, inIterationId=%s, inLoopId=%s]",
                    nodeId, nodeType, nodeTitle, startTime, inIterationId, inLoopId);
        }
    }

    /** 节点执行成功事件 */
    public static final class NodeSucceeded {
        private final String nodeId;
        private final String nodeType;
        private final String nodeTitle;
        private final Map<String, Object> inputs;
        private final Map<String, Object> outputs;
        private final Map<String, Object> processData;
        private final long startTime;
        private final long endTime;
        private final long elapsedMs;
        private final String inIterationId;
        private final String inLoopId;

        public NodeSucceeded(String nodeId, String nodeType, String nodeTitle,
                             Map<String, Object> inputs, Map<String, Object> outputs,
                             Map<String, Object> processData, long startTime, long endTime,
                             long elapsedMs, String inIterationId, String inLoopId) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.nodeTitle = nodeTitle;
            this.inputs = inputs;
            this.outputs = outputs;
            this.processData = processData;
            this.startTime = startTime;
            this.endTime = endTime;
            this.elapsedMs = elapsedMs;
            this.inIterationId = inIterationId;
            this.inLoopId = inLoopId;
        }

        public String nodeId() { return nodeId; }
        public String nodeType() { return nodeType; }
        public String nodeTitle() { return nodeTitle; }
        public Map<String, Object> inputs() { return inputs; }
        public Map<String, Object> outputs() { return outputs; }
        public Map<String, Object> processData() { return processData; }
        public long startTime() { return startTime; }
        public long endTime() { return endTime; }
        public long elapsedMs() { return elapsedMs; }
        public String inIterationId() { return inIterationId; }
        public String inLoopId() { return inLoopId; }

        public String getNodeId() { return nodeId; }
        public String getNodeType() { return nodeType; }
        public String getNodeTitle() { return nodeTitle; }
        public Map<String, Object> getInputs() { return inputs; }
        public Map<String, Object> getOutputs() { return outputs; }
        public Map<String, Object> getProcessData() { return processData; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public long getElapsedMs() { return elapsedMs; }
        public String getInIterationId() { return inIterationId; }
        public String getInLoopId() { return inLoopId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NodeSucceeded)) return false;
            NodeSucceeded that = (NodeSucceeded) o;
            return startTime == that.startTime
                    && endTime == that.endTime
                    && elapsedMs == that.elapsedMs
                    && Objects.equals(nodeId, that.nodeId)
                    && Objects.equals(nodeType, that.nodeType)
                    && Objects.equals(nodeTitle, that.nodeTitle)
                    && Objects.equals(inputs, that.inputs)
                    && Objects.equals(outputs, that.outputs)
                    && Objects.equals(processData, that.processData)
                    && Objects.equals(inIterationId, that.inIterationId)
                    && Objects.equals(inLoopId, that.inLoopId);
        }

        @Override
        public int hashCode() { return Objects.hash(nodeId, nodeType, nodeTitle, inputs, outputs, processData, startTime, endTime, elapsedMs, inIterationId, inLoopId); }

        @Override
        public String toString() {
            return String.format("NodeSucceeded[nodeId=%s, nodeType=%s, nodeTitle=%s, startTime=%d, endTime=%d, elapsedMs=%d, inIterationId=%s, inLoopId=%s]",
                    nodeId, nodeType, nodeTitle, startTime, endTime, elapsedMs, inIterationId, inLoopId);
        }
    }

    /** 节点执行失败事件 */
    public static final class NodeFailed {
        private final String nodeId;
        private final String nodeType;
        private final String nodeTitle;
        private final Map<String, Object> inputs;
        private final String error;
        private final long startTime;
        private final long endTime;
        private final long elapsedMs;
        private final String inIterationId;
        private final String inLoopId;

        public NodeFailed(String nodeId, String nodeType, String nodeTitle, Map<String, Object> inputs,
                          String error, long startTime, long endTime, long elapsedMs,
                          String inIterationId, String inLoopId) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.nodeTitle = nodeTitle;
            this.inputs = inputs;
            this.error = error;
            this.startTime = startTime;
            this.endTime = endTime;
            this.elapsedMs = elapsedMs;
            this.inIterationId = inIterationId;
            this.inLoopId = inLoopId;
        }

        public String nodeId() { return nodeId; }
        public String nodeType() { return nodeType; }
        public String nodeTitle() { return nodeTitle; }
        public Map<String, Object> inputs() { return inputs; }
        public String error() { return error; }
        public long startTime() { return startTime; }
        public long endTime() { return endTime; }
        public long elapsedMs() { return elapsedMs; }
        public String inIterationId() { return inIterationId; }
        public String inLoopId() { return inLoopId; }

        public String getNodeId() { return nodeId; }
        public String getNodeType() { return nodeType; }
        public String getNodeTitle() { return nodeTitle; }
        public Map<String, Object> getInputs() { return inputs; }
        public String getError() { return error; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public long getElapsedMs() { return elapsedMs; }
        public String getInIterationId() { return inIterationId; }
        public String getInLoopId() { return inLoopId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NodeFailed)) return false;
            NodeFailed that = (NodeFailed) o;
            return startTime == that.startTime
                    && endTime == that.endTime
                    && elapsedMs == that.elapsedMs
                    && Objects.equals(nodeId, that.nodeId)
                    && Objects.equals(nodeType, that.nodeType)
                    && Objects.equals(nodeTitle, that.nodeTitle)
                    && Objects.equals(inputs, that.inputs)
                    && Objects.equals(error, that.error)
                    && Objects.equals(inIterationId, that.inIterationId)
                    && Objects.equals(inLoopId, that.inLoopId);
        }

        @Override
        public int hashCode() { return Objects.hash(nodeId, nodeType, nodeTitle, inputs, error, startTime, endTime, elapsedMs, inIterationId, inLoopId); }

        @Override
        public String toString() {
            return String.format("NodeFailed[nodeId=%s, error=%s, elapsedMs=%d]", nodeId, error, elapsedMs);
        }
    }

    /** 迭代开始事件 */
    public static final class IterationStarted {
        private final String nodeId;
        private final String nodeType;
        private final String nodeTitle;
        private final long startTime;
        private final int totalSteps;

        public IterationStarted(String nodeId, String nodeType, String nodeTitle, long startTime, int totalSteps) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.nodeTitle = nodeTitle;
            this.startTime = startTime;
            this.totalSteps = totalSteps;
        }

        public String nodeId() { return nodeId; }
        public String nodeType() { return nodeType; }
        public String nodeTitle() { return nodeTitle; }
        public long startTime() { return startTime; }
        public int totalSteps() { return totalSteps; }

        public String getNodeId() { return nodeId; }
        public String getNodeType() { return nodeType; }
        public String getNodeTitle() { return nodeTitle; }
        public long getStartTime() { return startTime; }
        public int getTotalSteps() { return totalSteps; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IterationStarted)) return false;
            IterationStarted that = (IterationStarted) o;
            return startTime == that.startTime
                    && totalSteps == that.totalSteps
                    && Objects.equals(nodeId, that.nodeId)
                    && Objects.equals(nodeType, that.nodeType)
                    && Objects.equals(nodeTitle, that.nodeTitle);
        }

        @Override
        public int hashCode() { return Objects.hash(nodeId, nodeType, nodeTitle, startTime, totalSteps); }

        @Override
        public String toString() {
            return String.format("IterationStarted[nodeId=%s, totalSteps=%d]", nodeId, totalSteps);
        }
    }

    /** 迭代步骤完成事件 */
    public static final class IterationNext {
        private final String nodeId;
        private final int index;
        private final Map<String, Object> output;

        public IterationNext(String nodeId, int index, Map<String, Object> output) {
            this.nodeId = nodeId;
            this.index = index;
            this.output = output;
        }

        public String nodeId() { return nodeId; }
        public int index() { return index; }
        public Map<String, Object> output() { return output; }

        public String getNodeId() { return nodeId; }
        public int getIndex() { return index; }
        public Map<String, Object> getOutput() { return output; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IterationNext)) return false;
            IterationNext that = (IterationNext) o;
            return index == that.index
                    && Objects.equals(nodeId, that.nodeId)
                    && Objects.equals(output, that.output);
        }

        @Override
        public int hashCode() { return Objects.hash(nodeId, index, output); }

        @Override
        public String toString() {
            return String.format("IterationNext[nodeId=%s, index=%d]", nodeId, index);
        }
    }

    /** 迭代完成事件 */
    public static final class IterationCompleted {
        private final String nodeId;
        private final String nodeType;
        private final String nodeTitle;
        private final long startTime;
        private final long endTime;
        private final List<Map<String, Object>> outputs;
        private final int steps;
        private final String error;

        public IterationCompleted(String nodeId, String nodeType, String nodeTitle, long startTime,
                                  long endTime, List<Map<String, Object>> outputs, int steps, String error) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.nodeTitle = nodeTitle;
            this.startTime = startTime;
            this.endTime = endTime;
            this.outputs = outputs;
            this.steps = steps;
            this.error = error;
        }

        public String nodeId() { return nodeId; }
        public String nodeType() { return nodeType; }
        public String nodeTitle() { return nodeTitle; }
        public long startTime() { return startTime; }
        public long endTime() { return endTime; }
        public List<Map<String, Object>> outputs() { return outputs; }
        public int steps() { return steps; }
        public String error() { return error; }

        public String getNodeId() { return nodeId; }
        public String getNodeType() { return nodeType; }
        public String getNodeTitle() { return nodeTitle; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public List<Map<String, Object>> getOutputs() { return outputs; }
        public int getSteps() { return steps; }
        public String getError() { return error; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IterationCompleted)) return false;
            IterationCompleted that = (IterationCompleted) o;
            return startTime == that.startTime
                    && endTime == that.endTime
                    && steps == that.steps
                    && Objects.equals(nodeId, that.nodeId)
                    && Objects.equals(nodeType, that.nodeType)
                    && Objects.equals(nodeTitle, that.nodeTitle)
                    && Objects.equals(outputs, that.outputs)
                    && Objects.equals(error, that.error);
        }

        @Override
        public int hashCode() { return Objects.hash(nodeId, nodeType, nodeTitle, startTime, endTime, outputs, steps, error); }

        @Override
        public String toString() {
            return String.format("IterationCompleted[nodeId=%s, steps=%d, error=%s]", nodeId, steps, error);
        }
    }

    /** 循环开始事件 */
    public static final class LoopStarted {
        private final String nodeId;
        private final String nodeType;
        private final String nodeTitle;
        private final long startTime;
        private final int totalSteps;

        public LoopStarted(String nodeId, String nodeType, String nodeTitle, long startTime, int totalSteps) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.nodeTitle = nodeTitle;
            this.startTime = startTime;
            this.totalSteps = totalSteps;
        }

        public String nodeId() { return nodeId; }
        public String nodeType() { return nodeType; }
        public String nodeTitle() { return nodeTitle; }
        public long startTime() { return startTime; }
        public int totalSteps() { return totalSteps; }

        public String getNodeId() { return nodeId; }
        public String getNodeType() { return nodeType; }
        public String getNodeTitle() { return nodeTitle; }
        public long getStartTime() { return startTime; }
        public int getTotalSteps() { return totalSteps; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LoopStarted)) return false;
            LoopStarted that = (LoopStarted) o;
            return startTime == that.startTime
                    && totalSteps == that.totalSteps
                    && Objects.equals(nodeId, that.nodeId)
                    && Objects.equals(nodeType, that.nodeType)
                    && Objects.equals(nodeTitle, that.nodeTitle);
        }

        @Override
        public int hashCode() { return Objects.hash(nodeId, nodeType, nodeTitle, startTime, totalSteps); }

        @Override
        public String toString() {
            return String.format("LoopStarted[nodeId=%s, totalSteps=%d]", nodeId, totalSteps);
        }
    }

    /** 循环步骤完成事件 */
    public static final class LoopNext {
        private final String nodeId;
        private final int index;
        private final Map<String, Object> output;

        public LoopNext(String nodeId, int index, Map<String, Object> output) {
            this.nodeId = nodeId;
            this.index = index;
            this.output = output;
        }

        public String nodeId() { return nodeId; }
        public int index() { return index; }
        public Map<String, Object> output() { return output; }

        public String getNodeId() { return nodeId; }
        public int getIndex() { return index; }
        public Map<String, Object> getOutput() { return output; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LoopNext)) return false;
            LoopNext that = (LoopNext) o;
            return index == that.index
                    && Objects.equals(nodeId, that.nodeId)
                    && Objects.equals(output, that.output);
        }

        @Override
        public int hashCode() { return Objects.hash(nodeId, index, output); }

        @Override
        public String toString() {
            return String.format("LoopNext[nodeId=%s, index=%d]", nodeId, index);
        }
    }

    /** 循环完成事件 */
    public static final class LoopCompleted {
        private final String nodeId;
        private final String nodeType;
        private final String nodeTitle;
        private final long startTime;
        private final long endTime;
        private final Map<String, Object> outputs;
        private final int steps;
        private final String error;

        public LoopCompleted(String nodeId, String nodeType, String nodeTitle, long startTime,
                             long endTime, Map<String, Object> outputs, int steps, String error) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.nodeTitle = nodeTitle;
            this.startTime = startTime;
            this.endTime = endTime;
            this.outputs = outputs;
            this.steps = steps;
            this.error = error;
        }

        public String nodeId() { return nodeId; }
        public String nodeType() { return nodeType; }
        public String nodeTitle() { return nodeTitle; }
        public long startTime() { return startTime; }
        public long endTime() { return endTime; }
        public Map<String, Object> outputs() { return outputs; }
        public int steps() { return steps; }
        public String error() { return error; }

        public String getNodeId() { return nodeId; }
        public String getNodeType() { return nodeType; }
        public String getNodeTitle() { return nodeTitle; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public Map<String, Object> getOutputs() { return outputs; }
        public int getSteps() { return steps; }
        public String getError() { return error; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LoopCompleted)) return false;
            LoopCompleted that = (LoopCompleted) o;
            return startTime == that.startTime
                    && endTime == that.endTime
                    && steps == that.steps
                    && Objects.equals(nodeId, that.nodeId)
                    && Objects.equals(nodeType, that.nodeType)
                    && Objects.equals(nodeTitle, that.nodeTitle)
                    && Objects.equals(outputs, that.outputs)
                    && Objects.equals(error, that.error);
        }

        @Override
        public int hashCode() { return Objects.hash(nodeId, nodeType, nodeTitle, startTime, endTime, outputs, steps, error); }

        @Override
        public String toString() {
            return String.format("LoopCompleted[nodeId=%s, steps=%d, error=%s]", nodeId, steps, error);
        }
    }

    /**
     * 流式输出 chunk 事件 — LLM/Agent 节点每收到一个 token 触发一次。
     *
     * <p>对齐 Dify Python 端 WorkflowEvent.Chunk (api/core/workflow/workflow_entry.py)。
     * Java 端通过 {@link WorkflowEventListener#onChunk(WorkflowEvent.Chunk)} 默认空回调
     * 透传给 listener,实现向后兼容。</p>
     *
     * <p>语义:
     * <ul>
     *   <li>LLM/Agent 节点每收到 SSE data chunk 触发一次 onChunk</li>
     *   <li>delta 是单次增量文本(可能为空字符串但 index 自增)</li>
     *   <li>stream 结束(收到 [DONE] 或 finishReason)时,发最后一条 delta=null,index 递增的 chunk 作为收尾</li>
     * </ul>
     */
    public static final class Chunk {
        private final String nodeId;
        private final int messageId;
        private final String delta;
        private final int index;

        public Chunk(String nodeId, int messageId, String delta, int index) {
            this.nodeId = nodeId;
            this.messageId = messageId;
            this.delta = delta;
            this.index = index;
        }

        public String nodeId() { return nodeId; }
        public int messageId() { return messageId; }
        public String delta() { return delta; }
        public int index() { return index; }

        public String getNodeId() { return nodeId; }
        public int getMessageId() { return messageId; }
        public String getDelta() { return delta; }
        public int getIndex() { return index; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Chunk)) return false;
            Chunk that = (Chunk) o;
            return messageId == that.messageId
                    && index == that.index
                    && Objects.equals(nodeId, that.nodeId)
                    && Objects.equals(delta, that.delta);
        }

        @Override
        public int hashCode() { return Objects.hash(nodeId, messageId, delta, index); }

        @Override
        public String toString() {
            return String.format("Chunk[nodeId=%s, messageId=%d, index=%d, delta=%s]",
                    nodeId, messageId, index, delta);
        }
    }
}
