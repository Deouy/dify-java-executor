package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.DifyVariable;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Iteration start node - marks the beginning of an iteration body.
 *
 * 迭代开始节点工作流程：
 * 1. 从上下文获取迭代变量（通常是数组）
 * 2. 准备迭代上下文，为每次迭代设置当前元素
 * 3. 标记迭代状态
 *
 * 注意：实际的迭代执行由 DifyWorkflowExecutor 在遇到 iteration_start 时触发
 */
public class IterationStartNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(IterationStartNode.class);

    public IterationStartNode(String id, DifyNodeData data) {
        super(id, NodeType.ITERATION_START, data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing iteration start node: {}", id);

        // 获取迭代变量配置
        // 注意：在 Dify 中，迭代变量通常通过 input_variables 指定
        List<DifyVariable> inputVars = data.inputVariables();
        if (inputVars != null && !inputVars.isEmpty()) {
            for (DifyVariable var : inputVars) {
                Object value = context.getVariable(id, var.variable());
                context.setVariable(id, "_iteration_variable", value);
                context.setVariable(id, "_iteration_variable_name", var.variable());
                log.debug("Iteration variable {} = {}", var.variable(), value);
            }
        }

        // 标记迭代开始
        context.setVariable(id, "_iteration_started", true);
    }
}
