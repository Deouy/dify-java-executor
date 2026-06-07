package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Chat request for LLM calls.
 */
public final class ChatRequest {
    @JSONField(name = "model")
    private final String model;
    @JSONField(name = "messages")
    private final List<Message> messages;
    @JSONField(name = "temperature")
    private final Double temperature;
    @JSONField(name = "max_tokens")
    private final Integer maxTokens;
    @JSONField(name = "top_p")
    private final Double topP;
    @JSONField(name = "extra_parameters")
    private final Map<String, Object> extraParameters;
    @JSONField(name = "tools")
    private final List<Map<String, Object>> tools;
    @JSONField(name = "stream")
    private final boolean stream;

    public ChatRequest(String model, List<Message> messages, Double temperature, Integer maxTokens,
                       Double topP, Map<String, Object> extraParameters, List<Map<String, Object>> tools,
                       boolean stream) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.topP = topP;
        this.extraParameters = extraParameters;
        this.tools = tools;
        this.stream = stream;
    }

    public String model() { return model; }
    public List<Message> messages() { return messages; }
    public Double temperature() { return temperature; }
    public Integer maxTokens() { return maxTokens; }
    public Double topP() { return topP; }
    public Map<String, Object> extraParameters() { return extraParameters; }
    public List<Map<String, Object>> tools() { return tools; }
    public boolean stream() { return stream; }

    public String getModel() { return model; }
    public List<Message> getMessages() { return messages; }
    public Double getTemperature() { return temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public Double getTopP() { return topP; }
    public Map<String, Object> getExtraParameters() { return extraParameters; }
    public List<Map<String, Object>> getTools() { return tools; }
    public boolean getStream() { return stream; }

    public static Builder builder() { return new Builder(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatRequest)) return false;
        ChatRequest that = (ChatRequest) o;
        return Objects.equals(model, that.model)
                && Objects.equals(messages, that.messages)
                && Objects.equals(temperature, that.temperature)
                && Objects.equals(maxTokens, that.maxTokens)
                && Objects.equals(topP, that.topP)
                && Objects.equals(extraParameters, that.extraParameters)
                && Objects.equals(tools, that.tools)
                && stream == that.stream;
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, messages, temperature, maxTokens, topP, extraParameters, tools, stream);
    }

    @Override
    public String toString() {
        return String.format("ChatRequest[model=%s, messages=%s, temperature=%s, maxTokens=%s, topP=%s, extraParameters=%s, tools=%s, stream=%s]",
                model, messages, temperature, maxTokens, topP, extraParameters, tools, stream);
    }

    public static final class Builder {
        private String model;
        private List<Message> messages;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Map<String, Object> extraParameters;
        private List<Map<String, Object>> tools;
        private boolean stream = false;

        public Builder model(String model) { this.model = model; return this; }
        public Builder messages(List<Message> messages) { this.messages = messages; return this; }
        public Builder temperature(Double temperature) { this.temperature = temperature; return this; }
        public Builder maxTokens(Integer maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder topP(Double topP) { this.topP = topP; return this; }
        public Builder extraParameters(Map<String, Object> extraParameters) { this.extraParameters = extraParameters; return this; }
        public Builder tools(List<Map<String, Object>> tools) { this.tools = tools; return this; }
        public Builder stream(boolean stream) { this.stream = stream; return this; }

        public ChatRequest build() {
            return new ChatRequest(model, messages, temperature, maxTokens, topP, extraParameters, tools, stream);
        }
    }

    /**
     * Chat message.
     * 支持 tool_calls 和 tool_call_id 用于 Function Calling。
     */
    public static final class Message {
        @JSONField(name = "role")
        private final String role;
        @JSONField(name = "content")
        private final String content;
        @JSONField(name = "name")
        private final String name;
        @JSONField(name = "tool_calls")
        private final List<ToolCall> toolCalls;
        @JSONField(name = "tool_call_id")
        private final String toolCallId;

        public Message(String role, String content, String name, List<ToolCall> toolCalls, String toolCallId) {
            this.role = role;
            this.content = content;
            this.name = name;
            this.toolCalls = toolCalls;
            this.toolCallId = toolCallId;
        }

        public String role() { return role; }
        public String content() { return content; }
        public String name() { return name; }
        public List<ToolCall> toolCalls() { return toolCalls; }
        public String toolCallId() { return toolCallId; }

        public String getRole() { return role; }
        public String getContent() { return content; }
        public String getName() { return name; }
        public List<ToolCall> getToolCalls() { return toolCalls; }
        public String getToolCallId() { return toolCallId; }

        public static Message system(String content) { return new Message("system", content, null, null, null); }
        public static Message user(String content) { return new Message("user", content, null, null, null); }
        public static Message assistant(String content) { return new Message("assistant", content, null, null, null); }
        public static Message tool(String toolCallId, String content) { return new Message("tool", content, null, null, toolCallId); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Message)) return false;
            Message that = (Message) o;
            return Objects.equals(role, that.role)
                    && Objects.equals(content, that.content)
                    && Objects.equals(name, that.name)
                    && Objects.equals(toolCalls, that.toolCalls)
                    && Objects.equals(toolCallId, that.toolCallId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(role, content, name, toolCalls, toolCallId);
        }

        @Override
        public String toString() {
            return String.format("Message[role=%s, content=%s, name=%s, toolCalls=%s, toolCallId=%s]",
                    role, content, name, toolCalls, toolCallId);
        }
    }

    /**
     * Tool call for Function Calling.
     */
    public static final class ToolCall {
        @JSONField(name = "id")
        private final String id;
        @JSONField(name = "function")
        private final Function function;

        public ToolCall(String id, Function function) {
            this.id = id;
            this.function = function;
        }

        public String id() { return id; }
        public Function function() { return function; }

        public String getId() { return id; }
        public Function getFunction() { return function; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ToolCall)) return false;
            ToolCall that = (ToolCall) o;
            return Objects.equals(id, that.id) && Objects.equals(function, that.function);
        }

        @Override
        public int hashCode() { return Objects.hash(id, function); }

        @Override
        public String toString() { return String.format("ToolCall[id=%s, function=%s]", id, function); }
    }

    /**
     * Function definition in a tool call.
     */
    public static final class Function {
        @JSONField(name = "name")
        private final String name;
        @JSONField(name = "arguments")
        private final String arguments;

        public Function(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String name() { return name; }
        public String arguments() { return arguments; }

        public String getName() { return name; }
        public String getArguments() { return arguments; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Function)) return false;
            Function that = (Function) o;
            return Objects.equals(name, that.name) && Objects.equals(arguments, that.arguments);
        }

        @Override
        public int hashCode() { return Objects.hash(name, arguments); }

        @Override
        public String toString() { return String.format("Function[name=%s, arguments=%s]", name, arguments); }
    }
}
