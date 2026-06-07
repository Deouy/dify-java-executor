package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dify workflow definition.
 */
public final class DifyWorkflow {
    @JSONField(name = "graph")
    private final DifyGraph graph;
    @JSONField(name = "features")
    private final Map<String, Object> features;
    @JSONField(name = "environment_variables")
    private final List<DifyEnvironmentVariable> environmentVariables;
    @JSONField(name = "conversation_variables")
    private final List<DifyConversationVariable> conversationVariables;
    @JSONField(name = "rag_pipeline_variables")
    private final List<Object> ragPipelineVariables;

    public DifyWorkflow(DifyGraph graph, Map<String, Object> features,
                        List<DifyEnvironmentVariable> environmentVariables,
                        List<DifyConversationVariable> conversationVariables,
                        List<Object> ragPipelineVariables) {
        this.graph = graph;
        this.features = features;
        this.environmentVariables = environmentVariables;
        this.conversationVariables = conversationVariables;
        this.ragPipelineVariables = ragPipelineVariables;
    }

    public DifyGraph graph() { return graph; }
    public Map<String, Object> features() { return features; }
    public List<DifyEnvironmentVariable> environmentVariables() { return environmentVariables; }
    public List<DifyConversationVariable> conversationVariables() { return conversationVariables; }
    public List<Object> ragPipelineVariables() { return ragPipelineVariables; }

    public DifyGraph getGraph() { return graph; }
    public Map<String, Object> getFeatures() { return features; }
    public List<DifyEnvironmentVariable> getEnvironmentVariables() { return environmentVariables; }
    public List<DifyConversationVariable> getConversationVariables() { return conversationVariables; }
    public List<Object> getRagPipelineVariables() { return ragPipelineVariables; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyWorkflow)) return false;
        DifyWorkflow that = (DifyWorkflow) o;
        return Objects.equals(graph, that.graph)
                && Objects.equals(features, that.features)
                && Objects.equals(environmentVariables, that.environmentVariables)
                && Objects.equals(conversationVariables, that.conversationVariables)
                && Objects.equals(ragPipelineVariables, that.ragPipelineVariables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(graph, features, environmentVariables, conversationVariables, ragPipelineVariables);
    }

    @Override
    public String toString() {
        return String.format("DifyWorkflow[graph=%s, features=%s, environmentVariables=%s, conversationVariables=%s, ragPipelineVariables=%s]",
                graph, features, environmentVariables, conversationVariables, ragPipelineVariables);
    }
}
