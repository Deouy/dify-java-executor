package com.dify.workflow.nodes.agent;

import com.dify.workflow.model.ChatRequest;
import com.dify.workflow.model.Java8Compat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct (Reasoning + Acting) 策略。
 * 使用 ReAct prompt 模式，让 LLM 自主决定工具调用。
 */
public class ReActStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(ReActStrategy.class);

    // ReAct 输出格式的正则表达式
    private static final Pattern THOUGHT_PATTERN = Pattern.compile(
            "Thought:\\s*(.*?)\\s*(?=Action:|Final Answer:)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_PATTERN = Pattern.compile(
            "Action:\\s*(\\w+)\\s*(?:\\[([^\\]]+)\\])?",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern FINAL_ANSWER_PATTERN = Pattern.compile(
            "Final Answer:\\s*(.*)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    @Override
    public String execute(AgentExecutionContext context, String query, String systemPrompt) {
        log.info("ReActStrategy executing with {} tools", context.getTools().size());

        // 构建 ReAct 系统提示词
        String reactPrompt = buildReActPrompt(context.getTools(), systemPrompt);

        // 构建初始消息列表
        List<ChatRequest.Message> messages = new ArrayList<>();
        messages.add(ChatRequest.Message.system(reactPrompt));
        messages.add(ChatRequest.Message.user(query));

        int iteration = 0;
        int maxIterations = context.getMaxIterations();

        while (iteration < maxIterations) {
            iteration++;
            log.debug("ReAct iteration {}/{}", iteration, maxIterations);

            // 调用 LLM
            ChatRequest request = ChatRequest.builder()
                    .model(context.getModelName())
                    .messages(messages)
                    .build();

            com.dify.workflow.model.LlmCallResult result = context.callLlm(context.getModelProvider(), context.getModelName(), request);

            if (!result.isSuccess()) {
                throw new RuntimeException("LLM call failed: " + result.errorMessage());
            }

            String content = result.content();
            log.debug("ReAct LLM response:\n{}", content);

            // 添加 assistant 消息
            messages.add(ChatRequest.Message.assistant(content));

            // 解析 ReAct 输出
            ParsedReActOutput parsed = parseReActOutput(content);

            if (parsed.isFinalAnswer) {
                log.info("ReAct finished with final answer");
                return parsed.finalAnswer;
            }

            if (parsed.action == null) {
                // 无法解析动作，继续循环或返回当前内容
                log.warn("Cannot parse action from response, returning content");
                return content;
            }

            // 执行工具
            log.info("ReAct executing action: {} with input: {}", parsed.action, parsed.actionInput);

            Object toolResult;
            try {
                Map<String, Object> args = parseActionInput(parsed.actionInput);
                toolResult = context.executeTool(parsed.action, args);
            } catch (Exception e) {
                log.error("Tool execution failed: {} - {}", parsed.action, e.getMessage());
                toolResult = "Error: " + e.getMessage();
            }

            String resultStr = toolResult != null ? toolResult.toString() : "";
            log.debug("Tool {} result: {}", parsed.action, resultStr);

            // 添加工具结果到消息
            messages.add(ChatRequest.Message.user("Observation: " + resultStr));
        }

        log.warn("ReAct reached max iterations ({})", maxIterations);

        // 返回最后一条消息的内容
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatRequest.Message msg = messages.get(i);
            if ("assistant".equals(msg.role()) && msg.content() != null && !msg.content().isEmpty()) {
                return msg.content();
            }
        }

        return "";
    }

    /**
     * 构建 ReAct 提示词。
     */
    private String buildReActPrompt(List<ToolDefinition> tools, String systemPrompt) {
        StringBuilder prompt = new StringBuilder();

        // 添加原始系统提示词
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            prompt.append(systemPrompt).append("\n\n");
        }

        // 添加 ReAct 指令
        prompt.append("You are a helpful assistant that uses tools to answer questions.\n\n");
        prompt.append("You have access to the following tools:\n\n");

        // 添加工具列表
        for (ToolDefinition tool : tools) {
            prompt.append("- ").append(tool.getName()).append(": ");
            if (tool.getDescription() != null) {
                prompt.append(tool.getDescription());
            }
            prompt.append("\n");
            prompt.append("  Parameters: ").append(tool.getParameters() != null ? tool.getParameters() : "none").append("\n\n");
        }

        // 添加输出格式说明
        prompt.append("\nFor each user request, you must respond in the following format:\n\n");
        prompt.append("Thought: [your reasoning about what to do]\n");
        prompt.append("Action: [the name of the tool to use, or 'Final Answer' if done]\n");
        prompt.append("Action Input: [the input parameters as JSON, e.g. {\"param1\": \"value1\", \"param2\": 123}]\n\n");
        prompt.append("After the action, you will receive an Observation with the result.\n");
        prompt.append("Continue reasoning and acting until you reach a Final Answer.\n\n");
        prompt.append("When you have the final answer, respond with:\n");
        prompt.append("Thought: [your final reasoning]\n");
        prompt.append("Final Answer: [your response to the user's question]");

        return prompt.toString();
    }

    /**
     * 解析 ReAct 输出。
     */
    private ParsedReActOutput parseReActOutput(String content) {
        ParsedReActOutput result = new ParsedReActOutput();

        // 检查是否是 Final Answer
        Matcher finalMatcher = FINAL_ANSWER_PATTERN.matcher(content);
        if (finalMatcher.find()) {
            result.isFinalAnswer = true;
            result.finalAnswer = finalMatcher.group(1).trim();
            return result;
        }

        // 提取 Action
        Matcher actionMatcher = ACTION_PATTERN.matcher(content);
        if (actionMatcher.find()) {
            result.action = actionMatcher.group(1).trim();
            result.actionInput = actionMatcher.group(2);
        }

        // 提取 Thought
        Matcher thoughtMatcher = THOUGHT_PATTERN.matcher(content);
        if (thoughtMatcher.find()) {
            result.thought = thoughtMatcher.group(1).trim();
        }

        return result;
    }

    /**
     * 解析 Action Input 为 JSON 对象。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseActionInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Java8Compat.mapOf();
        }

        input = input.trim();

        // 如果已经是 JSON 对象格式，直接解析
        if (input.startsWith("{")) {
            try {
                return com.alibaba.fastjson2.JSON.parseObject(input);
            } catch (Exception e) {
                log.warn("Failed to parse action input as JSON: {}", input);
            }
        }

        // 尝试解析为 key=value 对
        Map<String, Object> result = new java.util.HashMap<>();

        // 处理 JSON 对象格式的字符串
        if (input.startsWith("{") && input.endsWith("}")) {
            input = input.substring(1, input.length() - 1);
        }

        // 简单解析 key=value 对
        String[] pairs = input.split(",");
        for (String pair : pairs) {
            int eqIndex = pair.indexOf('=');
            if (eqIndex > 0) {
                String key = pair.substring(0, eqIndex).trim();
                String value = pair.substring(eqIndex + 1).trim();
                // 去除引号
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * ReAct 解析结果。
     */
    private static class ParsedReActOutput {
        String thought;
        String action;
        String actionInput;
        boolean isFinalAnswer = false;
        String finalAnswer = "";
    }
}
