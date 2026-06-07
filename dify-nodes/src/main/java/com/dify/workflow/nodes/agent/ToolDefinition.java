package com.dify.workflow.nodes.agent;

import java.util.Map;
import java.util.Objects;

/**
 * 工具定义。
 * 描述一个可被 Agent 调用的工具。
 */
public class ToolDefinition {

    private final String name;
    private final String description;
    private final Map<String, Object> parameters;
    private final ToolType type;
    private final String providerName;  // provider_name 用于 MCP 工具查找
    private final ToolExecutor executor;

    public ToolDefinition(String name, String description, Map<String, Object> parameters,
                          ToolType type, String providerName, ToolExecutor executor) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
        this.type = type;
        this.providerName = providerName;
        this.executor = executor;
    }

    /**
     * 工具类型。
     */
    public enum ToolType {
        WORKFLOW,  // 子工作流工具
        BUILTIN,   // 内置工具
        MCP        // MCP 协议工具
    }

    /**
     * 工具执行器接口。
     */
    @FunctionalInterface
    public interface ToolExecutor {
        Object execute(Map<String, Object> args);
    }

    // ========== getters ==========

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public ToolType getType() {
        return type;
    }

    public String getProviderName() {
        return providerName;
    }

    public ToolExecutor getExecutor() {
        return executor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolDefinition that = (ToolDefinition) o;
        return Objects.equals(name, that.name) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    @Override
    public String toString() {
        return "ToolDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", providerName='" + providerName + '\'' +
                '}';
    }

    // ========== Builder ==========

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private Map<String, Object> parameters;
        private ToolType type;
        private String providerName;
        private ToolExecutor executor;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder type(ToolType type) {
            this.type = type;
            return this;
        }

        public Builder providerName(String providerName) {
            this.providerName = providerName;
            return this;
        }

        public Builder executor(ToolExecutor executor) {
            this.executor = executor;
            return this;
        }

        public ToolDefinition build() {
            if (name == null || type == null) {
                throw new IllegalArgumentException("name and type are required");
            }
            return new ToolDefinition(name, description, parameters, type, providerName, executor);
        }
    }
}
