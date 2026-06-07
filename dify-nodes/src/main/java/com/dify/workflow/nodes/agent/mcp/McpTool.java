package com.dify.workflow.nodes.agent.mcp;

import com.alibaba.fastjson2.JSONObject;

/**
 * MCP 工具定义，从 MCP Server 的 tools/list 返回。
 */
public class McpTool {
    private String name;
    private String description;
    private JSONObject inputSchema;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public JSONObject getInputSchema() { return inputSchema; }
    public void setInputSchema(JSONObject inputSchema) { this.inputSchema = inputSchema; }
}
