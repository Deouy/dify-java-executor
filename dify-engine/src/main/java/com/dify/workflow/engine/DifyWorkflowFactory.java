package com.dify.workflow.engine;

import com.dify.workflow.engine.llm.LlmProviderConfig;
import com.dify.workflow.engine.llm.LlmProviderFactory;
import com.dify.workflow.model.ConversationHistoryStore;
import com.dify.workflow.model.Java8Compat;
import com.dify.workflow.model.McpServerConfig;
import com.dify.workflow.model.WorkflowEventListener;
import com.dify.workflow.parser.DifyYamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dify 工作流执行工厂。
 * Builder 阶段只配全局:LLM Provider / MCP / YAML registry。
 * listener / historyStore / conversationId 通过 execute(..., ExecuteOptions) 每次执行传特定值。
 *
 * <pre>{@code
 * // Builder 阶段(全局,只配一次)
 * DifyWorkflowFactory factory = DifyWorkflowFactory.builder()
 *     .llmProvider(deepseekConfig)
 *     .mcpConfig("baidumap", McpServerConfig.builder()...build())
 *     .registerWorkflow(Path.of("workflow.yml"))
 *     .build();
 *
 * // Execute 阶段(每次执行传特定值)
 * InMemoryConversationStore history = new InMemoryConversationStore();
 * DifyWorkflowResult r = factory.execute("testchatflow", Map.of("a", 1),
 *     DifyWorkflowFactory.ExecuteOptions.builder()
 *         .listener(new SseStreamListener())      // 每次执行特定的 listener
 *         .historyStore(history)                  // 跨执行累积的记忆存储
 *         .conversationId("user-123-session-1")   // 本次会话 ID
 *         .build());
 * }</pre>
 */
public class DifyWorkflowFactory {

    private static final Logger log = LoggerFactory.getLogger(DifyWorkflowFactory.class);

    private final LlmProviderFactory llmFactory;
    private final Map<String, McpServerConfig> mcpConfigs;
    private final Map<String, String> yamlRegistry;

    private DifyWorkflowFactory(Builder builder) {
        this.llmFactory = builder.llmBuilder.build();
        this.mcpConfigs = new ConcurrentHashMap<>(builder.mcpConfigs);
        this.yamlRegistry = new ConcurrentHashMap<>(builder.yamlRegistry);
    }

    // ========== YAML 管理 ==========

    /**
     * 注册工作流 YAML 字符串。key 从 YAML 的 app.name 自动提取。
     */
    public String registerWorkflow(String yamlContent) {
        String workflowId = extractAppName(yamlContent);
        yamlRegistry.put(workflowId, yamlContent);
        log.info("Registered workflow: {}", workflowId);
        return workflowId;
    }

    /**
     * 注册工作流 YAML 文件。key 从 YAML 的 app.name 自动提取。
     */
    public String registerWorkflow(Path yamlPath) {
        try {
            String content = new String(Files.readAllBytes(yamlPath), java.nio.charset.StandardCharsets.UTF_8);
            return registerWorkflow(content);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read YAML file: " + yamlPath, e);
        }
    }

    /** 移除已注册的工作流 */
    public void removeWorkflow(String workflowId) {
        yamlRegistry.remove(workflowId);
        log.info("Removed workflow: {}", workflowId);
    }

    /** 检查工作流是否已注册 */
    public boolean hasWorkflow(String workflowId) {
        return yamlRegistry.containsKey(workflowId);
    }

    /** 获取所有已注册的工作流 ID */
    public java.util.Set<String> getWorkflowIds() {
        return java.util.Collections.unmodifiableSet(yamlRegistry.keySet());
    }

    // ========== 执行 ==========

    /**
     * 简单执行(无 listener / history / conversationId)。
     * 委托给 execute(workflowId, inputs, ExecuteOptions.builder().build())。
     */
    public DifyWorkflowResult execute(String workflowId, Map<String, Object> inputs) {
        return execute(workflowId, inputs, ExecuteOptions.builder().build());
    }

    /**
     * 完整执行:传 ExecuteOptions 显式控制 listener / history / conversationId。
     */
    public DifyWorkflowResult execute(String workflowId, Map<String, Object> inputs,
                                     ExecuteOptions options) {
        String yaml = yamlRegistry.get(workflowId);
        if (yaml == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId
                    + ". Available: " + yamlRegistry.keySet());
        }

        DifyWorkflowExecutor.Builder builder = DifyWorkflowExecutor.builder()
                .yaml(yaml)
                .llmProviderFactory(llmFactory);

        if (options.listener() != null) {
            builder.eventListener(options.listener());
        }
        if (options.historyStore() != null) {
            builder.historyStore(options.historyStore());
        }
        if (options.conversationId() != null) {
            builder.conversationId(options.conversationId());
        }
        // 注入子工作流注册表,供 Agent 节点使用
        if (!yamlRegistry.isEmpty()) {
            builder.subWorkflows(new java.util.ArrayList<>(yamlRegistry.values()));
        }

        DifyWorkflowExecutor executor = builder.build();
        return executor.execute(inputs != null ? new HashMap<>(inputs) : Java8Compat.mapOf());
    }

    /**
     * 单次执行的选项。
     * 替代旧版 execute(..., listener, historyStore, conversationId) 多参风格,语义更清晰。
     */
    public static final class ExecuteOptions {
        /** 本次执行的监听器(可空,空则不监听) */
        private final WorkflowEventListener listener;
        /** 聊天历史存储(可空,空则不持久化多轮记忆) */
        private final ConversationHistoryStore historyStore;
        /** 本次执行的会话 ID(可空,空则单次匿名) */
        private final String conversationId;

        public ExecuteOptions(WorkflowEventListener listener,
                              ConversationHistoryStore historyStore,
                              String conversationId) {
            this.listener = listener;
            this.historyStore = historyStore;
            this.conversationId = conversationId;
        }

        public WorkflowEventListener listener() { return listener; }
        public ConversationHistoryStore historyStore() { return historyStore; }
        public String conversationId() { return conversationId; }

        public WorkflowEventListener getListener() { return listener; }
        public ConversationHistoryStore getHistoryStore() { return historyStore; }
        public String getConversationId() { return conversationId; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private WorkflowEventListener listener;
            private ConversationHistoryStore historyStore;
            private String conversationId;

            public Builder listener(WorkflowEventListener listener) {
                this.listener = listener;
                return this;
            }

            public Builder historyStore(ConversationHistoryStore historyStore) {
                this.historyStore = historyStore;
                return this;
            }

            public Builder conversationId(String conversationId) {
                this.conversationId = conversationId;
                return this;
            }

            public ExecuteOptions build() {
                return new ExecuteOptions(listener, historyStore, conversationId);
            }
        }
    }

    // ========== 内部 ==========

    /** 从 YAML 内容提取 app.name */
    private static String extractAppName(String yamlContent) {
        try {
            return new DifyYamlParser().parse(yamlContent).app().name();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse YAML or extract app.name", e);
        }
    }

    // ========== Builder ==========

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final LlmProviderFactory.Builder llmBuilder = LlmProviderFactory.builder();
        private final Map<String, McpServerConfig> mcpConfigs = new HashMap<>();
        private final Map<String, String> yamlRegistry = new HashMap<>();

        public Builder() {
            // 默认注册 4 个内置 provider (openai/deepseek/minimax/custom) 的 executor,
            // 调用方可继续 .provider(name, config) 添加自定义配置。
            llmBuilder.withBuiltinProviders();
        }

        /** 添加 LLM Provider 配置 */
        public Builder llmProvider(LlmProviderConfig config) {
            llmBuilder.provider(config.provider(), config);
            return this;
        }

        /** 添加 MCP Server 配置 */
        public Builder mcpConfig(String name, McpServerConfig config) {
            mcpConfigs.put(name, config);
            return this;
        }

        /** 注册工作流 YAML 字符串（自动提取 app.name 做 key） */
        public Builder registerWorkflow(String yamlContent) {
            String id = extractAppName(yamlContent);
            yamlRegistry.put(id, yamlContent);
            return this;
        }

        /** 注册工作流 YAML 文件（自动提取 app.name 做 key） */
        public Builder registerWorkflow(Path yamlPath) {
            try {
                String content = new String(Files.readAllBytes(yamlPath), java.nio.charset.StandardCharsets.UTF_8);
                return registerWorkflow(content);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to read YAML: " + yamlPath, e);
            }
        }

        public DifyWorkflowFactory build() {
            return new DifyWorkflowFactory(this);
        }
    }

    // ========== getters ==========

    public Map<String, String> getYamlRegistry() { return new HashMap<>(yamlRegistry); }
    public Map<String, McpServerConfig> getMcpConfigs() { return new HashMap<>(mcpConfigs); }
    public LlmProviderFactory getLlmFactory() { return llmFactory; }
}
