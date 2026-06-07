package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Iteration node - 迭代节点。
 * 参考 Dify 的 IterationNode（graphon.nodes.iteration.IterationNode）。
 *
 * 工作流程：
 * 1. 从 iterator_selector 读取数组
 * 2. 对每个元素：注入 item/index 到变量池 → 执行子图 → 收集输出
 * 3. 将结果存入 output_selector 指定的位置
 */
public class IterationNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(IterationNode.class);

    public IterationNode(String id, DifyNodeData data) {
        super(id, NodeType.ITERATION, data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing iteration node: {}", id);

        // 读取配置
        List<String> iteratorSelector = data.iteratorSelector();
        List<String> outputSelector = data.outputSelector();
        String startNodeId = data.startNodeId();

        System.out.println("[DEBUG-iter] node=" + id + " iterator=" + iteratorSelector
            + " startNodeId=" + startNodeId + " output=" + outputSelector);

        if (iteratorSelector == null || iteratorSelector.size() < 2) {
            log.warn("Iteration node {} has no valid iterator_selector, skipping", id);
            context.setVariable(id, "_iteration_completed", true);
            return;
        }

        // 解析 iterator_selector: [sourceNodeId, varName]
        String sourceNodeId = iteratorSelector.get(0);
        String sourceVarName = iteratorSelector.get(1);

        // 从变量池获取要迭代的数组
        Object arrayObj = context.getVariable(sourceNodeId, sourceVarName);
        System.out.println("[DEBUG-iter] node=" + id + " arrayObj=" + (arrayObj == null ? "null" : arrayObj.getClass().getSimpleName() + " size=" + (arrayObj instanceof java.util.Collection ? ((java.util.Collection)arrayObj).size() : "?")));
        if (arrayObj == null) {
            log.warn("Iteration node {} - array is null: {}.{}", id, sourceNodeId, sourceVarName);
            context.setVariable(id, "_iteration_completed", true);
            return;
        }

        // 数据源已通过 CodeNode 的 unwrapNashorn 转换为纯 Java 类型，无需额外处理 Nashorn 对象
        if (!(arrayObj instanceof List)) {
            log.warn("Iteration node {} - variable is not a list: {} (type: {})",
                    id, sourceVarName, arrayObj.getClass().getSimpleName());
            context.setVariable(id, "_iteration_completed", true);
            return;
        }

        List<?> items = (List<?>) arrayObj;
        int totalSteps = items.size();
        List<Map<String, Object>> iterationOutputs = new ArrayList<>();

        log.info("Iteration node {} starting: {} items", id, totalSteps);

        long iterStartTime = System.currentTimeMillis();

        for (int i = 0; i < totalSteps; i++) {
            Object item = items.get(i);

            // 注入 item 和 index 到迭代节点的变量池
            context.setVariable(id, "item", item);
            context.setVariable(id, "index", i);

            log.debug("Iteration node {} step {}/{}: item = {}", id, i + 1, totalSteps, item);

            // 执行子图（迭代体）
            if (startNodeId != null) {
                try {
                    context.executeSubGraph(startNodeId, id);
                } catch (Exception e) {
                    log.error("Iteration node {} step {} failed: {}", id, i, e.getMessage());
                    throw e;
                }
            }

            // 收集本次迭代的输出
            Map<String, Object> stepOutput = new HashMap<>();
            if (outputSelector != null && outputSelector.size() >= 2) {
                String outNodeId = outputSelector.get(0);
                String outVarName = outputSelector.get(1);
                Object output = context.getVariable(outNodeId, outVarName);
                if (output != null) {
                    stepOutput.put(outVarName, output);
                }
            }
            // 同时从迭代节点收集所有变量
            iterationOutputs.add(stepOutput);
        }

        // 展平输出: 如果 output_type 是 array[string]，取出每项的 output 字段组合成数组
        List<Object> flattenedOutputs = new ArrayList<>();
        for (Map<String, Object> stepOutput : iterationOutputs) {
            if (stepOutput.size() == 1) {
                flattenedOutputs.add(stepOutput.values().iterator().next());
            } else if (!stepOutput.isEmpty()) {
                flattenedOutputs.add(stepOutput);
            }
        }
        if (flattenedOutputs.isEmpty() && !iterationOutputs.isEmpty()) {
            flattenedOutputs.addAll(iterationOutputs);
        }

        // 将迭代结果存入 output_selector 指定的位置
        if (outputSelector != null && outputSelector.size() >= 2) {
            String outNodeId = outputSelector.get(0);
            String outVarName = outputSelector.get(1);
            context.setVariable(outNodeId, outVarName, flattenedOutputs);
        }

        context.setVariable(id, "_iteration_completed", true);
        context.setVariable(id, "output", flattenedOutputs);

        log.info("Iteration node {} completed: {} steps, {} outputs", id, totalSteps, flattenedOutputs.size());
    }
}
