package com.dify.workflow.nodes.thinking;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认 ProviderExtraParamsBuilder:不做结构转换,直接透传 thinking/reasoning_effort 字段。
 *
 * <p>适用于未实现专属 builder 的 provider(OpenAI、Anthropic 等),由具体 LLM
 * API 自己识别 thinking/reasoning_effort 字段。</p>
 */
public class DefaultExtraParamsBuilder implements ProviderExtraParamsBuilder {

    @Override
    public String providerName() {
        return "default";
    }

    @Override
    public Map<String, Object> buildThinkingExtraParams(Map<String, Object> completionParams) {
        Map<String, Object> result = new HashMap<>();
        if (completionParams == null) {
            return result;
        }
        // 透传 thinking(bool) 和 reasoning_effort(string)
        // (OpenAI 等 API 接受顶层字段)
        if (completionParams.containsKey("thinking")) {
            result.put("thinking", completionParams.get("thinking"));
        }
        if (completionParams.containsKey("reasoning_effort")) {
            result.put("reasoning_effort", completionParams.get("reasoning_effort"));
        }
        return result;
    }
}