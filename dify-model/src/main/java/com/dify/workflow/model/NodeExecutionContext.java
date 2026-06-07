package com.dify.workflow.model;

import java.util.Map;

/**
 * Interface for node execution context.
 * Implemented by DifyWorkflowContext in dify-engine.
 */
public interface NodeExecutionContext {

    /**
     * Get the variable pool for storing node variables.
     */
    VariablePool getVariablePool();

    /**
     * Get a variable value by node ID and variable name.
     */
    Object getVariable(String nodeId, String variableName);

    /**
     * Set a variable value.
     */
    void setVariable(String nodeId, String variableName, Object value);

    /**
     * Set a conversation variable. Used by VariableAssignerNode when its
     * variable_selector is ["conversation", X]. Must write to the conversation
     * variable map (the same place {{#conversation.X#}} reads from), NOT to
     * the generic node-output bucket.
     *
     * @param field Conversation variable name (e.g., "allcount")
     * @param value Variable value
     */
    default void setConversationVariable(String field, Object value) {
        // Backward-compatible default: route through the pool. Implementations
        // should override to use the conversation channel.
        getVariablePool().setConversation(field, value);
    }

    /**
     * Get the LLM service for making LLM calls.
     */
    LlmService getLlmService();

    /**
     * Resolve variables in a template string.
     */
    String resolveVariables(String template);

    /**
     * Set node status.
     */
    void setNodeStatus(String nodeId, String status);

    /**
     * Get node status.
     */
    String getNodeStatus(String nodeId);

    /**
     * Mark execution as failed.
     */
    void setFailed(String errorMessage);

    /**
     * Check if execution has failed.
     */
    boolean hasFailed();

    /**
     * Get error message if failed.
     */
    String getErrorMessage();

    /**
     * Set current node ID.
     */
    void setCurrentNodeId(String nodeId);

    /**
     * Get current node ID.
     */
    String getCurrentNodeId();

    /**
     * Get workflow inputs.
     */
    java.util.Map<String, Object> getInputs();

    /**
     * Get workflow outputs.
     */
    java.util.Map<String, Object> getOutputs();

    /**
     * Set a workflow output.
     */
    void setOutput(String key, Object value);

    /**
     * Execute a sub-workflow and return its outputs.
     * Used by ToolNode to execute nested workflows.
     *
     * @param subWorkflowYaml The YAML definition of the sub-workflow
     * @param inputs Input parameters for the sub-workflow
     * @return Map of output variables from the sub-workflow
     */
    java.util.Map<String, Object> executeSubWorkflow(String subWorkflowYaml, java.util.Map<String, Object> inputs);

    /**
     * 根据工具名（workflow name）查找并执行子工作流。
     * 用于 Agent 节点的 workflow 工具调用。
     *
     * @param workflowName 子工作流的名称（注册表中的 key）
     * @param inputs 输入参数
     * @return 子工作流的输出变量
     */
    default java.util.Map<String, Object> executeNamedSubWorkflow(String workflowName, java.util.Map<String, Object> inputs) {
        throw new UnsupportedOperationException("executeNamedSubWorkflow not implemented");
    }

    /**
     * 获取聊天历史存储（用于 LLM 节点的 conversation memory）。
     * @return 历史存储，可能为 null
     */
    default ConversationHistoryStore getHistoryStore() {
        return null;
    }

    default String getConversationId() {
        return null;
    }

    /**
     * 执行子图（迭代/循环体内的节点）。
     * 参考 Dify 的 Graph.init() + GraphEngine 子图执行模式。
     *
     * @param rootNodeId 子图入口节点ID（iteration-start / loop-start）
     * @param containerNodeId 容器节点ID（iteration / loop 节点）
     */
    void executeSubGraph(String rootNodeId, String containerNodeId);

    /**
     * 获取 MCP Server 配置（用于 Agent 节点的 MCP 策略）。
     * @return MCP 配置 Map，可能为空
     */
    default Map<String, McpServerConfig> getMcpConfigs() {
        return java.util.Collections.emptyMap();
    }

    /**
     * 发射 LLM 流式输出 chunk 事件。
     * 由 LLM/Agent 节点在收到每个 SSE token 时调用,转发给 WorkflowEventListener.onChunk。
     * 默认空实现,既有实现不需改动即可继续工作。
     *
     * @param nodeId 触发流式输出的节点 ID
     * @param delta 单次增量文本(token 或文本片段)
     */
    default void emitChunk(String nodeId, String delta) {
        // 默认空实现:listener 不关心流式 chunk
    }
}
