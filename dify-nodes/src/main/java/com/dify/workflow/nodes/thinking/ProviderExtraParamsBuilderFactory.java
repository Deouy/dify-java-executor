package com.dify.workflow.nodes.thinking;

import java.util.HashMap;
import java.util.Map;

/**
 * Provider 思考参数构造器工厂。
 *
 * <p>根据 LLM provider 名称返回对应的 {@link ProviderExtraParamsBuilder}。
 * 注册的 provider 与 {@link ThinkingStrategyFactory} 一致,保持策略一致性。</p>
 */
public final class ProviderExtraParamsBuilderFactory {

    private static final Map<String, ProviderExtraParamsBuilder> BUILDERS = new HashMap<>();

    static {
        BUILDERS.put("deepseek", new DeepSeekExtraParamsBuilder());
        BUILDERS.put("vllm", new VllmExtraParamsBuilder());
        // 其它 provider 走 default(直接透传字段)
    }

    private ProviderExtraParamsBuilderFactory() {}

    /**
     * 根据 provider 名称获取构造器。
     *
     * @param provider provider 名,例如 {@code "langgenius/deepseek/deepseek"}、
     *                 {@code "vllm"}、{@code "openai"} 等
     * @return 对应的 builder;未注册的 provider 返回 {@link DefaultExtraParamsBuilder}
     */
    public static ProviderExtraParamsBuilder create(String provider) {
        if (provider == null) {
            return new DefaultExtraParamsBuilder();
        }
        String p = provider.toLowerCase();
        // 优先匹配:provider 含 "deepseek" → DeepSeekExtraParamsBuilder
        if (p.contains("deepseek")) {
            return BUILDERS.get("deepseek");
        }
        if (p.contains("vllm")) {
            return BUILDERS.get("vllm");
        }
        return new DefaultExtraParamsBuilder();
    }
}