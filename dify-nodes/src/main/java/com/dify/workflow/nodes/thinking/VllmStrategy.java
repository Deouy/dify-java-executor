package com.dify.workflow.nodes.thinking;

import com.dify.workflow.model.StreamDelta;
import com.dify.workflow.nodes.ThinkTagParser;

/**
 * vLLM 思考内容处理策略。
 *
 * <p>vLLM 把思考内容放在 {@code content} 字段里并用 {@code <think>...</think>} 标签包裹,
 * SSE 响应中通常没有独立的 {@code reasoning_content} 字段。
 * 所以本策略完全依赖 {@link ThinkTagParser} 解析 content。</p>
 *
 * <p>行为:</p>
 * <ul>
 *   <li>解析 {@code content} 中的 <think>...</think> 标签</li>
 *   <li>标签内部 emit 到 {@code reason_content} selector</li>
 *   <li>标签外部 emit 到 {@code text} selector</li>
 *   <li>若 delta 带 {@code reasoning_content} 字段(部分 vLLM 代理可能扩展),
 *       优先 emit 到 {@code reason_content}</li>
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

        // 路径 1:vLLM 代理若额外提供 reasoning_content 字段,优先用之
        if (delta.reasoningContent() != null && !delta.reasoningContent().isEmpty()) {
            emitter.emitReasonContent(delta.reasoningContent());
        }

        // 路径 2:解析 content 中的 <think> 标签
        if (delta.content() != null && !delta.content().isEmpty()) {
            if (delta.reasoningContent() != null && !delta.reasoningContent().isEmpty()) {
                // reasoning_content 已发,content 是纯文本,不再做标签解析(避免重复)
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