package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.DifyVariable;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Template Transform node - transforms text using a template.
 */
public class TemplateTransformNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(TemplateTransformNode.class);

    public TemplateTransformNode(String id, DifyNodeData data) {
        super(id, NodeType.TEMPLATE_TRANSFORM, data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing template transform node: {}", id);

        String template = data.template();
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException("Template transform node has no template: " + id);
        }

        String result = template;

        // 1. 处理 Jinja2 风格 {{ varName }} 占位符
        //    从 variables 列表的 value_selector 解析每个占位符的值
        List<com.dify.workflow.model.DifyVariable> varList = data.variables();
        if (varList != null) {
            for (com.dify.workflow.model.DifyVariable variable : varList) {
                String placeholder = "{{ " + variable.variable() + " }}";
                Object resolvedValue = resolveVariableValue(variable, context);
                if (resolvedValue != null) {
                    result = result.replace(placeholder, String.valueOf(resolvedValue));
                }
                // 也处理无空格格式 {{varName}}
                String placeholderCompact = "{{" + variable.variable() + "}}";
                if (resolvedValue != null) {
                    result = result.replace(placeholderCompact, String.valueOf(resolvedValue));
                }
            }
        }

        // 2. 处理 {{#nodeId.var#}} 格式（兼容 LLM prompt_template 中的引用）
        result = resolveVariables(context, result);

        context.setVariable(id, "output", result);
        context.setVariable(id, "text", result);

        log.debug("Template transform node output: {}", result);
    }

    /**
     * 根据 value_selector 解析变量的值。
     */
    private Object resolveVariableValue(DifyVariable variable, NodeExecutionContext context) {
        List<String> selector = variable.valueSelector();
        if (selector != null && selector.size() >= 2) {
            String sourceNodeId = selector.get(0);
            String sourceVariable = selector.get(1);
            return context.getVariable(sourceNodeId, sourceVariable);
        }
        return null;
    }
}
