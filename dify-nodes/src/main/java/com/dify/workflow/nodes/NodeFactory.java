package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;

/**
 * Factory interface for creating node instances.
 * Implementations should be registered with NodeRegistry.
 */
@FunctionalInterface
public interface NodeFactory {
    /**
     * Create a node instance.
     *
     * @param id Node ID
     * @param data Node data
     * @return Node instance
     */
    DifyNode create(String id, DifyNodeData data);
}