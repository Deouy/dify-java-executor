package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Objects;

/**
 * Dify context configuration for LLM node.
 */
public final class DifyContextConfig {
    @JSONField(name = "enabled")
    private final Boolean enabled;
    @JSONField(name = "variable_selector")
    private final List<String> variableSelector;

    public DifyContextConfig(Boolean enabled, List<String> variableSelector) {
        this.enabled = enabled != null ? enabled : false;
        this.variableSelector = variableSelector;
    }

    public Boolean enabled() { return enabled; }
    public List<String> variableSelector() { return variableSelector; }

    public Boolean getEnabled() { return enabled; }
    public List<String> getVariableSelector() { return variableSelector; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyContextConfig)) return false;
        DifyContextConfig that = (DifyContextConfig) o;
        return Objects.equals(enabled, that.enabled)
                && Objects.equals(variableSelector, that.variableSelector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, variableSelector);
    }

    @Override
    public String toString() {
        return String.format("DifyContextConfig[enabled=%s, variableSelector=%s]", enabled, variableSelector);
    }
}
