package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.DifyVariable;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Variable Aggregator node - 变量聚合器。
 * 从多个分支收集变量，取第一个非 null 值作为输出。
 * 参考 Dify 的 VariableAggregator (graphon.nodes.variable_assigner.v1)。
 *
 * 支持两种模式：
 * 1. 非分组模式: variables 列表 → 输出为 "output"
 * 2. 分组模式: advanced_settings.groups → 每组一个输出 "GroupName.output"
 */
public class VariableAggregatorNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(VariableAggregatorNode.class);

    public VariableAggregatorNode(String id, DifyNodeData data) {
        super(id, NodeType.VARIABLE_AGGREGATOR, data);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing variable aggregator node: {}", id);

        Map<String, Object> advancedSettings = data.advancedSettings();

        // 分组模式
        if (advancedSettings != null && Boolean.TRUE.equals(advancedSettings.get("group_enabled"))) {
            List<Map<String, Object>> groups = (List<Map<String, Object>>) advancedSettings.get("groups");
            if (groups != null) {
                for (Map<String, Object> group : groups) {
                    String groupName = (String) group.get("group_name");
                    List<List<String>> groupVars = (List<List<String>>) group.get("variables");
                    Object value = resolveFirstValid(groupVars, context);
                    if (value != null) {
                        context.setVariable(id, groupName, value);
                        log.debug("Aggregator group '{}': {}", groupName, value);
                    }
                }
            }
        } else {
            // 非分组模式：从 variables 列表取第一个非 null 值
            List<DifyVariable> variables = data.variables();
            Object value = resolveFirstFromVariables(variables, context);
            context.setVariable(id, "output", value);
            log.debug("Aggregator output: {}", value);
        }
    }

    /**
     * 从分组变量列表中取第一个非 null 值。
     */
    @SuppressWarnings("unchecked")
    private Object resolveFirstValid(List<List<String>> groupVars, NodeExecutionContext context) {
        if (groupVars == null) return null;
        for (List<String> selector : groupVars) {
            if (selector.size() >= 2) {
                Object value = context.getVariable(selector.get(0), selector.get(1));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * 从 DifyVariable 列表取第一个非 null 值。
     */
    private Object resolveFirstFromVariables(List<DifyVariable> variables, NodeExecutionContext context) {
        if (variables == null) return null;
        for (DifyVariable var : variables) {
            List<String> selector = var.valueSelector();
            if (selector != null && selector.size() >= 2) {
                Object value = context.getVariable(selector.get(0), selector.get(1));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }
}
