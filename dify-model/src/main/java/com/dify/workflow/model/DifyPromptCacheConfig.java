package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Objects;

/**
 * Dify prompt cache configuration for LLM node.
 */
public final class DifyPromptCacheConfig {
    @JSONField(name = "enabled")
    private final Boolean enabled;

    public DifyPromptCacheConfig(Boolean enabled) {
        this.enabled = enabled != null ? enabled : false;
    }

    public Boolean enabled() { return enabled; }
    public Boolean getEnabled() { return enabled; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyPromptCacheConfig)) return false;
        DifyPromptCacheConfig that = (DifyPromptCacheConfig) o;
        return Objects.equals(enabled, that.enabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled);
    }

    @Override
    public String toString() {
        return String.format("DifyPromptCacheConfig[enabled=%s]", enabled);
    }
}
