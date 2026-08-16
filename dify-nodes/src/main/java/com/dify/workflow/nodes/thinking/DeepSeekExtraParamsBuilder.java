package com.dify.workflow.nodes.thinking;

import java.util.HashMap;
import java.util.Map;

/**
 * DeepSeek provider 的思考参数构造器。
 *
 * <p>DeepSeek V4-Flash / R1 等思考模型通过请求体顶层字段控制思考:</p>
 *
 * <pre>{@code
 * {
 *   "model": "deepseek-v4-flash",
 *   "messages": [...],
 *   "thinking": {"type": "enabled" | "disabled"},   // 启用开关
 *   "reasoning_effort": "high" | "max"               // 强度
 * }
 * }</pre>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>{@code thinking.type}: 必须是 struct,字段值 "enabled" 或 "disabled"(不是布尔值)</li>
 *   <li>{@code reasoning_effort}: 字符串,DeepSeek 接受 "high"/"max"</li>
 *   <li>若两个都设了,DeepSeek 实际用 reasoning_effort(thinking.type 仍需 enabled)</li>
 * </ul>
 */
public class DeepSeekExtraParamsBuilder implements ProviderExtraParamsBuilder {

    @Override
    public String providerName() {
        return "deepseek";
    }

    @Override
    public Map<String, Object> buildThinkingExtraParams(Map<String, Object> completionParams) {
        Map<String, Object> result = new HashMap<>();
        if (completionParams == null) {
            return result;
        }

        // 1. 思考开关:thinking=true → thinking: {type: "enabled"}
        //                 thinking=false → thinking: {type: "disabled"}
        //                 未配置 → 不传(用 API 默认:auto)
        Object thinkingVal = completionParams.get("thinking");
        if (Boolean.TRUE.equals(thinkingVal)) {
            Map<String, Object> thinkingOpts = new HashMap<>();
            thinkingOpts.put("type", "enabled");
            // 可选:budget_tokens 控制推理 token 上限
            Object budget = completionParams.get("thinking_budget_tokens");
            if (budget instanceof Number) {
                thinkingOpts.put("budget_tokens", budget);
            }
            result.put("thinking", thinkingOpts);
        } else if (Boolean.FALSE.equals(thinkingVal)) {
            Map<String, Object> thinkingOpts = new HashMap<>();
            thinkingOpts.put("type", "disabled");
            result.put("thinking", thinkingOpts);
        }

        // 2. 透传 reasoning_effort (DeepSeek V4 thinking 强度参数,值 high/max)
        Object reasoningEffort = completionParams.get("reasoning_effort");
        if (reasoningEffort != null) {
            result.put("reasoning_effort", reasoningEffort);
        }

        return result;
    }
}