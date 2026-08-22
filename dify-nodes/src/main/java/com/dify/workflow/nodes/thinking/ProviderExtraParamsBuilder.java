package com.dify.workflow.nodes.thinking;

import java.util.Map;

/**
 * Provider 思考参数构造器。
 *
 * <p>把 YAML LLM 节点的 {@code model.completion_params} 转换为该 provider
 * API 期望的请求参数结构。不同 provider 的"思考控制参数"位置和命名差异较大,
 * 用策略模式统一处理:</p>
 *
 * <h2>差异对比</h2>
 *
 * <table border="1">
 *   <tr><th>Provider</th><th>启用开关</th><th>强度参数</th><th>位置</th></tr>
 *   <tr><td>DeepSeek V4</td><td>{@code thinking: {type: "enabled"}}</td>
 *       <td>{@code reasoning_effort: "max"}</td><td>请求体顶层</td></tr>
 *   <tr><td>vLLM</td><td>{@code enable_thinking: true}</td>
 *       <td>{@code reasoning_effort: "high"}</td>
 *       <td>请求体顶层 {@code chat_template_kwargs}</td></tr>
 *   <tr><td>OpenAI o1</td><td>(顶层 reasoning_effort)</td>
 *       <td>{@code reasoning_effort: "high"}</td><td>请求体顶层</td></tr>
 * </table>
 *
 * <p>实现方负责按自家 API 文档转换 {@code completion_params} 中的字段。</p>
 */
public interface ProviderExtraParamsBuilder {

    /**
     * 构造 provider API 期望的额外请求参数。
     *
     * @param completionParams YAML {@code model.completion_params} 完整 Map
     *        (可能含 {@code thinking}、{@code reasoning_effort}、
     *         {@code temperature} 等字段,本接口只关心思考相关字段)
     * @return 构造好的 extra params(将作为请求体顶层字段发送)
     */
    Map<String, Object> buildThinkingExtraParams(Map<String, Object> completionParams);

    /**
     * provider 名(用于工厂匹配)。
     * 如 {@code "vllm"}、{@code "deepseek"}。
     */
    String providerName();
}