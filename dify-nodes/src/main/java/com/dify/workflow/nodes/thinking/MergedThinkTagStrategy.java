package com.dify.workflow.nodes.thinking;

import com.dify.workflow.model.StreamDelta;
import com.dify.workflow.nodes.ThinkTagParser;

/**
 * 合并流策略:把 <think>...</think> 标签拦截并把内部内容作为普通正文输出。
 *
 * <p>适用于 YAML LLM 节点未配置 {@code reasoning_format} 或配置为非 "separated" 的场景。
 * 用户看到的输出是"完整文本流",不区分思考和正文。</p>
 *
 * <p>行为细节:</p>
 * <ul>
 *   <li>对 SSE delta 的 {@code content} 字段用 {@link ThinkTagParser} 解析</li>
 *   <li>{@code <think>...</think>} 内的文字作为正常文本 emit(标签本身被剥离)</li>
 *   <li>{@code reasoning_content} 字段(若有)也作为正文 emit,
 *       紧接在 {@code content} 之后 — 模拟"模型完整回答"的效果</li>
 *   <li>流结束时调 {@link ThinkTagParser#flush} 防止 buffer 残留</li>
 * </ul>
 *
 * <p>兼容性:此策略是"默认行为",适合绝大多数场景。
 * 即便模型返回了 {@code reasoning_content} 字段(DeepSeek),
 * 用户也只会看到一个统一的文本流。</p>
 */
public class MergedThinkTagStrategy implements ThinkingContentStrategy {

    private final ThinkTagParser parser = new ThinkTagParser();

    @Override
    public void onDelta(StreamDelta delta, DeltaEmitter emitter) {
        if (delta == null) return;
        // reasoning_content 字段(DeepSeek R1 / V4 等):作为正文一部分输出
        if (delta.reasoningContent() != null && !delta.reasoningContent().isEmpty()) {
            emitter.emitText(delta.reasoningContent());
        }
        // content 字段:解析 <think> 标签,内部内容也作为正文输出
        if (delta.content() != null && !delta.content().isEmpty()) {
            for (ThinkTagParser.Segment seg : parser.consume(delta.content())) {
                // 不管是 normal 还是 thinking 段,统一当作文本 emit(标签本身已被 parser 剥掉)
                emitter.emitText(seg.text);
            }
        }
    }

    @Override
    public void flush(DeltaEmitter emitter) {
        // 强制 flush 残留 buffer(防止最后一段 <think> 块内文字丢失)
        for (ThinkTagParser.Segment seg : parser.flush(parser.isInsideThink())) {
            emitter.emitText(seg.text);
        }
    }
}