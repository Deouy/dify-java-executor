package com.dify.workflow.engine;

import com.dify.workflow.model.DifyDslModel;
import com.dify.workflow.model.DifyGraph;
import com.dify.workflow.model.DifyNode;
import com.dify.workflow.model.Java8Compat;
import com.dify.workflow.model.LlmCallResult;
import com.dify.workflow.model.LlmService;
import com.dify.workflow.model.McpServerConfig;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.VariablePool;
import com.dify.workflow.engine.llm.LlmProviderFactory;
import com.dify.workflow.parser.DifyYamlParser;
import com.dify.workflow.parser.VariableResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Workflow execution context.
 * Maintains state during workflow execution.
 */
public class DifyWorkflowContext implements NodeExecutionContext {

    private static final Logger log = LoggerFactory.getLogger(DifyWorkflowContext.class);

    private final DifyDslModel dslModel;
    private final DifyVariablePool variablePool;
    private final Map<String, NodeStatus> nodeStatuses = new ConcurrentHashMap<>();
    private final Map<String, Object> inputs;
    private final Map<String, Object> outputs = new HashMap<>();
    private final LlmProviderFactory llmProviderFactory;
    private final VariableResolver variableResolver;
    private String errorMessage;
    private boolean failed = false;
    private String currentNodeId;

    /** 子图执行运行器，由 DifyWorkflowExecutor 注入 */
    @FunctionalInterface
    interface SubGraphRunner {
        void run(String rootNodeId, String containerNodeId, DifyWorkflowContext context);
    }
    private SubGraphRunner subGraphRunner;
    private com.dify.workflow.model.ConversationHistoryStore historyStore;
    private String conversationId;
    private Map<String, String> subWorkflows = Java8Compat.mapOf();
    private Map<String, McpServerConfig> mcpConfigs = Java8Compat.mapOf();
    /** WorkflowEventListener,由 DifyWorkflowExecutor 注入,用于流式 chunk 透传 */
    private com.dify.workflow.model.WorkflowEventListener eventListener;
    /** 当前工作流实例的 chunk 索引(用于 WorkflowEvent.Chunk.index) */
    private int chunkIndex = 0;
    /** 当前工作流实例的 messageId(用于 WorkflowEvent.Chunk.messageId) */
    private final int chunkMessageId = (int) (System.currentTimeMillis() % 1_000_000);

    void setSubGraphRunner(SubGraphRunner runner) {
        this.subGraphRunner = runner;
    }

    void setHistoryStore(com.dify.workflow.model.ConversationHistoryStore store) {
        this.historyStore = store;
    }

    void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    void setSubWorkflows(Map<String, String> subWorkflows) {
        this.subWorkflows = subWorkflows != null ? subWorkflows : Java8Compat.mapOf();
    }

    void setMcpConfigs(Map<String, McpServerConfig> mcpConfigs) {
        this.mcpConfigs = mcpConfigs != null ? mcpConfigs : Java8Compat.mapOf();
    }

    /**
     * 由 DifyWorkflowExecutor 注入 eventListener,用于 NodeExecutionContext.emitChunk 透传。
     */
    void setEventListener(com.dify.workflow.model.WorkflowEventListener eventListener) {
        this.eventListener = eventListener;
    }

    @Override
    public Map<String, McpServerConfig> getMcpConfigs() {
        return mcpConfigs;
    }

    @Override
    public String getConversationId() {
        return conversationId;
    }

    @Override
    public com.dify.workflow.model.ConversationHistoryStore getHistoryStore() {
        return historyStore;
    }

    public DifyWorkflowContext(DifyDslModel dslModel, Map<String, Object> inputs, LlmProviderFactory llmProviderFactory) {
        this.dslModel = dslModel;
        this.inputs = inputs != null ? new HashMap<>(inputs) : new HashMap<>();
        this.variablePool = new DifyVariablePool();
        this.llmProviderFactory = llmProviderFactory;
        this.variableResolver = new VariableResolver();
    }

    // NodeExecutionContext interface implementation

    @Override
    public VariablePool getVariablePool() {
        return variablePool;
    }

    @Override
    public Object getVariable(String nodeId, String variableName) {
        return variablePool.get(nodeId, variableName);
    }

    @Override
    public void setVariable(String nodeId, String variableName, Object value) {
        variablePool.set(nodeId, variableName, value);
    }

    @Override
    public LlmService getLlmService() {
        return llmProviderFactory;
    }

    @Override
    public String resolveVariables(String template) {
        return variableResolver.resolve(template, ref -> {
            VariableResolver.VariableRef.Type type = ref.type();
            if (type == VariableResolver.VariableRef.Type.SYSTEM) {
                return variablePool.getSystem(ref.field()) != null
                        ? String.valueOf(variablePool.getSystem(ref.field())) : null;
            } else if (type == VariableResolver.VariableRef.Type.ENVIRONMENT) {
                return variablePool.getEnvironment(ref.field()) != null
                        ? String.valueOf(variablePool.getEnvironment(ref.field())) : null;
            } else if (type == VariableResolver.VariableRef.Type.CONVERSATION) {
                return variablePool.getConversation(ref.field()) != null
                        ? String.valueOf(variablePool.getConversation(ref.field())) : null;
            } else if (type == VariableResolver.VariableRef.Type.NODE) {
                return variablePool.get(ref.nodeId(), ref.field()) != null
                        ? String.valueOf(variablePool.get(ref.nodeId(), ref.field())) : null;
            }
            return null;
        });
    }

    @Override
    public void setNodeStatus(String nodeId, String status) {
        nodeStatuses.put(nodeId, NodeStatus.valueOf(status));
    }

    @Override
    public String getNodeStatus(String nodeId) {
        return nodeStatuses.getOrDefault(nodeId, NodeStatus.WAITING).name();
    }

    @Override
    public Map<String, Object> getInputs() {
        return inputs;
    }

    @Override
    public Map<String, Object> getOutputs() {
        return outputs;
    }

    @Override
    public void setOutput(String key, Object value) {
        outputs.put(key, value);
    }

    @Override
    public boolean hasFailed() {
        return failed;
    }

    @Override
    public void setFailed(String errorMessage) {
        this.failed = true;
        this.errorMessage = errorMessage;
    }

    @Override
    public String getCurrentNodeId() {
        return currentNodeId;
    }

    @Override
    public void setCurrentNodeId(String nodeId) {
        this.currentNodeId = nodeId;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public Map<String, Object> executeSubWorkflow(String subWorkflowYaml, Map<String, Object> inputs) {
        try {
            DifyWorkflowExecutor subExecutor = DifyWorkflowExecutor.builder()
                    .yaml(subWorkflowYaml)
                    .llmProviderFactory(llmProviderFactory)
                    .build();
            DifyWorkflowResult result = subExecutor.execute(inputs);
            if (!"succeeded".equals(result.status())) {
                throw new RuntimeException("Sub-workflow execution failed: " + result.errorMessage());
            }
            return result.outputs();
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute sub-workflow: " + e.getMessage(), e);
        }
    }

    /**
     * 根据工具名（workflow name）查找并执行子工作流。
     * 用于 Agent 节点的 workflow 工具调用。
     */
    public Map<String, Object> executeNamedSubWorkflow(String toolName, Map<String, Object> inputs) {
        String yaml = subWorkflows.get(toolName);
        if (yaml == null) {
            throw new IllegalArgumentException("Sub-workflow not found in registry: " + toolName
                    + ". Available: " + subWorkflows.keySet());
        }
        log.info("Executing sub-workflow '{}' (found in registry)", toolName);
        return executeSubWorkflow(yaml, inputs);
    }

    /**
     * 执行子图（迭代/循环体内的节点）。
     * 委托给 DifyWorkflowExecutor 的 executeSubGraph 方法。
     */
    @Override
    public void executeSubGraph(String rootNodeId, String containerNodeId) {
        if (subGraphRunner == null) {
            throw new IllegalStateException("SubGraphRunner not set in context");
        }
        subGraphRunner.run(rootNodeId, containerNodeId, this);
    }

    /**
     * 发射 LLM 流式输出 chunk 事件给 WorkflowEventListener.onChunk。
     * 由 LLM/Agent 节点在收到每个 SSE token 时调用,转发给 listener(若已注入)。
     */
    @Override
    public void emitChunk(String nodeId, String delta) {
        if (eventListener == null) {
            return;  // listener 不关心流式 chunk,默认丢弃
        }
        try {
            int idx = chunkIndex++;
            com.dify.workflow.model.WorkflowEvent.Chunk event =
                    new com.dify.workflow.model.WorkflowEvent.Chunk(nodeId, chunkMessageId, delta, idx);
            eventListener.onChunk(event);
        } catch (Exception e) {
            log.warn("emitChunk failed: {}", e.getMessage());
        }
    }

    // Extended methods for engine

    public DifyDslModel getDslModel() {
        return dslModel;
    }

    public DifyGraph getGraph() {
        return dslModel.workflow().graph();
    }

    public List<DifyNode> getNodes() {
        return getGraph().nodes();
    }

    public Object getInput(String key) {
        return inputs.get(key);
    }

    public void setNodeStatus(String nodeId, NodeStatus status) {
        nodeStatuses.put(nodeId, status);
    }

    public NodeStatus getNodeStatusEnum(String nodeId) {
        return nodeStatuses.getOrDefault(nodeId, NodeStatus.WAITING);
    }

    public boolean isNodeFinished(String nodeId) {
        return nodeStatuses.get(nodeId) == NodeStatus.FINISH;
    }

    public boolean isNodeSkipped(String nodeId) {
        return nodeStatuses.get(nodeId) == NodeStatus.SKIP;
    }

    public DifyNode findNodeById(String nodeId) {
        return getNodes().stream()
                .filter(n -> n.id().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    public DifyNode findStartNode() {
        return getNodes().stream()
                .filter(n -> "start".equals(n.data().type()))
                .findFirst()
                .orElse(null);
    }

    public enum NodeStatus {
        WAITING,
        RUNNING,
        FINISH,
        SKIP,
        ERROR
    }
}
