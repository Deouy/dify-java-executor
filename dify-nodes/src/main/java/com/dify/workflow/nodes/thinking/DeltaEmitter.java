package com.dify.workflow.nodes.thinking;

/**
 * 思考内容发送器。
 *
 * <p>把策略解析出的内容 emit 到对应 selector。
 * 由 {@link com.dify.workflow.nodes.LlmNode} 在循环中调用。</p>
 *
 * <p>实现方决定 selector 路由。当前 LlmNode 把 {@link #emitText} 路由到
 * {@code [nodeId, "text"]}(最终经协调器重写到 answer 节点),
 * {@link #emitReasonContent} 路由到 {@code [nodeId, "reason_content"]}(透传给监听器)。</p>
 */
public interface DeltaEmitter {

    /**
     * emit 正文文本(走 {@code [nodeId, "text"]} selector)。
     */
    void emitText(String text);

    /**
     * emit 思考内容(走 {@code [nodeId, "reason_content"]} selector)。
     */
    void emitReasonContent(String text);
}