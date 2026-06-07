package com.dify.workflow.model;

import java.util.Objects;

/**
 * LLM 流式输出单次增量。
 *
 * <p>由 {@link LlmService#callStream} 的 onDelta 回调传入,每次 LLM 服务
 * 推送一个 SSE chunk 触发一次。content 通常是 1-N 个字符的文本片段;
 * finishReason 标识流结束原因 ("stop"=正常完成, "error:..."=异常)。</p>
 */
public final class StreamDelta {
    private final String content;
    private final int index;
    private final String finishReason;

    public StreamDelta(String content, int index, String finishReason) {
        this.content = content;
        this.index = index;
        this.finishReason = finishReason;
    }

    public String content() { return content; }
    public int index() { return index; }
    public String finishReason() { return finishReason; }

    public String getContent() { return content; }
    public int getIndex() { return index; }
    public String getFinishReason() { return finishReason; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StreamDelta)) return false;
        StreamDelta that = (StreamDelta) o;
        return index == that.index
                && Objects.equals(content, that.content)
                && Objects.equals(finishReason, that.finishReason);
    }

    @Override
    public int hashCode() { return Objects.hash(content, index, finishReason); }

    @Override
    public String toString() {
        return String.format("StreamDelta[index=%d, content=%s, finishReason=%s]",
                index, content, finishReason);
    }
}
