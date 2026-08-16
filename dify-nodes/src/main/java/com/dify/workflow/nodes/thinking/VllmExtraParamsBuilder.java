package com.dify.workflow.nodes.thinking;

import java.util.HashMap;
import java.util.Map;

/**
 * vLLM provider 的思考参数构造器。
 *
 * <p>vLLM 通过 {@code extra_body.chat_template_kwargs} 传递思考控制参数,
 * 字段命名与 DeepSeek 不同:</p>
 *
 * <pre>{@code
 * {
 *   "model": "your-model",
 *   "messages": [...],
 *   "extra_body": {
 *     "chat_template_kwargs": {
 *       "enable_thinking": true,        // 启用思维链(boolean, 替代 DeepSeek 的 thinking: {...})
 *       "reasoning_effort": "high"      // 思考强度(low/medium/high, 无 "max")
 *     }
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
 * <p>YAML 中 thinking=true → enable_thinking=true;
 * YAML 中未配置或 false → 不传 enable_thinking 字段(让 vLLM 用模型默认)。</p>
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

        // 1. 思考开关:thinking=true → enable_thinking=true
        //    YAML 中未配置或 false → 不传(让 vLLM 用模型默认)
        Object thinkingVal = completionParams.get("thinking");
        if (Boolean.TRUE.equals(thinkingVal)) {
            chatTemplateKwargs.put("enable_thinking", Boolean.TRUE);
        }
        // 注意:vLLM 没有显式"关闭"概念,省略字段即代表关闭/默认

        // 2. 思考强度:reasoning_effort 透传(vLLM 接受 low/medium/high)
        Object reasoningEffort = completionParams.get("reasoning_effort");
        if (reasoningEffort != null) {
            chatTemplateKwargs.put("reasoning_effort", reasoningEffort.toString());
        }

        // 3. 包装成 extra_body.chat_template_kwargs
        if (!chatTemplateKwargs.isEmpty()) {
            Map<String, Object> extraBody = new HashMap<>();
            extraBody.put("chat_template_kwargs", chatTemplateKwargs);
            result.put("extra_body", extraBody);
        }

        return result;
    }
}