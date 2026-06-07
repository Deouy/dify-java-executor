package com.dify.workflow.nodes;

import com.dify.workflow.nodes.agent.*;
import com.dify.workflow.model.Java8Compat;
import com.dify.workflow.nodes.agent.mcp.McpClient;
import com.dify.workflow.nodes.agent.mcp.McpToolRegistry;
import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.McpServerConfig;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 节点 - 支持 FunctionCall / ReAct / MCP 三种策略。
 *
 * 工作流程：
 * 1. 读取 agent_strategy_label → 选择策略
 * 2. 从 agent_parameters 提取 model + tools + query
 * 3. 构建 AgentExecutionContext
 * 4. 执行策略
 * 5. 存储结果到变量池
 */
public class AgentNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(AgentNode.class);

    /** MCP 工具注册表缓存（按 provider_name 缓存 McpClient） */
    private final McpToolRegistry mcpRegistry = new McpToolRegistry();

    public AgentNode(String id, DifyNodeData data) {
        super(id, NodeType.AGENT, data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.info("Executing Agent node: {}", id);

        // 1. 获取策略类型
        String strategyLabel = data.agentStrategyLabel();
        if (strategyLabel == null) {
            strategyLabel = "FUNCTIONCALLING";
        }
        strategyLabel = strategyLabel.toUpperCase().trim();
        log.info("Agent strategy: {}", strategyLabel);

        // 2. 选择策略
        AgentStrategy strategy = selectStrategy(strategyLabel);

        // 3. 提取 agent_parameters
        Map<String, Object> params = data.agentParameters();
        if (params == null) {
            params = new HashMap<>();
        }

        // 4. 提取模型配置
        ModelConfig modelConfig = extractModelConfig(params);
        log.info("Agent model: provider={}, name={}", modelConfig.provider, modelConfig.name);

        // 5. 构建 AgentExecutionContext
        AgentExecutionContext ctx = AgentExecutionContext.builder()
                .workflowContext(context)
                .modelProvider(modelConfig.provider)
                .modelName(modelConfig.name)
                .mcpConfigs(context.getMcpConfigs())  // 来自 DifyWorkflowExecutor 的 MCP 配置
                .maxIterations(10)
                .historyStore(context.getHistoryStore())
                .conversationId(context.getConversationId())
                .build();

        // 6. 加载工具
        loadTools(context, params, ctx);

        // 7. 构建查询和系统提示词
        String query = extractQuery(context, params);
        String systemPrompt = buildSystemPrompt(context, params);

        log.debug("Agent query: {}", query);
        log.debug("Agent system prompt: {}", systemPrompt);

        // 8. 执行策略
        String result = strategy.execute(ctx, query, systemPrompt);

        // 9. 存储结果
        context.setVariable(id, "text", result);
        context.setVariable(id, "output", result);

        log.info("Agent node {} completed with result length: {}", id, result.length());
    }

    /**
     * 选择 Agent 策略。
     */
    private AgentStrategy selectStrategy(String label) {
        if ("FUNCTIONCALLING".equals(label)) {
            return new FunctionCallStrategy();
        } else if ("REACT".equals(label)) {
            return new ReActStrategy();
        } else if ("MCP".equals(label)) {
            return new McpStrategy();
        } else {
            log.warn("Unknown agent strategy label: {}, defaulting to FunctionCalling", label);
            return new FunctionCallStrategy();
        }
    }

    /**
     * 提取模型配置。
     */
    @SuppressWarnings("unchecked")
    private ModelConfig extractModelConfig(Map<String, Object> params) {
        ModelConfig config = new ModelConfig();

        // 从 agent_parameters.model 提取
        Object modelObj = params.get("model");
        if (modelObj instanceof Map) {
            Map<String, Object> modelMap = (Map<String, Object>) modelObj;
            Object valueObj = modelMap.get("value");
            if (valueObj instanceof Map) {
                Map<String, Object> valueMap = (Map<String, Object>) valueObj;
                // 提取 provider
                Object providerObj = valueMap.get("provider");
                if (providerObj instanceof String) {
                    config.provider = normalizeProvider((String) providerObj);
                }
                // 提取 model
                Object modelNameObj = valueMap.get("model");
                if (modelNameObj instanceof String) {
                    config.name = (String) modelNameObj;
                }
            }
        }

        // 如果没有找到，使用默认值
        if (config.provider == null) {
            config.provider = "deepseek";
        }
        if (config.name == null) {
            config.name = "deepseek-chat";
        }

        return config;
    }

    /**
     * 规范化 provider 名称。
     * 例如：langgenius/deepseek/deepseek -> deepseek
     */
    private String normalizeProvider(String provider) {
        if (provider == null) return "deepseek";

        // 提取最后一段
        String[] parts = provider.split("/");
        String lastPart = parts[parts.length - 1].toLowerCase();

        // 映射常见的 provider 别名
        if ("deepseek".equals(lastPart)) return "deepseek";
        if ("openai".equals(lastPart)) return "openai";
        if ("minimax".equals(lastPart)) return "minimax";
        if ("custom".equals(lastPart)) return "custom";
        return lastPart;
    }

    /**
     * 提取用户查询。
     */
    private String extractQuery(NodeExecutionContext context, Map<String, Object> params) {
        // 尝试从 variables 中提取 query
        Object queryVar = findVariableInPool(context, "query");
        if (queryVar != null) {
            return queryVar.toString();
        }

        // 尝试从 sys.query 获取
        Object sysQuery = context.getVariablePool().getSystem("query");
        if (sysQuery != null) {
            return sysQuery.toString();
        }

        return "";
    }

    /**
     * 构建系统提示词。
     */
    @SuppressWarnings("unchecked")
    private String buildSystemPrompt(NodeExecutionContext context, Map<String, Object> params) {
        StringBuilder prompt = new StringBuilder();

        // 从 context 中获取默认提示词
        Object contextObj = params.get("context");
        if (contextObj instanceof Map) {
            Map<String, Object> contextMap = (Map<String, Object>) contextObj;
            Object promptObj = contextMap.get("prompt");
            if (promptObj instanceof String) {
                prompt.append(promptObj);
            }
        }

        // 如果有系统变量，也添加到提示词
        Object sysQuery = context.getVariablePool().getSystem("query");
        if (sysQuery != null && prompt.length() == 0) {
            prompt.append("You are a helpful AI assistant. ");
        }

        return prompt.toString();
    }

    /**
     * 加载工具定义。
     */
    @SuppressWarnings("unchecked")
    private void loadTools(NodeExecutionContext context, Map<String, Object> params, AgentExecutionContext ctx) {
        Object toolsObj = params.get("tools");
        if (toolsObj == null) {
            log.debug("No tools defined in agent_parameters");
            return;
        }

        // tools 可能是一个数组或单个对象
        List<Object> toolsList;
        if (toolsObj instanceof List) {
            toolsList = (List<Object>) toolsObj;
        } else if (toolsObj instanceof Map) {
            toolsList = Java8Compat.listOf(toolsObj);
        } else {
            log.warn("Unexpected tools format: {}", toolsObj.getClass());
            return;
        }

        for (Object toolEntry : toolsList) {
            if (toolEntry instanceof Map) {
                Map<String, Object> toolMap = (Map<String, Object>) toolEntry;
                ToolDefinition tool = buildToolDefinition(context, toolMap);
                if (tool != null) {
                    ctx.addTool(tool);
                    log.info("Loaded tool: {} (type={})", tool.getName(), tool.getType());
                }
            }
        }
    }

    /**
     * 构建工具定义。
     */
    @SuppressWarnings("unchecked")
    private ToolDefinition buildToolDefinition(NodeExecutionContext context, Map<String, Object> toolMap) {
        try {
            // 获取工具名称
            Object toolNameObj = toolMap.get("tool_name");
            Object toolLabelObj = toolMap.get("tool_label");
            String toolName = toolNameObj != null ? toolNameObj.toString() : null;
            if (toolName == null && toolLabelObj != null) {
                toolName = toolLabelObj.toString();
            }
            if (toolName == null) {
                log.warn("Tool name is null, skipping");
                return null;
            }

            // 获取工具描述
            Object descObj = toolMap.get("description");
            String description = descObj != null ? descObj.toString() : "";
            Object toolDescObj = toolMap.get("tool_description");
            if (toolDescObj != null && toolDescObj.toString().isEmpty()) {
                description = toolDescObj.toString();
            }

            // 获取 provider_type
            Object typeObj = toolMap.get("type");
            Object providerTypeObj = toolMap.get("provider_type");
            String providerType = providerTypeObj != null ? providerTypeObj.toString() : null;

            ToolDefinition.ToolType toolType;
            if ("workflow".equalsIgnoreCase(providerType)) {
                toolType = ToolDefinition.ToolType.WORKFLOW;
            } else if ("mcp".equalsIgnoreCase(providerType)) {
                toolType = ToolDefinition.ToolType.MCP;
            } else if ("builtin".equalsIgnoreCase(providerType)) {
                toolType = ToolDefinition.ToolType.BUILTIN;
            } else {
                toolType = ToolDefinition.ToolType.WORKFLOW;  // 默认
            }

            // 获取 provider_name
            Object providerNameObj = toolMap.get("provider_name");
            String providerName = providerNameObj != null ? providerNameObj.toString() : null;

            // 构建参数 schema
            Map<String, Object> parameters = buildParameterSchema(toolMap);

            // 构建执行器
            ToolDefinition.ToolExecutor executor = buildToolExecutor(context, toolType, toolName, toolMap);

            return ToolDefinition.builder()
                    .name(toolName)
                    .description(description)
                    .parameters(parameters)
                    .type(toolType)
                    .providerName(providerName)
                    .executor(executor)
                    .build();

        } catch (Exception e) {
            log.error("Failed to build tool definition: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建参数 schema。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildParameterSchema(Map<String, Object> toolMap) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", new HashMap<String, Object>());

        // 从 parameters 或 paramSchemas 中提取
        Object paramsObj = toolMap.get("parameters");
        if (paramsObj instanceof Map) {
            Map<String, Object> params = (Map<String, Object>) paramsObj;
            Map<String, Object> properties = new HashMap<>();

            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                Map<String, Object> prop = new HashMap<>();
                prop.put("type", "string");  // 默认类型

                if (value instanceof Number) {
                    prop.put("type", "number");
                } else if (value instanceof Boolean) {
                    prop.put("type", "boolean");
                } else if (value != null) {
                    prop.put("description", value.toString());
                }

                properties.put(key, prop);
            }

            schema.put("properties", properties);
        }

        // 从 schemas 中提取（如果有）
        Object schemasObj = toolMap.get("schemas");
        if (schemasObj instanceof List) {
            List<Object> schemas = (List<Object>) schemasObj;
            Map<String, Object> properties = new HashMap<>();

            for (Object sch : schemas) {
                if (sch instanceof Map) {
                    Map<String, Object> schemaMap = (Map<String, Object>) sch;
                    Object nameObj = schemaMap.get("name");
                    if (nameObj != null) {
                        String name = nameObj.toString();
                        Map<String, Object> prop = new HashMap<>();

                        Object typeObj = schemaMap.get("type");
                        String type = typeObj != null ? typeObj.toString() : "string";
                        prop.put("type", type.equals("number") ? "number" : "string");

                        Object descObj = schemaMap.get("description");
                        if (descObj != null) {
                            prop.put("description", descObj.toString());
                        }

                        properties.put(name, prop);
                    }
                }
            }

            schema.put("properties", properties);
        }

        return schema;
    }

    /**
     * 构建工具执行器。
     */
    @SuppressWarnings("unchecked")
    private ToolDefinition.ToolExecutor buildToolExecutor(NodeExecutionContext context,
                                                         ToolDefinition.ToolType toolType,
                                                         String toolName,
                                                         Map<String, Object> toolMap) {
        return (args) -> {
            log.info("Executing tool: {} with args: {}", toolName, args);

            if (toolType == ToolDefinition.ToolType.WORKFLOW) {
                return executeWorkflowTool(context, toolName, toolMap, args);
            } else if (toolType == ToolDefinition.ToolType.BUILTIN) {
                return executeBuiltinTool(context, toolName, args);
            } else if (toolType == ToolDefinition.ToolType.MCP) {
                return executeMcpTool(context, toolName, toolMap, args);
            } else {
                return "Unknown tool type: " + toolType;
            }
        };
    }

    /**
     * 执行工作流工具（子流程）。
     */
    @SuppressWarnings("unchecked")
    private Object executeWorkflowTool(NodeExecutionContext context, String toolName,
                                       Map<String, Object> toolMap, Map<String, Object> args) {
        try {
            // 获取子工作流的参数映射
            Object toolParamsObj = toolMap.get("tool_parameters");
            Map<String, Object> toolParams = new HashMap<>();

            if (toolParamsObj instanceof Map) {
                toolParams = (Map<String, Object>) toolParamsObj;
            }

            // 解析参数值
            Map<String, Object> resolvedArgs = new HashMap<>();
            for (Map.Entry<String, Object> entry : toolParams.entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();

                if (paramValue instanceof Map) {
                    Map<String, Object> paramMap = (Map<String, Object>) paramValue;
                    String type = paramMap.getOrDefault("type", "constant").toString();

                    if ("variable".equals(type)) {
                        // 变量引用
                        Object valueObj = paramMap.get("value");
                        if (valueObj instanceof List) {
                            List<Object> selector = (List<Object>) valueObj;
                            if (selector.size() >= 2) {
                                String nodeId = selector.get(0).toString();
                                String varName = selector.get(1).toString();
                                Object varValue = context.getVariablePool().get(nodeId, varName);
                                resolvedArgs.put(paramName, varValue);
                            }
                        }
                    } else {
                        // 常量值
                        resolvedArgs.put(paramName, paramValue);
                    }
                } else {
                    resolvedArgs.put(paramName, paramValue);
                }
            }

            // 从 args 合并（如果有）
            if (args != null) {
                resolvedArgs.putAll(args);
            }

            // 通过上下文执行具名子工作流（从注册表查找 YAML）
            Object result = context.executeNamedSubWorkflow(toolName, resolvedArgs);
            return result;

        } catch (Exception e) {
            log.error("Failed to execute workflow tool: {}", e.getMessage(), e);
            return "Error executing workflow tool: " + e.getMessage();
        }
    }

    /**
     * 执行内置工具。
     */
    private Object executeBuiltinTool(NodeExecutionContext context, String toolName,
                                       Map<String, Object> args) {
        // TODO: 实现内置工具
        log.info("Executing builtin tool: {}", toolName);
        return "Builtin tool execution not yet implemented";
    }

    /**
     * 执行 MCP 工具调用。
     * 通过 provider_name 查找 MCP Server 配置，连接并调用工具。
     */
    private Object executeMcpTool(NodeExecutionContext context, String toolName,
                                   Map<String, Object> toolMap, Map<String, Object> args) {
        try {
            // 从 toolMap 中获取 provider_name
            String providerName = toolMap.get("provider_name") != null
                    ? toolMap.get("provider_name").toString() : null;
            if (providerName == null || providerName.isEmpty()) {
                return "MCP tool missing provider_name";
            }

            // 查找 MCP Server 配置
            Map<String, McpServerConfig> mcpConfigs = context.getMcpConfigs();
            if (mcpConfigs == null || !mcpConfigs.containsKey(providerName)) {
                return "MCP server config not found for provider: " + providerName;
            }

            McpServerConfig config = mcpConfigs.get(providerName);

            // 缓存 McpClient
            McpClient client = mcpRegistry.getClient(providerName);
            if (client == null) {
                mcpRegistry.registerServer(providerName, config);
                client = mcpRegistry.getClient(providerName);
            }

            if (client == null) {
                return "Failed to create MCP client for provider: " + providerName;
            }

            // 查找工具并调用
            Object mcpTool = mcpRegistry.findTool(toolName);
            if (mcpTool == null) {
                // 工具未在 tools/list 中发现，尝试直接调用
            }

            Object result = client.callTool(toolName, args);
            if (result == null) {
                return "MCP call returned null";
            }
            return result.toString();
        } catch (Exception e) {
            log.error("MCP tool call failed: {} - {}", toolName, e.getMessage(), e);
            return "MCP tool call failed: " + e.getMessage();
        }
    }

    /**
     * 在变量池中查找变量。
     */
    private Object findVariableInPool(NodeExecutionContext context, String varName) {
        com.dify.workflow.model.VariablePool pool = context.getVariablePool();
        if (pool == null) return null;

        for (String nodeId : pool.getNodeIds()) {
            Object val = pool.get(nodeId, varName);
            if (val != null) return val;
        }
        return null;
    }

    /**
     * 模型配置。
     */
    private static class ModelConfig {
        String provider;
        String name;
    }
}
