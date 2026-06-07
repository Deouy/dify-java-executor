package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Map;
import java.util.Set;

/**
 * Interface for variable pool.
 * Used to store node variables during workflow execution.
 */
public interface VariablePool {

    /**
     * Get a variable value.
     */
    Object get(String nodeId, String variableName);

    /**
     * Set a variable value.
     */
    void set(String nodeId, String variableName, Object value);

    /**
     * Get all variables for a node.
     */
    Map<String, Object> getNodeVariables(String nodeId);

    /**
     * Get all node outputs.
     */
    Map<String, Object> getNodeOutputs(String nodeId);

    /**
     * Get a system variable.
     */
    Object getSystem(String field);

    /**
     * Set a system variable.
     */
    void setSystem(String field, Object value);

    /**
     * Get an environment variable.
     */
    Object getEnvironment(String field);

    /**
     * Set an environment variable.
     * Mirrors setSystem/setConversation symmetry. Used by DifyWorkflowExecutor
     * when initializing bootstrap env vars from workflow YAML.
     */
    void setEnvironment(String field, Object value);

    /**
     * Get a conversation variable.
     */
    Object getConversation(String field);

    /**
     * Set a conversation variable.
     * Used by VariableAssignerNode when target selector is "conversation.X".
     */
    void setConversation(String field, Object value);

    /**
     * Get all node IDs that have outputs.
     */
    Set<String> getNodeIds();
}
