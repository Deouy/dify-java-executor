package com.dify.workflow.nodes.thinking;

import java.util.HashMap;
import java.util.Map;

/**
 * vLLM provider 的思考参数构造器。
 *
 * <p>vLLM 通过请求体顶层 {@code chat_template_kwargs} 传递思考控制参数
 * (OkHttp 直发 JSON,不能用 Python OpenAI SDK 的 {@code extra_body} 语义):</p>
 *
 * <pre>{@code
 * {
 *   "model": "your-model",
 *   "messages": [...],
 *   "chat_template_kwargs": {
 *     "enable_thinking": true,        // 启用思维链(boolean, 替代 DeepSeek 的 thinking: {...})
 *     "reasoning_effort": "high"      // 思考强度(low/medium/high, 无 "max")
 *   }
 * }
 * }</pre>
 *
 * <h2>取值说明</h2>
 * <ul>
 *   <li>{@code enable_thinking}: boolean — 启用/关闭思维链(替代 DeepSeek 的 thinking.type)</li>
 *   <li>{@code reasoning_effort}: string — 取值 low/medium/high(无 "max")</li>
 * </ul>
 *
 * <p>YAML 支持两种写法:</p>
 * <ul>
 *   <li>{@code thinking: true} / {@code "true"} → {@code enable_thinking: true}</li>
 *   <li>{@code enable_thinking: true} / {@code "true"} → 直接透传</li>
 * </ul>
 * <p>未配置或 false → 不传 enable_thinking(让 vLLM 用模型默认)。</p>
 */
public class VllmExtraParamsBuilder implements ProviderExtraParamsBuilder {

    @Override
    public String providerName() {
        return "vllm";
    }

    @Override
    public Map<String, Object> buildThinkingExtraParams(Map<String, Object> completionParams) {
        Map<String, Object> result = new HashMap<>();
        if (completionParams == null) {
            return result;
        }

        // 内部 chat_template_kwargs Map
        Map<String, Object> chatTemplateKwargs = new HashMap<>();

        // 1. 思考开关:
        //    - completion_params.enable_thinking (Dify 导出常见)
        //    - completion_params.thinking (兼容旧 YAML)
        Object enableThinkingVal = completionParams.get("enable_thinking");
        Object thinkingVal = completionParams.get("thinking");
        if (isThinkingEnabled(enableThinkingVal) || isThinkingEnabled(thinkingVal)) {
            chatTemplateKwargs.put("enable_thinking", Boolean.TRUE);
        }
        // 注意:vLLM 没有显式"关闭"概念,省略字段即代表关闭/默认

        // 2. 思考强度:reasoning_effort 透传(vLLM 接受 low/medium/high)
        Object reasoningEffort = completionParams.get("reasoning_effort");
        if (reasoningEffort != null) {
            chatTemplateKwargs.put("reasoning_effort", reasoningEffort.toString());
        }

        // 3. 作为请求体顶层 chat_template_kwargs(对齐 vLLM OpenAI 兼容 API)
        if (!chatTemplateKwargs.isEmpty()) {
            result.put("chat_template_kwargs", chatTemplateKwargs);
        }

        return result;
    }

    /** 兼容 Boolean.TRUE 与字符串 "true"(忽略大小写)。 */
    static boolean isThinkingEnabled(Object thinkingVal) {
        if (Boolean.TRUE.equals(thinkingVal)) {
            return true;
        }
        if (thinkingVal instanceof String) {
            return "true".equalsIgnoreCase(((String) thinkingVal).trim());
        }
        return false;
    }
}
