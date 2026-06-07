package com.dify.workflow.nodes.agent;

import com.alibaba.fastjson2.JSONObject;
import com.dify.workflow.model.Java8Compat;
import com.dify.workflow.model.McpServerConfig;
import com.dify.workflow.nodes.agent.mcp.McpClient;
import com.dify.workflow.nodes.agent.mcp.McpTool;
import com.dify.workflow.nodes.agent.mcp.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * MCP (Model Context Protocol) 策略。
 *
 * 连接外部 MCP Server，发现工具并通过 FunctionCall 执行。
 * 流程:
 * 1. 遍历 context.getTools() 中类型为 MCP 的工具
 * 2. 通过 provider_name 从 context.getMcpConfigs() 获取 MCP Server 配置
 * 3. 连接 MCP Server，执行 initialize + tools/list
 * 4. 将发现的工具转换为 ToolDefinition 添加到 context
 * 5. 委托给 FunctionCallStrategy 执行 LLM function calling
 */
public class McpStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(McpStrategy.class);

    private final FunctionCallStrategy functionCallStrategy = new FunctionCallStrategy();
    private final McpToolRegistry registry = new McpToolRegistry();

    @Override
    public String execute(AgentExecutionContext context, String query, String systemPrompt) {
        log.info("McpStrategy executing with {} tools", context.getTools().size());

        // 1. 连接所有 MCP Server 并发现工具
        discoverMcpTools(context);

        // 2. 委托给 FunctionCallStrategy 执行
        return functionCallStrategy.execute(context, query, systemPrompt);
    }

    /**
     * 发现 MCP 工具并添加到 context。
     */
    private void discoverMcpTools(AgentExecutionContext context) {
        Map<String, McpServerConfig> mcpConfigs = context.getMcpConfigs();
        if (mcpConfigs == null || mcpConfigs.isEmpty()) {
            log.debug("No MCP configs available");
            return;
        }

        // 遍历工具列表，找出 MCP 类型的工具
        for (ToolDefinition tool : context.getTools()) {
            if (tool.getType() != ToolDefinition.ToolType.MCP) {
                continue;
            }

            String providerName = tool.getProviderName();
            if (providerName == null || providerName.isEmpty()) {
                log.warn("MCP tool '{}' has no provider_name, skipping", tool.getName());
                continue;
            }

            McpServerConfig config = mcpConfigs.get(providerName);
            if (config == null) {
                log.warn("MCP tool '{}': no config found for provider '{}', available: {}",
                        tool.getName(), providerName, mcpConfigs.keySet());
                continue;
            }

            try {
                // 注册 MCP Server（如果尚未注册）
                if (registry.getClient(providerName) == null) {
                    registry.registerServer(providerName, config);
                }

                // 发现 MCP 工具并转换为 ToolDefinition
                List<McpTool> discoveredTools = registry.getTools(providerName);
                for (McpTool mcpTool : discoveredTools) {
                    // 转换为 Dify 的 ToolDefinition 并替换执行器为 MCP 调用
                    ToolDefinition convertedTool = ToolDefinition.builder()
                            .name(mcpTool.getName())
                            .description(mcpTool.getDescription())
                            .parameters(convertSchemaToParams(mcpTool.getInputSchema()))
                            .type(ToolDefinition.ToolType.MCP)
                            .providerName(providerName)
                            .executor((args) -> executeMcpTool(providerName, mcpTool.getName(), args))
                            .build();

                    // 避免重复添加
                    boolean alreadyExists = context.getTools().stream()
                            .anyMatch(t -> t.getName().equals(mcpTool.getName()));
                    if (!alreadyExists) {
                        context.addTool(convertedTool);
                        log.info("Added MCP tool: {} from provider '{}'", mcpTool.getName(), providerName);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to discover MCP tools for provider '{}': {}", providerName, e.getMessage(), e);
            }
        }
    }

    /**
     * 执行 MCP 工具调用。
     */
    @SuppressWarnings("unchecked")
    private Object executeMcpTool(String providerName, String toolName, Map<String, Object> args) {
        try {
            McpClient client = registry.getClient(providerName);
            if (client == null) {
                return "MCP client not found for provider: " + providerName;
            }

            com.dify.workflow.nodes.agent.mcp.McpCallResult result = client.callTool(toolName, args);
            if (result == null) {
                return "MCP call returned null";
            }
            return result.toString();
        } catch (Exception e) {
            log.error("MCP tool call failed: {}.{} - {}", providerName, toolName, e.getMessage(), e);
            return "MCP tool call failed: " + e.getMessage();
        }
    }

    /**
     * 将 MCP 的 JSON Schema 转换为 ToolDefinition 的参数格式。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertSchemaToParams(JSONObject inputSchema) {
        if (inputSchema == null || !inputSchema.containsKey("properties")) {
            return Java8Compat.mapOf();
        }
        return inputSchema.getJSONObject("properties").toJavaObject(Map.class);
    }
}
