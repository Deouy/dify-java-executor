package com.dify.workflow.nodes.thinking;

import com.dify.workflow.model.StreamDelta;

/**
 * 思考内容(思维链)处理策略。
 *
 * <p>由 {@link com.dify.workflow.nodes.LlmNode} 在 LLM 流式调用时调用,
 * 把每个 {@link StreamDelta} 转成下游可消费的 emit 事件。
 *
 * <h2>设计动机</h2>
 *
 * <p>不同 LLM 提供商返回"思考内容"的方式不同:</p>
 * <ul>
 *   <li><b>DeepSeek V4-Flash / R1</b>:SSE delta 同时返回
 *       {@code content} 和 {@code reasoning_content} 两个独立字段</li>
 *   <li><b>vLLM / Qwen 等</b>:把思考内容放在 {@code content} 里,
 *       用 {@code <think>...</think>} 标签包裹</li>
 *   <li><b>OpenAI o1 / Claude</b>:类似 vLLM,用标签包裹</li>
 * </ul>
 *
 * <p>同时,YAML LLM 节点上的 {@code reasoning_format} 字段控制是否分离思考内容:</p>
 * <ul>
 *   <li>{@code reasoning_format=separated}:思考内容单独路由到
 *       {@code selector=[nodeId, "reason_content"]},正文路由到
 *       {@code selector=[nodeId, "text"]}</li>
 *   <li>其他值或未配置:所有内容(包括思考)都输出到
 *       {@code selector=[nodeId, "text"]}(合并流),下游消费者看不到分离</li>
 * </ul>
 *
 * <p>策略模式让新增 provider 时只需实现 {@link ThinkingContentStrategy},
 * 无需修改 {@link com.dify.workflow.nodes.LlmNode} 主体逻辑。</p>
 *
 * @see com.dify.workflow.model.StreamDelta#reasoningContent()
 * @see ThinkTagParser
 */
public interface ThinkingContentStrategy {

    /**
     * 处理一个流式 delta。
     *
     * <p>实现方应解析 delta 中的 {@code content} 和 {@code reasoningContent} 字段,
     * 通过 {@link DeltaEmitter} 把内容分发到正确的 selector。</p>
     *
     * @param delta LLM 返回的单次增量
     * @param emitter 内容发送器,负责实际 emit 到 SSE 流
     */
    void onDelta(StreamDelta delta, DeltaEmitter emitter);

    /**
     * 流结束时强制 flush 缓冲区内容(防止 <think> 块内最后一段丢失)。
     *
     * <p>仅当实现使用了内部 buffer(如 {@link ThinkTagParser})时才需要。
     * 无状态实现可留空。</p>
     */
    void flush(DeltaEmitter emitter);
}