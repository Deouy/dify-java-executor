package com.dify.workflow.engine.llm;

import com.dify.workflow.engine.llm.providers.CustomOpenAiProvider;
import com.dify.workflow.engine.llm.providers.DeepSeekProvider;
import com.dify.workflow.engine.llm.providers.MiniMaxProvider;
import com.dify.workflow.engine.llm.providers.OpenAiProvider;
import com.dify.workflow.model.ChatRequest;
import com.dify.workflow.model.LlmCallResult;
import com.dify.workflow.model.LlmService;
import com.dify.workflow.model.StreamDelta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Factory for creating and managing LLM providers.
 *
 * Usage:
 * <pre>
 * LlmProviderFactory factory = LlmProviderFactory.builder()
 *     .provider("openai", LlmProviderConfig.builder()
 *         .provider("openai")
 *         .baseUrl("https://api.openai.com/v1")
 *         .apiKey("sk-xxx")
 *         .defaultModel("gpt-4o")
 *         .build())
 *     .provider("deepseek", LlmProviderConfig.builder()
 *         .provider("deepseek")
 *         .baseUrl("https://api.deepseek.com/v1")
 *         .apiKey("xxx")
 *         .defaultModel("deepseek-chat")
 *         .build())
 *     .build();
 *
 * LlmCallResult result = factory.call("openai", "gpt-4o", chatRequest);
 * </pre>
 */
public class LlmProviderFactory implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderFactory.class);

    private final Map<String, LlmProviderConfig> configs = new HashMap<>();
    private final Map<String, Function<ChatRequest, LlmCallResult>> executors = new HashMap<>();
    private final Map<String, java.util.function.BiConsumer<ChatRequest, Consumer<StreamDelta>>> streamExecutors = new HashMap<>();

    private LlmProviderFactory() {}

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Register a provider with its configuration.
     *
     * @param name Provider name (e.g., "openai", "deepseek")
     * @param config Provider configuration
     */
    public void registerProvider(String name, LlmProviderConfig config) {
        // S19.1 修复:defaultModel ∈ supportedModels 不变式已上移至 LlmProviderConfig.Builder.build(),
        //   此处不再重复检查。Builder 保证 config 总是 valid。
        log.info("Registering LLM provider: {} with baseUrl: {}, models: {}",
                name, config.baseUrl(), config.supportedModels());
        configs.put(name, config);
    }

    /**
     * Register a custom executor for a provider.
     *
     * @param name Provider name
     * @param executor Custom executor function
     */
    public void registerExecutor(String name, Function<ChatRequest, LlmCallResult> executor) {
        executors.put(name, executor);
    }

    /**
     * 解析提供商名称。
     * marketplace 格式: "langgenius/deepseek/deepseek" → 尝试最后一段 "deepseek"
     * 简单格式: "deepseek" → 直接匹配
     */
    private String resolveProvider(String provider) {
        if (configs.containsKey(provider)) {
            return provider;
        }
        // marketplace ID 格式: 按 / 分割，尝试每段
        if (provider.contains("/")) {
            String[] parts = provider.split("/");
            // 从后往前尝试
            for (int i = parts.length - 1; i >= 0; i--) {
                if (configs.containsKey(parts[i])) {
                    log.info("Resolved provider '{}' → '{}'", provider, parts[i]);
                    return parts[i];
                }
            }
        }
        return provider;
    }

    /**
     * Call the LLM with the specified provider and model.
     *
     * @param provider Provider name
     * @param model Model name (uses default if null/empty)
     * @param request Chat request
     * @return LLM call result
     */
    @Override
    public LlmCallResult call(String provider, String model, ChatRequest request) {
        String resolvedProvider = resolveProvider(provider);
        LlmProviderConfig config = configs.get(resolvedProvider);
        if (config == null) {
            throw new IllegalArgumentException("Unknown LLM provider: " + provider);
        }

        String actualModel = (model != null && !model.isEmpty()) ? model : config.defaultModel();

        // Create a new request with the resolved model
        ChatRequest resolvedRequest = ChatRequest.builder()
                .model(actualModel)
                .messages(request.messages())
                .temperature(request.temperature() != null ? request.temperature() : config.temperature())
                .maxTokens(request.maxTokens() != null ? request.maxTokens() : config.maxTokens())
                .topP(request.topP())
                .extraParameters(request.extraParameters())
                .tools(request.tools())  // 传递 tools 参数
                .build();

        log.info("Calling LLM provider: {} (resolved from {}) with model: {}", resolvedProvider, provider, actualModel);

        Function<ChatRequest, LlmCallResult> executor = executors.get(resolvedProvider);
        if (executor == null) {
            throw new IllegalArgumentException("No executor registered for provider: " + resolvedProvider);
        }

        return executor.apply(resolvedRequest);
    }

    /**
     * 流式调用 LLM,委托给 provider 的 stream executor。每收到一个 SSE chunk 触发一次 onDelta。
     */
    @Override
    public void callStream(String provider, String model, ChatRequest request, Consumer<StreamDelta> onDelta) {
        String resolvedProvider = resolveProvider(provider);
        LlmProviderConfig config = configs.get(resolvedProvider);
        if (config == null) {
            throw new IllegalArgumentException("Unknown LLM provider: " + provider);
        }

        String actualModel = (model != null && !model.isEmpty()) ? model : config.defaultModel();
        ChatRequest resolvedRequest = ChatRequest.builder()
                .model(actualModel)
                .messages(request.messages())
                .temperature(request.temperature() != null ? request.temperature() : config.temperature())
                .maxTokens(request.maxTokens() != null ? request.maxTokens() : config.maxTokens())
                .topP(request.topP())
                .extraParameters(request.extraParameters())
                .tools(request.tools())
                .stream(true)
                .build();

        log.info("Calling LLM provider (stream): {} (resolved from {}) with model: {}",
                resolvedProvider, provider, actualModel);

        java.util.function.BiConsumer<ChatRequest, Consumer<StreamDelta>> streamExecutor = streamExecutors.get(resolvedProvider);
        if (streamExecutor == null) {
            // Fallback: 同步 executor 累积 delta 模拟流式
            log.debug("No streamExecutor for {}, using call() fallback", resolvedProvider);
            LlmCallResult result = call(provider, model, request);
            String content = result.content() != null ? result.content() : "";
            int chunkSize = Math.max(1, content.length() / 5);
            int idx = 0;
            for (int i = 0; i < content.length(); i += chunkSize) {
                String piece = content.substring(i, Math.min(i + chunkSize, content.length()));
                onDelta.accept(new StreamDelta(piece, idx++, null));
            }
            onDelta.accept(new StreamDelta(null, idx++, result.finishReason() != null ? result.finishReason() : "stop"));
            return;
        }
        streamExecutor.accept(resolvedRequest, onDelta);
    }

    @Override
    public boolean supportsVision(String provider) {
        String resolved = resolveProvider(provider);
        LlmProviderConfig config = configs.get(resolved);
        return config != null && Boolean.TRUE.equals(config.supportsVision());
    }

    /**
     * 关键修复(S18):获取 provider 支持的 model 列表(白名单)。
     * 用于 LLM 节点发请求前校验 YAML model 是否在白名单中,防"假通过"。
     * <p>
     * S19 修复:auto-fallback —— supportedModels 为空但 defaultModel 非空时,
     * 自动回退到 {defaultModel}。这样只配 defaultModel 的最小化注册也能强制校验。
     * 两者都为空时返回 emptySet,由 LlmNode 抛"provider 未配置 model"错误。
     * <p>
     * S19.2 简化:LlmProviderConfig 构造器保证 supportedModels 非 null(总是 init 为 emptySet),
     *   所以此处只需 isEmpty() 检查,不再 null check。
     */
    public java.util.Set<String> getSupportedModels(String provider) {
        String resolved = resolveProvider(provider);
        LlmProviderConfig config = configs.get(resolved);
        if (config == null) {
            return java.util.Collections.emptySet();
        }
        if (config.supportedModels().isEmpty()
                && config.defaultModel() != null && !config.defaultModel().isEmpty()) {
            return java.util.Collections.singleton(config.defaultModel());
        }
        return config.supportedModels();
    }

    /**
     * Get provider configuration.
     */
    public LlmProviderConfig getConfig(String name) {
        return configs.get(name);
    }

    /**
     * Check if a provider is registered.
     *
     * @param name Provider name
     * @return true if registered
     */
    public boolean hasProvider(String name) {
        return configs.containsKey(name);
    }

    /**
     * Get provider configuration.
     *
     * @param name Provider name
     * @return Provider config or null
     */
    public LlmProviderConfig getProvider(String name) {
        return configs.get(name);
    }

    public static class Builder {
        private final Map<String, LlmProviderConfig> configs = new HashMap<>();
        private final Map<String, Function<ChatRequest, LlmCallResult>> executors = new HashMap<>();
        private final Map<String, java.util.function.BiConsumer<ChatRequest, Consumer<StreamDelta>>> streamExecutors = new HashMap<>();

        public Builder provider(String name, LlmProviderConfig config) {
            configs.put(name, config);
            return this;
        }

        public Builder provider(String name, String baseUrl, String apiKey, String defaultModel) {
            configs.put(name, LlmProviderConfig.builder()
                    .provider(name)
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .defaultModel(defaultModel)
                    .build());
            return this;
        }

        public Builder executor(String name, Function<ChatRequest, LlmCallResult> executor) {
            executors.put(name, executor);
            return this;
        }

        public Builder streamExecutor(String name, java.util.function.BiConsumer<ChatRequest, Consumer<StreamDelta>> executor) {
            streamExecutors.put(name, executor);
            return this;
        }

        public Builder withBuiltinProviders() {
            // Register built-in provider executors
            executors.put("openai", request -> {
                LlmProviderConfig config = configs.get("openai");
                if (config == null) throw new IllegalArgumentException("OpenAI config not found");
                return new OpenAiProvider(config).call(request);
            });

            executors.put("deepseek", request -> {
                LlmProviderConfig config = configs.get("deepseek");
                if (config == null) throw new IllegalArgumentException("DeepSeek config not found");
                return new DeepSeekProvider(config).call(request);
            });

            executors.put("minimax", request -> {
                LlmProviderConfig config = configs.get("minimax");
                if (config == null) throw new IllegalArgumentException("MiniMax config not found");
                return new MiniMaxProvider(config).call(request);
            });

            executors.put("custom", request -> {
                LlmProviderConfig config = configs.get("custom");
                if (config == null) throw new IllegalArgumentException("Custom config not found");
                return new CustomOpenAiProvider(config).call(request);
            });

            // 注册流式 executor:callStream 路径走 AbstractLlmProvider 的真 SSE 实现(OkHttp EventSource),
            //   不再回退到 callStream() 行 169-181 的"同步 call + 切 5 段"假流。
            // try/catch IOException 是必需的:AbstractLlmProvider.callStream 声明 throws IOException,
            //   而 BiConsumer.accept 不允许 checked exception,wrap 成 RuntimeException 对齐
            //   LlmNode.doExecute 行 223-225 的现有做法。
            streamExecutors.put("openai", (req, onDelta) -> {
                LlmProviderConfig config = configs.get("openai");
                if (config == null) throw new IllegalArgumentException("OpenAI config not found");
                try { new OpenAiProvider(config).callStream(req, onDelta); }
                catch (IOException e) { throw new RuntimeException("LLM stream call failed for openai: " + e.getMessage(), e); }
            });

            streamExecutors.put("deepseek", (req, onDelta) -> {
                LlmProviderConfig config = configs.get("deepseek");
                if (config == null) throw new IllegalArgumentException("DeepSeek config not found");
                try { new DeepSeekProvider(config).callStream(req, onDelta); }
                catch (IOException e) { throw new RuntimeException("LLM stream call failed for deepseek: " + e.getMessage(), e); }
            });

            streamExecutors.put("minimax", (req, onDelta) -> {
                LlmProviderConfig config = configs.get("minimax");
                if (config == null) throw new IllegalArgumentException("MiniMax config not found");
                try { new MiniMaxProvider(config).callStream(req, onDelta); }
                catch (IOException e) { throw new RuntimeException("LLM stream call failed for minimax: " + e.getMessage(), e); }
            });

            streamExecutors.put("custom", (req, onDelta) -> {
                LlmProviderConfig config = configs.get("custom");
                if (config == null) throw new IllegalArgumentException("Custom config not found");
                try { new CustomOpenAiProvider(config).callStream(req, onDelta); }
                catch (IOException e) { throw new RuntimeException("LLM stream call failed for custom: " + e.getMessage(), e); }
            });

            return this;
        }

        public LlmProviderFactory build() {
            LlmProviderFactory factory = new LlmProviderFactory();
            factory.configs.putAll(configs);
            factory.executors.putAll(executors);
            // 关键修复:把 streamExecutors 复制到 factory,否则 Builder.streamExecutor(...) 公共 API
            //   静默失效,callStream 永远掉进行 169-181 的假流兜底。
            factory.streamExecutors.putAll(streamExecutors);
            return factory;
        }
    }
}
