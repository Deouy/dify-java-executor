package com.dify.workflow.nodes.thinking;

import com.dify.workflow.model.StreamDelta;
import com.dify.workflow.nodes.ThinkTagParser;

/**
 * 基于 {@code <think>...</think>} 标签的分离策略。
 *
 * <p>适用于把思考内容放在 {@code content} 里并用标签包裹的 LLM:</p>
 * <ul>
 *   <li><b>vLLM</b>(后续追加):本地部署的 Qwen3 / DeepSeek-R1-Distill 等
 *       — 通常返回 {@code <think>...</think>} 标签</li>
 *   <li><b>OpenAI o1</b>:通过 chat completions API,有时也用 <think> 标签</li>
 *   <li>任何返回原生 {@code reasoning_content} 字段但标签兜底仍生效的场景</li>
 * </ul>
 *
 * <p>行为:</p>
 * <ul>
 *   <li>若 delta 含 {@code reasoning_content} 字段(API 原生),优先用之</li>
 *   <li>否则用 {@link ThinkTagParser} 解析 {@code content} 字段,把标签内部 emit 到
 *       {@code reason_content},标签外部 emit 到 {@code text}</li>
 *   <li>若同时存在 {@code reasoning_content} 和带标签的 {@code content},
 *       不重复 emit(以 {@code reasoning_content} 为权威源)</li>
 * </ul>
 *
 * <p>TODO:目前先实现通用 <think> 标签解析,后续追加 vLLM-specific 行为
 * (如 chat template 注入、特殊 stop token 等)。</p>
 */
public class TagBasedSeparatedStrategy implements ThinkingContentStrategy {

    private final ThinkTagParser parser = new ThinkTagParser();

    @Override
    public void onDelta(StreamDelta delta, DeltaEmitter emitter) {
        if (delta == null) return;

        // 路径 A:delta.reasoningContent() 非空 → API 原生推理字段,直接 emit
        //         (DeepSeek R1 / V4-Flash 的 delta 也可能走这条,即使 provider 是 vLLM 代理)
        if (delta.reasoningContent() != null && !delta.reasoningContent().isEmpty()) {
            emitter.emitReasonContent(delta.reasoningContent());
        }

        if (delta.content() == null || delta.content().isEmpty()) {
            return;
        }

        // 路径 B:delta.reasoningContent() 已发 → content 是纯正文,不再做标签解析避免重复
        //         (类似 DeepSeek 分离策略,跳过 ThinkTagParser)
        if (delta.reasoningContent() != null && !delta.reasoningContent().isEmpty()) {
            emitter.emitText(delta.content());
            return;
        }

        // 路径 C:只有 content 且带 <think> 标签 → 按段分发
        for (ThinkTagParser.Segment seg : parser.consume(delta.content())) {
            if (seg.isNormalText()) {
                emitter.emitText(seg.text);
            } else {
                emitter.emitReasonContent(seg.text);
            }
        }
    }

    @Override
    public void flush(DeltaEmitter emitter) {
        // 强制 flush ThinkTagParser 残留 buffer
        for (ThinkTagParser.Segment seg : parser.flush(parser.isInsideThink())) {
            if (seg.isNormalText()) {
                emitter.emitText(seg.text);
            } else {
                emitter.emitReasonContent(seg.text);
            }
        }
    }
}