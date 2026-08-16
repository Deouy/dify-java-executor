package com.dify.workflow.model;

import java.util.Objects;

/**
 * LLM 流式输出单次增量。
 *
 * <p>由 {@link LlmService#callStream} 的 onDelta 回调传入,每次 LLM 服务
 * 推送一个 SSE chunk 触发一次。content 通常是 1-N 个字符的文本片段;
 * finishReason 标识流结束原因 ("stop"=正常完成, "error:..."=异常)。</p>
 *
 * <p>关键字段(2026-08-16 增):
 * <ul>
 *   <li><b>reasoningContent</b> — DeepSeek-R1/V4-Flash 等思考模型的思维链内容。
 *       DeepSeek API 在 SSE delta 中同时返回
 *       {@code {"delta": {"content": "...", "reasoning_content": "..."}}},
 *       两者会独立递增。该字段为 null 时表示本 chunk 没有思维链增量。</li>
 * </ul>
 * </p>
 */
public final class StreamDelta {
    private final String content;
    private final int index;
    private final String finishReason;
    /**
     * 思维链增量(DeepSeek R1/V4 系列特有)。
     * 与 content 独立递增;同一 chunk 中可同时存在两者,
     * 消费方(LlmNode)需分别 emit 到不同 selector 让下游区分 text / reason_content。
     */
    private final String reasoningContent;

    /**
     * @deprecated 仅保留三参构造以兼容老代码;新代码应使用
     *     {@link #StreamDelta(String, int, String, String)} 显式传 reasoningContent。
     */
    @Deprecated
    public StreamDelta(String content, int index, String finishReason) {
        this(content, index, finishReason, null);
    }

    public StreamDelta(String content, int index, String finishReason, String reasoningContent) {
        this.content = content;
        this.index = index;
        this.finishReason = finishReason;
        this.reasoningContent = reasoningContent;
    }

    public String content() { return content; }
    public int index() { return index; }
    public String finishReason() { return finishReason; }
    public String reasoningContent() { return reasoningContent; }

    public String getContent() { return content; }
    public int getIndex() { return index; }
    public String getFinishReason() { return finishReason; }
    public String getReasoningContent() { return reasoningContent; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StreamDelta)) return false;
        StreamDelta that = (StreamDelta) o;
        return index == that.index
                && Objects.equals(content, that.content)
                && Objects.equals(finishReason, that.finishReason)
                && Objects.equals(reasoningContent, that.reasoningContent);
    }

    @Override
    public int hashCode() { return Objects.hash(content, index, finishReason, reasoningContent); }

    @Override
    public String toString() {
        return String.format("StreamDelta[index=%d, content=%s, reasoningContent=%s, finishReason=%s]",
                index, content, reasoningContent, finishReason);
    }
}