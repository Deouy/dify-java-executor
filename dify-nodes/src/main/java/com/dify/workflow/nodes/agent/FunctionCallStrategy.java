package com.dify.workflow.nodes.agent;

import com.dify.workflow.model.ChatRequest;
import com.dify.workflow.model.Java8Compat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Function Calling 策略。
 * 使用 LLM 原生的 tool/function calling 功能。
 */
public class FunctionCallStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallStrategy.class);

    @Override
    public String execute(AgentExecutionContext context, String query, String systemPrompt) {
        log.info("FunctionCallStrategy executing with {} tools", context.getTools().size());

        // 构建初始消息列表
        List<ChatRequest.Message> messages = new ArrayList<>();

        // 添加系统提示词
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(ChatRequest.Message.system(systemPrompt));
        }

        // 添加用户查询
        messages.add(ChatRequest.Message.user(query));

        // 构建 OpenAI 格式的 tools
        List<Map<String, Object>> tools = buildToolsDefinition(context.getTools());

        int iteration = 0;
        int maxIterations = context.getMaxIterations();

        while (iteration < maxIterations) {
            iteration++;
            log.debug("FunctionCall iteration {}/{}", iteration, maxIterations);

            // 调用 LLM
            ChatRequest request = ChatRequest.builder()
                    .model(context.getModelName())
                    .messages(messages)
                    .tools(tools == null || tools.isEmpty() ? null : tools)
                    .build();

            com.dify.workflow.model.LlmCallResult result = context.callLlm(context.getModelProvider(), context.getModelName(), request);

            if (!result.isSuccess()) {
                throw new RuntimeException("LLM call failed: " + result.errorMessage());
            }

            String content = result.content();
            List<ChatRequest.Message> assistantMessages = result.getAssistantMessages();

            // 添加 assistant 消息
            if (assistantMessages != null && !assistantMessages.isEmpty()) {
                messages.addAll(assistantMessages);
            } else {
                // 兼容没有 tool_calls 的情况
                messages.add(ChatRequest.Message.assistant(content));
            }

            // 检查是否有工具调用
            List<ChatRequest.ToolCall> toolCalls = result.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                // 没有工具调用，返回内容作为最终结果
                log.debug("No tool calls, returning content as final result");
                return content;
            }

            // 处理工具调用
            for (ChatRequest.ToolCall toolCall : toolCalls) {
                String toolName = toolCall.function().name();
                String arguments = toolCall.function().arguments();

                log.info("Agent calling tool: {}", toolName);

                try {
                    // 解析参数
                    Map<String, Object> args = parseJsonArguments(arguments);

                    // 执行工具
                    Object toolResult = context.executeTool(toolName, args);

                    // 添加工具结果消息
                    String resultStr = toolResult != null ? toolResult.toString() : "";
                    messages.add(ChatRequest.Message.tool(toolCall.id(), resultStr));

                    log.debug("Tool {} result: {}", toolName, resultStr);
                } catch (Exception e) {
                    log.error("Tool execution failed: {} - {}", toolName, e.getMessage());
                    messages.add(ChatRequest.Message.tool(toolCall.id(), "Error: " + e.getMessage()));
                }
            }
        }

        log.warn("FunctionCall reached max iterations ({})", maxIterations);

        // 返回最后一条 assistant 消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatRequest.Message msg = messages.get(i);
            if ("assistant".equals(msg.role()) && msg.content() != null && !msg.content().isEmpty()) {
                return msg.content();
            }
        }

        return "";
    }

    /**
     * 构建 OpenAI 格式的 tools 定义。
     */
    private List<Map<String, Object>> buildToolsDefinition(List<ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (ToolDefinition tool : tools) {
            Map<String, Object> function = new HashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription() != null ? tool.getDescription() : "");

            // 转换参数为 OpenAI 格式
            Map<String, Object> parameters = tool.getParameters();
            if (parameters != null) {
                function.put("parameters", convertToOpenApiSchema(parameters));
            } else {
                // 默认空 schema
                function.put("parameters", Java8Compat.mapOf("type", "object", "properties", Java8Compat.mapOf()));
            }

            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("type", "function");
            toolDef.put("function", function);
            result.add(toolDef);
        }

        return result;
    }

    /**
     * 将参数转换为 OpenAPI schema 格式。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToOpenApiSchema(Map<String, Object> params) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Boolean> required = new HashMap<>();

        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                Map<String, Object> prop = new HashMap<>();

                // 尝试从 value 推断类型
                if (value instanceof String) {
                    prop.put("type", "string");
                    prop.put("description", value.toString());
                } else if (value instanceof Number) {
                    prop.put("type", "number");
                } else if (value instanceof Boolean) {
                    prop.put("type", "boolean");
                } else if (value instanceof Map) {
                    // 如果是复杂对象，进行递归转换
                    Map<String, Object> nestedSchema = convertToOpenApiSchema((Map<String, Object>) value);
                    properties.put(key, nestedSchema);
                    continue;
                } else {
                    prop.put("type", "string");
                }

                properties.put(key, prop);
                required.put(key, true);  // 假设所有参数都是必需的
            }
        }

        schema.put("properties", properties);

        // 添加必需参数列表
        List<String> requiredList = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : required.entrySet()) {
            if (entry.getValue()) {
                requiredList.add(entry.getKey());
            }
        }
        if (!requiredList.isEmpty()) {
            schema.put("required", requiredList);
        }

        return schema;
    }

    /**
     * 解析 JSON 格式的参数。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonArguments(String arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return new HashMap<>();
        }

        try {
            // 尝试解析为 JSON 对象
            return com.alibaba.fastjson2.JSON.parseObject(arguments);
        } catch (Exception e) {
            log.warn("Failed to parse arguments as JSON: {}", arguments);
            // 如果解析失败，尝试作为简单 key=value 对解析
            Map<String, Object> result = new HashMap<>();
            try {
                // 尝试解析为 FastJSON
                Object parsed = com.alibaba.fastjson2.JSON.parse(arguments);
                if (parsed instanceof Map) {
                    return (Map<String, Object>) parsed;
                }
            } catch (Exception ignored) {
            }
            return result;
        }
    }
}
