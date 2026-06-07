package com.dify.workflow.model;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP Server 通用配置（暂仅支持 SSE 传输）。
 * 后续可实现 StreamableHTTP 传输和 OAuth 认证。
 */
public class McpServerConfig {

    private final String url;
    private final String apiKey;
    private final int timeout;
    private final int sseReadTimeout;
    private final Map<String, String> headers;

    private McpServerConfig(Builder builder) {
        this.url = builder.url;
        this.apiKey = builder.apiKey;
        this.timeout = builder.timeout;
        this.sseReadTimeout = builder.sseReadTimeout;
        this.headers = new HashMap<>(builder.headers);
    }

    public static Builder builder() {
        return new Builder();
    }

    // -- getters --

    public String url() { return url; }
    public String apiKey() { return apiKey; }
    public int timeout() { return timeout; }
    public int sseReadTimeout() { return sseReadTimeout; }
    public Map<String, String> headers() { return new HashMap<>(headers); }

    public static class Builder {
        private String url;
        private String apiKey;
        private int timeout = 30;
        private int sseReadTimeout = 300;
        private final Map<String, String> headers = new HashMap<>();

        /** MCP Server SSE URL (e.g., http://localhost:8080/sse) */
        public Builder url(String url) { this.url = url; return this; }

        /** 可选的 API Key 认证 */
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }

        /** HTTP 超时（秒），默认 30 */
        public Builder timeout(int timeout) { this.timeout = timeout; return this; }

        /** SSE 读取超时（秒），默认 300 */
        public Builder sseReadTimeout(int sseReadTimeout) { this.sseReadTimeout = sseReadTimeout; return this; }

        /** 自定义 HTTP Header */
        public Builder header(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public McpServerConfig build() {
            if (url == null || url.trim().isEmpty()) {
                throw new IllegalArgumentException("MCP Server URL is required");
            }
            return new McpServerConfig(this);
        }
    }
}
