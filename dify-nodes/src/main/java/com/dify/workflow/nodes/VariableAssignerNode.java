package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import com.dify.workflow.model.node.SystemVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Variable Assigner node - 变量赋值节点。
 *
 * 支持将值写入指定的 variable_selector 目标，如：
 * - [conversation, varName] → 会话变量
 * - [nodeId, varName] → 其他节点的变量
 * - 同节点自身变量（无 variable_selector 时）
 */
public class VariableAssignerNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(VariableAssignerNode.class);

    public VariableAssignerNode(String id, DifyNodeData data) {
        super(id, NodeType.VARIABLE_ASSIGNER, data);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing variable assigner node: {}", id);

        // 处理 items 列表（Dify 新版 assigner 格式）
        List<Map<String, Object>> items = data.assignerItems();
        if (items != null && !items.isEmpty()) {
            for (Map<String, Object> item : items) {
                assignItem(item, context);
            }
            return;
        }

        // 兼容旧格式：直接写入自身节点
        Map<String, Object> outputs = data.additionalProperties();
        if (outputs != null) {
            for (Map.Entry<String, Object> entry : outputs.entrySet()) {
                if ("items".equals(entry.getKey())) {
                    continue; // 已处理
                }
                Object value = entry.getValue();
                if (value instanceof String) {
                    String strValue = (String) value;
                    value = resolveVariables(context, strValue);
                }
                context.setVariable(id, entry.getKey(), value);
                log.debug("Variable assigner node: {} = {}", entry.getKey(), value);
            }
        }
    }

    /**
     * 处理单个赋值条目。
     * value: [sourceNodeId, sourceVarName] — 值来源
     * variable_selector: [targetNodeId, targetVarName] — 写入目标
     */
    @SuppressWarnings("unchecked")
    private void assignItem(Map<String, Object> item, NodeExecutionContext context) {
        // 解析值来源
        Object valueObj = item.get("value");
        Object resolvedValue = null;

        if (valueObj instanceof List && ((List<?>) valueObj).size() >= 2) {
            List<?> valSelector = (List<?>) valueObj;
            String sourceNodeId = String.valueOf(valSelector.get(0));
            String sourceVarName = String.valueOf(valSelector.get(1));
            resolvedValue = context.getVariable(sourceNodeId, sourceVarName);
        } else if (valueObj != null) {
            resolvedValue = valueObj;
        }

        if (resolvedValue == null) {
            return;
        }

        // 解析写入目标
        Object targetObj = item.get("variable_selector");
        if (targetObj instanceof List && ((List<?>) targetObj).size() >= 2) {
            List<?> targetSelector = (List<?>) targetObj;
            String targetNodeId = String.valueOf(targetSelector.get(0));
            String targetVarName = String.valueOf(targetSelector.get(1));

            // 关键修复：assigner 写到 conversation.X 时必须走 conversation 专用通道,
            // 否则通用 setVariable 会把值塞进 nodeOutputs["conversation"],
            // 而 resolver 从 conversationVariables 读,两条路径永不相交,
            // 导致 {{#conversation.X#}} 永远解析失败,字面回显。
            if (SystemVariable.CONVERSATION.equals(targetNodeId)) {
                context.setConversationVariable(targetVarName, resolvedValue);
                log.debug("Assigner: conversation.{} = {}", targetVarName, resolvedValue);
                return;
            }

            context.setVariable(targetNodeId, targetVarName, resolvedValue);
            log.debug("Assigner: {}.{} = {}", targetNodeId, targetVarName, resolvedValue);
        } else {
            // 无 variable_selector 时写入自身节点
            String varName = String.valueOf(item.getOrDefault("name", "output"));
            context.setVariable(id, varName, resolvedValue);
        }
    }
}
