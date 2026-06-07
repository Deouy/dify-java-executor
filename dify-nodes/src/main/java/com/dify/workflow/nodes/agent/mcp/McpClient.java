package com.dify.workflow.nodes.agent.mcp;

import com.alibaba.fastjson2.JSON;
import com.dify.workflow.model.Java8Compat;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.dify.workflow.model.McpServerConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP (Model Context Protocol) 客户端。
 * 实现 JSON-RPC 2.0 over StreamableHTTP 协议，连接 MCP Server 进行工具发现和调用。
 */
public class McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);
    private static final String JSONRPC_VERSION = "2.0";

    private final McpServerConfig config;
    private final OkHttpClient httpClient;
    private final AtomicLong requestId = new AtomicLong(1);
    private String sessionId;

    public McpClient(McpServerConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.timeout(), TimeUnit.SECONDS)
                .readTimeout(config.sseReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(config.timeout(), TimeUnit.SECONDS)
                .build();
    }

    /**
     * 初始化握手，返回服务器能力信息。
     */
    public JSONObject initialize() throws IOException {
        JSONObject params = new JSONObject();
        params.put("protocolVersion", "2024-11-05");
        params.put("capabilities", new JSONObject());
        JSONObject clientInfo = new JSONObject();
        clientInfo.put("name", "dify-java-executor");
        clientInfo.put("version", "1.0.0");
        params.put("clientInfo", clientInfo);

        JSONObject result = sendRequest("initialize", params);
        if (result != null) {
            log.info("MCP initialize successful: {}", result.toJSONString());
        }
        return result;
    }

    /**
     * 获取 MCP Server 提供的工具列表。
     */
    public List<McpTool> listTools() throws IOException {
        JSONObject result = sendRequest("tools/list", new JSONObject());
        List<McpTool> tools = new ArrayList<>();

        if (result != null && result.containsKey("tools")) {
            JSONArray toolsArray = result.getJSONArray("tools");
            for (int i = 0; i < toolsArray.size(); i++) {
                JSONObject toolJson = toolsArray.getJSONObject(i);
                McpTool tool = new McpTool();
                tool.setName(toolJson.getString("name"));
                tool.setDescription(toolJson.getString("description"));
                if (toolJson.containsKey("inputSchema")) {
                    tool.setInputSchema(toolJson.getJSONObject("inputSchema"));
                }
                tools.add(tool);
                log.debug("Discovered MCP tool: {}", tool.getName());
            }
        }

        log.info("MCP tools/list returned {} tools", tools.size());
        return tools;
    }

    /**
     * 调用 MCP 工具。
     */
    public McpCallResult callTool(String toolName, Map<String, Object> args) throws IOException {
        JSONObject params = new JSONObject();
        params.put("name", toolName);
        if (args != null) {
            params.put("arguments", new JSONObject(args));
        }

        JSONObject result = sendRequest("tools/call", params);
        if (result != null) {
            McpCallResult callResult = new McpCallResult();
            if (result.containsKey("content")) {
                Object content = result.get("content");
                if (content instanceof JSONArray) {
                    JSONArray contentArray = (JSONArray) content;
                    List<Object> contentList = new ArrayList<>();
                    for (int i = 0; i < contentArray.size(); i++) {
                        JSONObject contentItem = contentArray.getJSONObject(i);
                        if ("text".equals(contentItem.getString("type"))) {
                            contentList.add(contentItem.getString("text"));
                        } else {
                            contentList.add(contentItem);
                        }
                    }
                    callResult.setContent(contentList);
                } else {
                    callResult.setContent(Java8Compat.listOf(content));
                }
            }
            callResult.setIsError(result.getBooleanValue("isError", false));
            return callResult;
        }
        return null;
    }

    /**
     * 发送 JSON-RPC 请求。
     */
    private JSONObject sendRequest(String method, JSONObject params) throws IOException {
        long id = requestId.getAndIncrement();

        JSONObject request = new JSONObject();
        request.put("jsonrpc", JSONRPC_VERSION);
        request.put("method", method);
        request.put("params", params);
        request.put("id", id);

        String jsonBody = request.toJSONString();
        log.debug("MCP request [{}] {}: {}", id, method, jsonBody);

        Request.Builder requestBuilder = new Request.Builder()
                .url(config.url())
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")));

        // 添加自定义 headers
        for (Map.Entry<String, String> entry : config.headers().entrySet()) {
            requestBuilder.addHeader(entry.getKey(), entry.getValue());
        }

        // Session ID
        if (sessionId != null) {
            requestBuilder.addHeader("Mcp-Session-Id", sessionId);
        }

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            // 保存 session ID
            String respSessionId = response.header("Mcp-Session-Id");
            if (respSessionId != null) {
                this.sessionId = respSessionId;
            }

            if (!response.isSuccessful()) {
                log.error("MCP request failed: HTTP {}", response.code());
                throw new IOException("MCP request failed: HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }

            String responseBody = body.string();
            JSONObject jsonResponse = JSON.parseObject(responseBody);

            if (jsonResponse.containsKey("error")) {
                JSONObject error = jsonResponse.getJSONObject("error");
                log.error("MCP JSON-RPC error: {}", error.toJSONString());
                throw new IOException("MCP error: " + error.getString("message"));
            }

            return jsonResponse.getJSONObject("result");
        }
    }

    /**
     * 关闭客户端。
     */
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
