package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loop node - 循环节点。
 * 参考 Dify 的 LoopNode（graphon.nodes.loop.LoopNode）。
 *
 * 工作流程：
 * 1. 初始化循环变量（如 index=0）
 * 2. 每次迭代执行子图 → 更新循环变量 → 检查 break 条件
 * 3. 循环结束时输出最终结果
 *
 * 与 Iteration 的区别：Loop 复用同一个变量池（累积状态），
 * Iteration 每次深拷贝（隔离状态）。
 */
public class LoopNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(LoopNode.class);

    public LoopNode(String id, DifyNodeData data) {
        super(id, NodeType.LOOP, data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing loop node: {}", id);

        String startNodeId = data.startNodeId();
        Integer loopCount = data.loopCount();

        if (loopCount == null) {
            // 从 additionalProperties 读取
            if (data.additionalProperties() != null) {
                Object countObj = data.additionalProperties().get("loop_count");
                if (countObj instanceof Number) {
                    loopCount = ((Number) countObj).intValue();
                }
            }
        }
        if (loopCount == null) {
            loopCount = 1;
        }

        // 初始化循环变量（同时设置 _index 和 index 以兼容代码节点引用）
        int index = 0;
        context.setVariable(id, "_loop_count", loopCount);
        context.setVariable(id, "_index", index);
        context.setVariable(id, "index", index);
        context.setVariable(id, "_loop_completed", false);

        log.info("Loop node {} starting: loopCount={}, startNodeId={}", id, loopCount, startNodeId);

        long loopStartTime = System.currentTimeMillis();

        for (int i = 0; i < loopCount; i++) {
            context.setVariable(id, "_index", index);
            context.setVariable(id, "index", index);
            log.debug("Loop node {} iteration {}/{}: index={}", id, i + 1, loopCount, index);

            // 先检查 break_conditions，避免越界访问
            if (shouldBreak(context)) {
                log.info("Loop node {} breaking at iteration {} (break condition met)", id, i + 1);
                break;
            }

            // 执行子图（循环体）
            if (startNodeId != null) {
                try {
                    context.executeSubGraph(startNodeId, id);
                } catch (Exception e) {
                    log.error("Loop node {} iteration {} failed: {}", id, i, e.getMessage());
                    throw e;
                }
            }

            index++;
            context.setVariable(id, "_index", index);
            context.setVariable(id, "index", index);
        }

        context.setVariable(id, "_loop_completed", true);

        long loopEndTime = System.currentTimeMillis();
        log.info("Loop node {} completed: {} iterations, {}ms",
                id, index, loopEndTime - loopStartTime);
    }

    /**
     * 检查 break_conditions 是否满足。
     * 当前支持简单的 index >= value 比较。
     */
    private boolean shouldBreak(NodeExecutionContext context) {
        List<Map<String, Object>> conditions = data.breakConditions();
        if (conditions == null || conditions.isEmpty()) {
            return false;
        }
        for (Object condition : conditions) {
            String operator = "";
            Object varSelector = null;
            Object rawValue = null;
            if (condition instanceof Map) {
                Map<?, ?> condMap = (Map<?, ?>) condition;
                Object opObj = condMap.get("comparison_operator");
                operator = opObj != null ? opObj.toString() : "";
                varSelector = condMap.get("variable_selector");
                rawValue = condMap.get("value");
            }

            if (varSelector instanceof List && ((List<?>) varSelector).size() >= 2) {
                List<?> sel = (List<?>) varSelector;
                String nodeId = String.valueOf(sel.get(0));
                String varName = String.valueOf(sel.get(1));
                Object varValue = context.getVariable(nodeId, varName);

                // 解析 value：可能是常量数字或 {{#node.var#}} 引用
                String valueStr = String.valueOf(rawValue);
                String resolvedValue = context.resolveVariables(valueStr);
                if (resolvedValue != null && !resolvedValue.equals(valueStr)) {
                    valueStr = resolvedValue;
                }

                try {
                    double varNum = varValue instanceof Number
                            ? ((Number) varValue).doubleValue()
                            : Double.parseDouble(String.valueOf(varValue));
                    double valueNum = Double.parseDouble(valueStr.replaceAll("[^0-9.]", ""));

                    if (">".equals(operator) && varNum > valueNum) {
                        return true;
                    }
                    if (">=".equals(operator) && varNum >= valueNum) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    log.debug("Break condition parse error: {}", e.getMessage());
                }
            }
        }
        return false;
    }
}
