package com.dify.workflow.nodes.agent;

import com.dify.workflow.model.ChatRequest;
import com.dify.workflow.model.ConversationHistoryStore;
import com.dify.workflow.model.LlmCallResult;
import com.dify.workflow.model.LlmService;
import com.dify.workflow.model.McpServerConfig;
import com.dify.workflow.model.NodeExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 执行上下文。
 * 包含执行 Agent 所需的所有信息和工具。
 */
public class AgentExecutionContext {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionContext.class);

    private final NodeExecutionContext workflowContext;
    private final String modelProvider;
    private final String modelName;
    private final Map<String, McpServerConfig> mcpConfigs;
    private final List<ToolDefinition> tools = new ArrayList<>();
    private int maxIterations = 10;
    private final ConversationHistoryStore historyStore;
    private final String conversationId;

    private AgentExecutionContext(Builder builder) {
        this.workflowContext = builder.workflowContext;
        this.modelProvider = builder.modelProvider;
        this.modelName = builder.modelName;
        this.mcpConfigs = builder.mcpConfigs;
        this.maxIterations = builder.maxIterations > 0 ? builder.maxIterations : 10;
        this.historyStore = builder.historyStore;
        this.conversationId = builder.conversationId;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 执行工具调用。
     * 根据工具类型分发到不同的执行器。
     */
    public Object executeTool(String toolName, Map<String, Object> args) {
        log.debug("Agent executing tool: {} with args: {}", toolName, args);

        // 查找工具定义
        Optional<ToolDefinition> toolOpt = tools.stream()
                .filter(t -> t.getName().equals(toolName))
                .findFirst();

        if (!toolOpt.isPresent()) {
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }

        ToolDefinition tool = toolOpt.get();
        try {
            return tool.getExecutor().execute(args);
        } catch (Exception e) {
            log.error("Tool execution failed: {} - {}", toolName, e.getMessage());
            return "Tool execution failed: " + e.getMessage();
        }
    }

    /**
     * 调用 LLM(流式,emit chunk 给 listener)。
     *
     * <p>关键修复:Agent 节点内的 LLM 调用也走 callStream 路径,每收到 SSE delta
     * 通过 context.emitChunk 转发给 WorkflowEventListener.onChunk。
     * 与 Dify Python 端 agent_node.py:121 strategy.invoke() 返回 generator 一致。</p>
     *
     * <p>实现:第一次同步 call 拿 token usage 等元数据(可走 provider 的 HTTP 调用
     * 拿到完整响应),第二次 stream call 累积 content 并 emit chunk。
     * 用 content 来自 stream 累积(实时),元数据来自 sync call 兜底,
     * 整体调用次数 2n 但保证 listener 收到所有 token。</p>
     */
    public LlmCallResult callLlm(String modelProvider, String modelName, ChatRequest request) {
        LlmService llmService = workflowContext.getLlmService();
        if (llmService == null) {
            throw new IllegalStateException("LLM service not configured");
        }
        // 第一次同步 call 拿 token usage / toolCalls / assistantMessages 等元数据兜底
        LlmCallResult syncResult = llmService.call(modelProvider, modelName, request);

        // 第二次流式 call 累积 content + emit chunk 给 listener
        StringBuilder accumulated = new StringBuilder();
        try {
            llmService.callStream(modelProvider, modelName, request, delta -> {
                if (delta.content() != null) {
                    accumulated.append(delta.content());
                    // emit chunk:把 LLM 当前节点的 id 作为事件来源,listener 知道哪个节点产生这个 token
                    if (workflowContext.getCurrentNodeId() != null) {
                        workflowContext.emitChunk(workflowContext.getCurrentNodeId(), delta.content());
                    }
                }
            });
        } catch (Exception e) {
            // 流式失败 fallback:用 sync 拿到的 content
            log.warn("Agent LLM stream call failed, using sync fallback: {}", e.getMessage());
            return syncResult;
        }

        // 构造 LlmCallResult:content 走 stream 累积,其他元数据走 sync 兜底
        return new LlmCallResult(
                accumulated.length() > 0 ? accumulated.toString() : syncResult.content(),
                syncResult.model(),
                syncResult.promptTokens(),
                syncResult.completionTokens(),
                syncResult.totalTokens(),
                syncResult.finishReason(),
                true,
                null,
                syncResult.toolCalls(),
                syncResult.assistantMessages());
    }

    /**
     * 添加工具到上下文。
     */
    public void addTool(ToolDefinition tool) {
        this.tools.add(tool);
    }

    // ========== getters ==========

    public NodeExecutionContext getWorkflowContext() {
        return workflowContext;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public String getModelName() {
        return modelName;
    }

    public Map<String, McpServerConfig> getMcpConfigs() {
        return mcpConfigs;
    }

    public List<ToolDefinition> getTools() {
        return tools;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public ConversationHistoryStore getHistoryStore() {
        return historyStore;
    }

    public String getConversationId() {
        return conversationId;
    }

    // ========== Builder ==========

    public static class Builder {
        private NodeExecutionContext workflowContext;
        private String modelProvider;
        private String modelName;
        private Map<String, McpServerConfig> mcpConfigs;
        private int maxIterations = 10;
        private ConversationHistoryStore historyStore;
        private String conversationId;

        public Builder workflowContext(NodeExecutionContext context) {
            this.workflowContext = context;
            return this;
        }

        public Builder modelProvider(String provider) {
            this.modelProvider = provider;
            return this;
        }

        public Builder modelName(String name) {
            this.modelName = name;
            return this;
        }

        public Builder mcpConfigs(Map<String, McpServerConfig> configs) {
            this.mcpConfigs = configs;
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public Builder historyStore(ConversationHistoryStore store) {
            this.historyStore = store;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public AgentExecutionContext build() {
            return new AgentExecutionContext(this);
        }
    }
}
