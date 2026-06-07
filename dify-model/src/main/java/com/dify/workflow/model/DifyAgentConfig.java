package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dify agent configuration.
 */
public final class DifyAgentConfig {
    @JSONField(name = "type")
    private final String type;
    @JSONField(name = "strategy")
    private final String strategy;
    @JSONField(name = "model")
    private final DifyModel model;
    @JSONField(name = "prompt_template")
    private final String promptTemplate;
    @JSONField(name = "tools")
    private final List<DifyTool> tools;

    public DifyAgentConfig(String type, String strategy, DifyModel model, String promptTemplate, List<DifyTool> tools) {
        this.type = type;
        this.strategy = strategy;
        this.model = model;
        this.promptTemplate = promptTemplate;
        this.tools = tools;
    }

    public String type() { return type; }
    public String strategy() { return strategy; }
    public DifyModel model() { return model; }
    public String promptTemplate() { return promptTemplate; }
    public List<DifyTool> tools() { return tools; }

    public String getType() { return type; }
    public String getStrategy() { return strategy; }
    public DifyModel getModel() { return model; }
    public String getPromptTemplate() { return promptTemplate; }
    public List<DifyTool> getTools() { return tools; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyAgentConfig)) return false;
        DifyAgentConfig that = (DifyAgentConfig) o;
        return Objects.equals(type, that.type)
                && Objects.equals(strategy, that.strategy)
                && Objects.equals(model, that.model)
                && Objects.equals(promptTemplate, that.promptTemplate)
                && Objects.equals(tools, that.tools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, strategy, model, promptTemplate, tools);
    }

    @Override
    public String toString() {
        return String.format("DifyAgentConfig[type=%s, strategy=%s, model=%s, promptTemplate=%s, tools=%s]",
                type, strategy, model, promptTemplate, tools);
    }

    /**
     * Agent tool configuration.
     */
    public static final class DifyTool {
        @JSONField(name = "provider_id")
        private final DifyProviderId providerId;
        @JSONField(name = "tool_name")
        private final String toolName;
        @JSONField(name = "tool_label")
        private final String toolLabel;
        @JSONField(name = "tool_input_parameters")
        private final Map<String, Object> toolInputParameters;

        public DifyTool(DifyProviderId providerId, String toolName, String toolLabel, Map<String, Object> toolInputParameters) {
            this.providerId = providerId;
            this.toolName = toolName;
            this.toolLabel = toolLabel;
            this.toolInputParameters = toolInputParameters;
        }

        public DifyProviderId providerId() { return providerId; }
        public String toolName() { return toolName; }
        public String toolLabel() { return toolLabel; }
        public Map<String, Object> toolInputParameters() { return toolInputParameters; }

        public DifyProviderId getProviderId() { return providerId; }
        public String getToolName() { return toolName; }
        public String getToolLabel() { return toolLabel; }
        public Map<String, Object> getToolInputParameters() { return toolInputParameters; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DifyTool)) return false;
            DifyTool that = (DifyTool) o;
            return Objects.equals(providerId, that.providerId)
                    && Objects.equals(toolName, that.toolName)
                    && Objects.equals(toolLabel, that.toolLabel)
                    && Objects.equals(toolInputParameters, that.toolInputParameters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(providerId, toolName, toolLabel, toolInputParameters);
        }

        @Override
        public String toString() {
            return String.format("DifyTool[providerId=%s, toolName=%s, toolLabel=%s, toolInputParameters=%s]",
                    providerId, toolName, toolLabel, toolInputParameters);
        }
    }
}
