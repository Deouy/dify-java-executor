package com.dify.workflow.nodes.agent.mcp;

import com.dify.workflow.model.McpServerConfig;
import com.dify.workflow.model.PinyinUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具注册表。
 * 管理 MCP 客户端和工具缓存，支持按 provider_name 查找和拼音匹配。
 */
public class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    /** provider_name → McpClient */
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();

    /** provider_name → 工具列表 */
    private final Map<String, List<McpTool>> toolsCache = new ConcurrentHashMap<>();

    /** 所有工具名 → (providerName, McpTool) 映射，用于快速查找（含拼音匹配） */
    private final Map<String, McpToolEntry> toolIndex = new ConcurrentHashMap<>();

    /**
     * 注册 MCP Server 配置，创建客户端并发现工具。
     */
    public void registerServer(String providerName, McpServerConfig config) throws IOException {
        if (clients.containsKey(providerName)) {
            log.debug("MCP server already registered: {}", providerName);
            return;
        }

        McpClient client = new McpClient(config);
        client.initialize();

        List<McpTool> tools = client.listTools();
        clients.put(providerName, client);
        toolsCache.put(providerName, tools);

        // 建立工具名索引
        for (McpTool tool : tools) {
            toolIndex.put(tool.getName().toLowerCase(), new McpToolEntry(providerName, tool));
        }

        log.info("MCP server '{}' registered with {} tools", providerName, tools.size());
    }

    /**
     * 查找 MCP 工具（支持拼音匹配）。
     */
    public McpTool findTool(String toolName) {
        if (toolName == null || toolName.isEmpty()) {
            return null;
        }

        // 精确匹配
        String key = toolName.toLowerCase();
        McpToolEntry entry = toolIndex.get(key);
        if (entry != null) {
            return entry.tool;
        }

        // 拼音匹配: 将已有工具名和搜索名都转拼音后比较
        for (Map.Entry<String, McpToolEntry> indexEntry : toolIndex.entrySet()) {
            if (PinyinUtil.matches(toolName, indexEntry.getKey())) {
                return indexEntry.getValue().tool;
            }
        }

        return null;
    }

    /**
     * 根据 provider_name 获取 MCP 客户端。
     */
    public McpClient getClient(String providerName) {
        return clients.get(providerName);
    }

    /**
     * 获取指定 provider 的工具列表。
     */
    public List<McpTool> getTools(String providerName) {
        return toolsCache.getOrDefault(providerName, Collections.emptyList());
    }

    /**
     * 获取所有已注册的 provider。
     */
    public Set<String> getProviders() {
        return new HashSet<>(clients.keySet());
    }

    /**
     * 关闭所有连接。
     */
    public void shutdown() {
        clients.values().forEach(McpClient::close);
        clients.clear();
        toolsCache.clear();
        toolIndex.clear();
    }

    /** 工具索引条目 */
    private static final class McpToolEntry {
        private final String providerName;
        private final McpTool tool;

        McpToolEntry(String providerName, McpTool tool) {
            this.providerName = providerName;
            this.tool = tool;
        }

        String providerName() { return providerName; }
        McpTool tool() { return tool; }
    }
}
