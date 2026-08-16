package com.dify.workflow.nodes.thinking;

import com.dify.workflow.model.StreamDelta;
import com.dify.workflow.nodes.ThinkTagParser;

/**
 * DeepSeek 分离策略:处理两种可能的"思考内容"返回方式。
 *
 * <h2>DeepSeek 的两种返回方式</h2>
 *
 * <p>实际测试中发现 DeepSeek V4-Flash 等思考模型的响应格式并不统一:</p>
 * <ul>
 *   <li><b>方式 A:独立 reasoning_content 字段</b>(deepseek-reasoner / 启用了独立推理输出)
 *       <pre>{@code
 * {"delta": {
 *     "content":          "正文片段",
 *     "reasoning_content": "思考片段"
 * }}
 *       }</pre>
 *   </li>
 *   <li><b>方式 B:think 标签包裹</b>(deepseek-v4-flash / 部分代理转发场景)
 *       <pre>{@code
 * {"delta": {
 *     "content": "<think>我需要思考</think>正文片段"
 * }}
 *       }</pre>
 *   </li>
 * </ul>
 *
 * <p>本策略同时处理这两种格式:
 * <ol>
 *   <li>优先看 {@code reasoning_content} 字段,有就直接 emit 到 reason_content</li>
 *   <li>{@code content} 字段也用 {@link ThinkTagParser} 解析,
 *       标签内部 emit 到 reason_content,标签外部 emit 到 text</li>
 *   <li>两者可同时存在(DeepSeek 可能在 reasoning_content 已输出的同时,
 *       content 里仍带 <think> 标签首尾;此时按各自分发,不重复)</li>
 * </ol>
 *
 * <p>适用条件:</p>
 * <ul>
 *   <li>YAML LLM 节点配置 {@code reasoning_format: separated}</li>
 *   <li>LLM provider 是 deepseek 系列</li>
 * </ul>
 */
public class DeepSeekSeparatedStrategy implements ThinkingContentStrategy {

    private final ThinkTagParser parser = new ThinkTagParser();

    @Override
    public void onDelta(StreamDelta delta, DeltaEmitter emitter) {
        if (delta == null) return;

        // 1. reasoning_content 字段(方式 A) → 直接 emit 到 reason_content
        if (delta.reasoningContent() != null && !delta.reasoningContent().isEmpty()) {
            emitter.emitReasonContent(delta.reasoningContent());
        }

        // 2. content 字段:用 ThinkTagParser 解析,标签内部→reason_content,外部→text
        //    (覆盖方式 B 的场景,以及方式 A 中 content 还含 <think> 残留的情况)
        if (delta.content() != null && !delta.content().isEmpty()) {
            for (ThinkTagParser.Segment seg : parser.consume(delta.content())) {
                if (seg.isNormalText()) {
                    emitter.emitText(seg.text);
                } else {
                    // 标签内部内容 → reason_content selector
                    emitter.emitReasonContent(seg.text);
                }
            }
        }
    }

    @Override
    public void flush(DeltaEmitter emitter) {
        // 强制 flush ThinkTagParser 残留 buffer
        // (防止 delta 最后一段停在 <think> 中间时,内部思考内容丢失)
        for (ThinkTagParser.Segment seg : parser.flush(parser.isInsideThink())) {
            if (seg.isNormalText()) {
                emitter.emitText(seg.text);
            } else {
                emitter.emitReasonContent(seg.text);
            }
        }
    }
}