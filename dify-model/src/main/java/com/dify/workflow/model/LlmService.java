package com.dify.workflow.model;

import java.util.function.Consumer;

/**
 * Interface for LLM service.
 * Implemented by LlmProviderFactory in dify-engine.
 */
public interface LlmService {

    /**
     * Call the LLM with the specified provider and model (同步阻塞).
     * 内部委托给 callStream + 累积 delta,行为对调用方完全一致。
     */
    LlmCallResult call(String provider, String model, ChatRequest request);

    /**
     * 流式调用 LLM,每收到一个 SSE chunk 触发一次 onDelta 回调。
     * 收尾时会发一条 delta=null 的回调作为 stream 结束信号(finishReason="stop" 或 "error:...")。
     *
     * <p>对齐 Dify Python 端 LLMService.stream_chat + SSE data: {...} 解析流程。</p>
     */
    void callStream(String provider, String model, ChatRequest request,
                    Consumer<StreamDelta> onDelta);

    /**
     * Check if a provider supports vision (image input).
     */
    default boolean supportsVision(String provider) {
        return false;
    }

    /**
     * 关键修复(S18):获取 provider 支持的 model 列表(白名单)。
     * LLM 节点发请求前调用此方法,校验 YAML model ∈ 白名单,防"假通过"。
     * 默认空 Set = 不校验(向后兼容老 config)。
     */
    default java.util.Set<String> getSupportedModels(String provider) {
        return java.util.Collections.emptySet();
    }
}
