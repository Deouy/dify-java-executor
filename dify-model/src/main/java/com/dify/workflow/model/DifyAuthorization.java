package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Objects;

/**
 * Dify HTTP authorization configuration.
 */
public final class DifyAuthorization {
    @JSONField(name = "type")
    private final String type;
    @JSONField(name = "config")
    private final DifyAuthorizationConfig config;

    public DifyAuthorization(String type, DifyAuthorizationConfig config) {
        this.type = type;
        this.config = config;
    }

    public String type() { return type; }
    public DifyAuthorizationConfig config() { return config; }

    public String getType() { return type; }
    public DifyAuthorizationConfig getConfig() { return config; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyAuthorization)) return false;
        DifyAuthorization that = (DifyAuthorization) o;
        return Objects.equals(type, that.type) && Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, config);
    }

    @Override
    public String toString() {
        return String.format("DifyAuthorization[type=%s, config=%s]", type, config);
    }

    /**
     * Authorization config (api_key).
     */
    public static final class DifyAuthorizationConfig {
        @JSONField(name = "api_key")
        private final String apiKey;

        public DifyAuthorizationConfig(String apiKey) {
            this.apiKey = apiKey;
        }

        public String apiKey() { return apiKey; }
        public String getApiKey() { return apiKey; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DifyAuthorizationConfig)) return false;
            DifyAuthorizationConfig that = (DifyAuthorizationConfig) o;
            return Objects.equals(apiKey, that.apiKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(apiKey);
        }

        @Override
        public String toString() {
            return String.format("DifyAuthorizationConfig[apiKey=%s]", apiKey);
        }
    }
}
