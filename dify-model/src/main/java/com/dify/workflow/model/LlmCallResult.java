package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Objects;

/**
 * Result of an LLM call.
 */
public final class LlmCallResult {
    @JSONField(name = "content")
    private final String content;
    @JSONField(name = "model")
    private final String model;
    @JSONField(name = "prompt_tokens")
    private final Integer promptTokens;
    @JSONField(name = "completion_tokens")
    private final Integer completionTokens;
    @JSONField(name = "total_tokens")
    private final Integer totalTokens;
    @JSONField(name = "finish_reason")
    private final String finishReason;
    @JSONField(name = "success")
    private final Boolean success;
    @JSONField(name = "error_message")
    private final String errorMessage;
    @JSONField(name = "tool_calls")
    private final List<ChatRequest.ToolCall> toolCalls;
    @JSONField(name = "assistant_messages")
    private final List<ChatRequest.Message> assistantMessages;
    /**
     * 思维链内容(DeepSeek {@code reasoning_content} / vLLM {@code reasoning})。
     * 非流式响应解析时填充;流式路径通常经 StreamDelta 单独累积。
     */
    @JSONField(name = "reasoning_content")
    private final String reasoningContent;

    public LlmCallResult(String content, String model, Integer promptTokens, Integer completionTokens,
                         Integer totalTokens, String finishReason, Boolean success, String errorMessage,
                         List<ChatRequest.ToolCall> toolCalls, List<ChatRequest.Message> assistantMessages) {
        this(content, model, promptTokens, completionTokens, totalTokens, finishReason, success,
                errorMessage, toolCalls, assistantMessages, null);
    }

    public LlmCallResult(String content, String model, Integer promptTokens, Integer completionTokens,
                         Integer totalTokens, String finishReason, Boolean success, String errorMessage,
                         List<ChatRequest.ToolCall> toolCalls, List<ChatRequest.Message> assistantMessages,
                         String reasoningContent) {
        this.content = content;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.finishReason = finishReason;
        this.success = success;
        this.errorMessage = errorMessage;
        this.toolCalls = toolCalls;
        this.assistantMessages = assistantMessages;
        this.reasoningContent = reasoningContent;
    }

    public String content() { return content; }
    public String model() { return model; }
    public Integer promptTokens() { return promptTokens; }
    public Integer completionTokens() { return completionTokens; }
    public Integer totalTokens() { return totalTokens; }
    public String finishReason() { return finishReason; }
    public Boolean success() { return success; }
    public String errorMessage() { return errorMessage; }
    public List<ChatRequest.ToolCall> toolCalls() { return toolCalls; }
    public List<ChatRequest.Message> assistantMessages() { return assistantMessages; }
    public String reasoningContent() { return reasoningContent; }

    public String getContent() { return content; }
    public String getModel() { return model; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public String getFinishReason() { return finishReason; }
    public Boolean getSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public List<ChatRequest.ToolCall> getToolCalls() { return toolCalls; }
    public List<ChatRequest.Message> getAssistantMessages() { return assistantMessages; }
    public String getReasoningContent() { return reasoningContent; }

    public boolean isSuccess() { return success != null && success; }

    public static LlmCallResult success(String content) {
        return new LlmCallResult(content, null, null, null, null, "stop", true, null, null, null);
    }

    public static LlmCallResult success(String content, String model, Integer promptTokens,
                                       Integer completionTokens, Integer totalTokens) {
        return new LlmCallResult(content, model, promptTokens, completionTokens, totalTokens, "stop", true, null, null, null);
    }

    public static LlmCallResult failure(String errorMessage) {
        return new LlmCallResult(null, null, null, null, null, null, false, errorMessage, null, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LlmCallResult)) return false;
        LlmCallResult that = (LlmCallResult) o;
        return Objects.equals(content, that.content)
                && Objects.equals(model, that.model)
                && Objects.equals(promptTokens, that.promptTokens)
                && Objects.equals(completionTokens, that.completionTokens)
                && Objects.equals(totalTokens, that.totalTokens)
                && Objects.equals(finishReason, that.finishReason)
                && Objects.equals(success, that.success)
                && Objects.equals(errorMessage, that.errorMessage)
                && Objects.equals(toolCalls, that.toolCalls)
                && Objects.equals(assistantMessages, that.assistantMessages)
                && Objects.equals(reasoningContent, that.reasoningContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, model, promptTokens, completionTokens, totalTokens,
                finishReason, success, errorMessage, toolCalls, assistantMessages, reasoningContent);
    }

    @Override
    public String toString() {
        return String.format("LlmCallResult[content=%s, model=%s, promptTokens=%s, completionTokens=%s, totalTokens=%s, finishReason=%s, success=%s, errorMessage=%s, toolCalls=%s, assistantMessages=%s, reasoningContent=%s]",
                content, model, promptTokens, completionTokens, totalTokens, finishReason, success, errorMessage, toolCalls, assistantMessages, reasoningContent);
    }
}
