package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Objects;

/**
 * Dify vision configuration for LLM node.
 */
public final class DifyVisionConfig {
    @JSONField(name = "enabled")
    private final Boolean enabled;
    @JSONField(name = "configs")
    private final DifyVisionConfigs configs;

    public DifyVisionConfig(Boolean enabled, DifyVisionConfigs configs) {
        this.enabled = enabled != null ? enabled : false;
        this.configs = configs;
    }

    public Boolean enabled() { return enabled; }
    public DifyVisionConfigs configs() { return configs; }

    public Boolean getEnabled() { return enabled; }
    public DifyVisionConfigs getConfigs() { return configs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyVisionConfig)) return false;
        DifyVisionConfig that = (DifyVisionConfig) o;
        return Objects.equals(enabled, that.enabled) && Objects.equals(configs, that.configs);
    }

    @Override
    public int hashCode() { return Objects.hash(enabled, configs); }

    @Override
    public String toString() {
        return String.format("DifyVisionConfig[enabled=%s, configs=%s]", enabled, configs);
    }

    public static final class DifyVisionConfigs {
        @JSONField(name = "variable_selector")
        private final List<String> variableSelector;

        public DifyVisionConfigs(List<String> variableSelector) {
            this.variableSelector = variableSelector;
        }

        public List<String> variableSelector() { return variableSelector; }
        public List<String> getVariableSelector() { return variableSelector; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DifyVisionConfigs)) return false;
            DifyVisionConfigs that = (DifyVisionConfigs) o;
            return Objects.equals(variableSelector, that.variableSelector);
        }

        @Override
        public int hashCode() { return Objects.hash(variableSelector); }

        @Override
        public String toString() {
            return String.format("DifyVisionConfigs[variableSelector=%s]", variableSelector);
        }
    }
}
