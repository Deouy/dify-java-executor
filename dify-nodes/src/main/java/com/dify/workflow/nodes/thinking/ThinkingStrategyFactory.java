package com.dify.workflow.nodes.thinking;

/**
 * 思考内容策略工厂。
 *
 * <p>根据 LLM provider 和 YAML 节点的 {@code reasoning_format} 配置
 * 选择合适的 {@link ThinkingContentStrategy}。</p>
 *
 * <h2>选择规则</h2>
 * <pre>
 *  reasoning_format=separated:
 *    ├─ provider 含 "deepseek" → DeepSeekSeparatedStrategy
 *    ├─ provider 含 "vllm"     → TagBasedSeparatedStrategy (后续追加 vLLM 专属行为)
 *    └─ 其它                    → TagBasedSeparatedStrategy (兜底)
 *
 *  reasoning_format=其它或未配置:
 *    └─ MergedThinkTagStrategy (默认)
 * </pre>
 */
public final class ThinkingStrategyFactory {

    private ThinkingStrategyFactory() {}

    /**
     * 根据 provider 和 reasoning_format 选择策略。
     *
     * @param provider        LLM provider 名,例如 {@code "langgenius/deepseek/deepseek"}、
     *                        {@code "vllm"}、{@code "openai"} 等
     * @param reasoningFormat 来自 YAML LLM 节点的 {@code reasoning_format} 字段
     *                        (可为 null)
     * @return 对应的思考内容策略
     */
    public static ThinkingContentStrategy create(String provider, String reasoningFormat) {
        boolean separated = "separated".equals(reasoningFormat);

        if (!separated) {
            // 默认:合并流,标签拦截并把内容当正文输出
            return new MergedThinkTagStrategy();
        }

        // 分离模式:按 provider 选策略
        String p = provider != null ? provider.toLowerCase() : "";
        if (p.contains("deepseek")) {
            return new DeepSeekSeparatedStrategy();
        }
        if (p.contains("vllm")) {
            // vLLM 用 <think>...</think> 标签包裹思考内容,无原生 reasoning_content 字段
            return new VllmStrategy();
        }

        // 兜底:用标签解析(OpenAI o1 / Anthropic / 其它通用)
        return new TagBasedSeparatedStrategy();
    }
}