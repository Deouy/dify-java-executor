package com.dify.workflow.nodes.agent.mcp;

import java.util.List;

/**
 * MCP 工具调用结果。
 */
public class McpCallResult {
    private List<Object> content;
    private boolean isError;

    public List<Object> getContent() { return content; }
    public void setContent(List<Object> content) { this.content = content; }

    public boolean isError() { return isError; }
    public void setIsError(boolean isError) { this.isError = isError; }

    @Override
    public String toString() {
        if (content == null || content.isEmpty()) return "";
        if (content.size() == 1) return String.valueOf(content.get(0));
        return content.toString();
    }
}
