package com.dify.workflow.nodes.mcp;

import com.alibaba.fastjson2.JSONObject;
import com.dify.workflow.model.McpServerConfig;
import com.dify.workflow.nodes.agent.mcp.McpCallResult;
import com.dify.workflow.nodes.agent.mcp.McpClient;
import com.dify.workflow.nodes.agent.mcp.McpTool;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 百度地图连通性测试。
 * 测试 McpClient 能否正常连接百度地图 MCP Server 并进行工具发现和调用。
 */
class McpBaidumapTest {

    private static final String BAIDU_MCP_URL = "https://mcp.map.baidu.com/mcp?ak=iCPbCAbDkExlwmt9e1j98LpbQdDPXxiz";

    @Test
    void testBaidumapConnection() throws Exception {
        McpServerConfig config = McpServerConfig.builder()
                .url(BAIDU_MCP_URL)
                .timeout(60)
                .sseReadTimeout(120)
                .build();

        System.out.println("=== MCP百度地图连通性测试 ===");
        McpClient client = new McpClient(config);

        // 1. 初始化握手
        JSONObject initResult = client.initialize();
        assertNotNull(initResult, "Initialize should return result");
        String serverName = initResult.getJSONObject("serverInfo").getString("name");
        System.out.println("1. Initialize: OK - " + serverName);
        assertNotNull(serverName, "Server name should not be null");

        // 2. 工具发现
        List<McpTool> tools = client.listTools();
        assertNotNull(tools, "Tools list should not be null");
        assertFalse(tools.isEmpty(), "Should discover at least one tool");
        System.out.println("2. Tools discovered: " + tools.size());
        for (McpTool tool : tools) {
            System.out.println("   - " + tool.getName() + ": " + tool.getDescription());
        }

        // 3. 测试工具调用
        System.out.println("3. Testing tool call: " + tools.get(0).getName());
        McpCallResult callResult = client.callTool(tools.get(0).getName(), new HashMap<String, Object>());
        assertNotNull(callResult, "Tool call result should not be null");
        System.out.println("   Result: " + (callResult.toString().length() > 200
                ? callResult.toString().substring(0, 200) + "..."
                : callResult.toString()));

        client.close();
        System.out.println("=== MCP连通性测试完成 ===");
    }
}
