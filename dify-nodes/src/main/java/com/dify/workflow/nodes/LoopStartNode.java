package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loop start node - 标记循环体的开始。
 * 基础实现：仅标记循环开始状态。
 */
public class LoopStartNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(LoopStartNode.class);

    public LoopStartNode(String id, DifyNodeData data) {
        super(id, NodeType.LOOP_START, data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing loop start node: {}", id);
        context.setVariable(id, "_loop_started", true);
    }
}
