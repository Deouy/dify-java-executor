package com.dify.workflow.nodes.thinking;

import com.dify.workflow.model.StreamDelta;
import com.dify.workflow.nodes.ThinkTagParser;

/**
 * vLLM 思考内容处理策略。
 *
 * <p>vLLM 0.22+ 在 SSE / 非流式响应中通过独立字段返回思维链
 * ({@code delta.reasoning} 或 {@code message.reasoning}),由
 * {@link com.dify.workflow.engine.llm.providers.AbstractLlmProvider}
 * 映射到 {@link StreamDelta#reasoningContent()}。
 * 旧版或未开独立推理字段时,也可能把思考放进 {@code content} 的
 * {@code <think>...</think>} 标签里。</p>
 *
 * <p>行为:</p>
 * <ul>
 *   <li>若 delta 带 {@code reasoningContent}(来自 API 的 {@code reasoning}
 *       或 {@code reasoning_content}),优先 emit 到 {@code reason_content}</li>
 *   <li>否则解析 {@code content} 中的 <think>...</think> 标签</li>
 *   <li>标签内部 emit 到 {@code reason_content},标签外部 emit 到 {@code text}</li>
 * </ul>
 *
 * <p>适用条件:</p>
 * <ul>
 *   <li>YAML LLM 节点配置 {@code reasoning_format: separated}</li>
 *   <li>LLM provider 是 vllm 系列</li>
 * </ul>
 */
public class VllmStrategy implements ThinkingContentStrategy {

    private final ThinkTagParser parser = new ThinkTagParser();

    @Override
    public void onDelta(StreamDelta delta, DeltaEmitter emitter) {
        if (delta == null) return;

        // 路径 1:API 原生 reasoning / reasoning_content → StreamDelta.reasoningContent
        if (delta.reasoningContent() != null && !delta.reasoningContent().isEmpty()) {
            emitter.emitReasonContent(delta.reasoningContent());
        }

        // 路径 2:解析 content 中的 <think> 标签
        if (delta.content() != null && !delta.content().isEmpty()) {
            if (delta.reasoningContent() != null && !delta.reasoningContent().isEmpty()) {
                // reasoning 已发,content 是纯文本,不再做标签解析(避免重复)
                emitter.emitText(delta.content());
            } else {
                // 用 ThinkTagParser 拆 <think> 标签
                for (ThinkTagParser.Segment seg : parser.consume(delta.content())) {
                    if (seg.isNormalText()) {
                        emitter.emitText(seg.text);
                    } else {
                        emitter.emitReasonContent(seg.text);
                    }
                }
            }
        }
    }

    @Override
    public void flush(DeltaEmitter emitter) {
        // 强制 flush ThinkTagParser 残留 buffer
        // 防止最后一段停在 <think> 中间时,内部思考内容丢失
        for (ThinkTagParser.Segment seg : parser.flush(parser.isInsideThink())) {
            if (seg.isNormalText()) {
                emitter.emitText(seg.text);
            } else {
                emitter.emitReasonContent(seg.text);
            }
        }
    }
}
