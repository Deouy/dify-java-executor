package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;

/**
 * Dify node interface for all node implementations.
 */
public interface DifyNode {

    /**
     * Get the node ID.
     */
    String getId();

    /**
     * Get the node type.
     */
    String getType();

    /**
     * Get the node data.
     */
    DifyNodeData getData();

    /**
     * Execute the node.
     *
     * @param context Workflow execution context
     * @throws Exception if execution fails
     */
    void execute(NodeExecutionContext context) throws Exception;

    /**
     * Check if the node should be skipped.
     */
    default boolean shouldSkip() {
        return false;
    }
}
