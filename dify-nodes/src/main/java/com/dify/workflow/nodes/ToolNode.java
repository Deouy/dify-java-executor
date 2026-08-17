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
 * Tool node - executes a tool or sub-workflow.
 * Supports calling nested workflows (sub-processes).
 */
public class ToolNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(ToolNode.class);

    public ToolNode(String id, DifyNodeData data) {
        super(id, NodeType.TOOL, data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing tool node: {}", id);

        String providerType = data.providerType();
        String providerName = data.providerName();
        String toolName = data.toolName();

        log.info("Tool node {} calling tool: {} (provider: {}, type: {})", id, toolName, providerName, providerType);

        // Build input parameters from tool_parameters
        Map<String, Object> inputs = new HashMap<>();
        Map<String, Object> toolParams = data.toolParameters();
        if (toolParams != null) {
            for (Map.Entry<String, Object> entry : toolParams.entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();
                Object resolvedValue = resolveParameter(paramValue, context);
                inputs.put(paramName, resolvedValue);
                log.debug("Tool parameter {} = {}", paramName, resolvedValue);
            }
        }

        if ("workflow".equals(providerType)) {
            // Execute sub-workflow
            executeSubWorkflow(context, providerName, inputs);
        } else {
            // TODO: Support other tool types (API calls, plugins, etc.)
            throw new UnsupportedOperationException("Tool type not yet supported: " + providerType);
        }
    }

    /**
     * Resolve a parameter value from the YAML structure.
     * Parameters can be:
     * - constant: { type: "constant", value: <actual_value> }
     * - variable: { type: "variable", value: [nodeId, variableName] }
     */
    private Object resolveParameter(Object paramValue, NodeExecutionContext context) {
        if (paramValue == null) {
            return null;
        }

        if (!(paramValue instanceof Map)) {
            return paramValue;
        }

        Map<String, Object> paramMap = (Map<String, Object>) paramValue;
        String type = paramMap.get("type") != null ? paramMap.get("type").toString() : null;
        Object value = paramMap.get("value");

        if(value != null){
            value = resolveVariables(context, value.toString());
        }

        if ("constant".equals(type)) {
            return value;
        } else if ("variable".equals(type)) {
            return resolveVariableReference(value, context);
        }else if ("mixed".equals(type)) {
            return resolveVariableReference(value, context);
        }

        return value;
    }

    /**
     * Resolve a variable reference like ["nodeId", "variableName"].
     */
    private Object resolveVariableReference(Object value, NodeExecutionContext context) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.size() >= 2) {
                String nodeId = list.get(0).toString();
                String variableName = list.get(1).toString();
                return context.getVariable(nodeId, variableName);
            }
        }
        return value;
    }

    /**
     * Execute a sub-workflow.
     */
    private void executeSubWorkflow(NodeExecutionContext context, String workflowName, Map<String, Object> inputs) throws Exception {
        log.info("Executing sub-workflow: {} with inputs: {}", workflowName, inputs);

        // 通过 context 查找子工作流(由 executor.setSubWorkflows() 注入的注册表)
        // key 用 toolName(对齐 Dify 官方约定,tool 节点用 tool_name 引用 workflow 的 app.name)
        // 优先用 data.toolName() 因为 providerName 可能跟 app.name 不一致
        String dataToolName = data.toolName();
        String lookupKey = dataToolName != null ? dataToolName : workflowName;
        Map<String, Object> outputs;
        try {
            outputs = context.executeNamedSubWorkflow(lookupKey, inputs);
        } catch (UnsupportedOperationException e) {
            // Context 未实现命名查找,fallback:直接传 yaml
            log.warn("Context does not support executeNamedSubWorkflow, falling back to loadSubWorkflow");
            String subWorkflowYaml = loadSubWorkflow(lookupKey);
            if (subWorkflowYaml == null) {
                throw new IllegalArgumentException("Sub-workflow not found: " + lookupKey);
            }
            outputs = context.executeSubWorkflow(subWorkflowYaml, inputs);
        }

        // Copy outputs from sub-workflow to current context
        if (outputs != null) {
            for (Map.Entry<String, Object> entry : outputs.entrySet()) {
                context.setVariable(id, entry.getKey(), entry.getValue());
                log.debug("Sub-workflow output {} = {}", entry.getKey(), entry.getValue());
            }
        }

        log.info("Sub-workflow {} completed successfully", workflowName);
    }

    /**
     * Load sub-workflow YAML by name.
     * This is a simplified implementation - in production, this would look up
     * from a workflow registry or load from files.
     */
    private String loadSubWorkflow(String workflowName) {
        // Simple lookup - in production, this would be more sophisticated
        // For now, we support predefined workflow mappings
        if ("a+b".equals(workflowName)) {
            return getEmbeddedWorkflow("testworkflow");
        }
        log.warn("Unknown workflow: {}", workflowName);
        return null;
    }

    /**
     * Get embedded workflow YAML for testing.
     * In production, workflows would be loaded from files or a registry.
     */
    private String getEmbeddedWorkflow(String name) {
        // This is a simplified version - the actual workflow would be loaded
        // from files or a workflow registry
        if ("testworkflow".equals(name)) {
            StringBuilder sb = new StringBuilder();
        sb.append("kind: app\n");
        sb.append("version: 0.5.0\n");
        sb.append("app:\n");
        sb.append("  name: testworkflow\n");
        sb.append("  mode: workflow\n");
        sb.append("  description: ''\n");
        sb.append("  icon: 🤖\n");
        sb.append("  icon_background: '#FFEAD5'\n");
        sb.append("  use_icon_as_answer_icon: false\n");
        sb.append("dependencies: []\n");
        sb.append("workflow:\n");
        sb.append("  conversation_variables: []\n");
        sb.append("  environment_variables: []\n");
        sb.append("  features:\n");
        sb.append("    file_upload:\n");
        sb.append("      enabled: false\n");
        sb.append("    retriever_resource:\n");
        sb.append("      enabled: true\n");
        sb.append("    sensitive_word_avoidance:\n");
        sb.append("      enabled: false\n");
        sb.append("    speech_to_text:\n");
        sb.append("      enabled: false\n");
        sb.append("    suggested_questions: []\n");
        sb.append("    suggested_questions_after_answer:\n");
        sb.append("      enabled: false\n");
        sb.append("    text_to_speech:\n");
        sb.append("      enabled: false\n");
        sb.append("  graph:\n");
        sb.append("    edges:\n");
        sb.append("    - data:\n");
        sb.append("        isInIteration: false\n");
        sb.append("        isInLoop: false\n");
        sb.append("        sourceType: start\n");
        sb.append("        targetType: code\n");
        sb.append("      id: start-to-code\n");
        sb.append("      source: 'start_node'\n");
        sb.append("      sourceHandle: source\n");
        sb.append("      target: code_node\n");
        sb.append("      targetHandle: target\n");
        sb.append("      type: custom\n");
        sb.append("    - data:\n");
        sb.append("        isInIteration: false\n");
        sb.append("        isInLoop: false\n");
        sb.append("        sourceType: code\n");
        sb.append("        targetType: end\n");
        sb.append("      id: code-to-end\n");
        sb.append("      source: code_node\n");
        sb.append("      sourceHandle: source\n");
        sb.append("      target: end_node\n");
        sb.append("      targetHandle: target\n");
        sb.append("      type: custom\n");
        sb.append("    nodes:\n");
        sb.append("    - data:\n");
        sb.append("        selected: false\n");
        sb.append("        title: 用户输入\n");
        sb.append("        type: start\n");
        sb.append("        variables:\n");
        sb.append("        - default: ''\n");
        sb.append("          hint: ''\n");
        sb.append("          label: a\n");
        sb.append("          max_length: 48\n");
        sb.append("          options: []\n");
        sb.append("          placeholder: ''\n");
        sb.append("          required: true\n");
        sb.append("          type: number\n");
        sb.append("          variable: a\n");
        sb.append("        - default: ''\n");
        sb.append("          hint: ''\n");
        sb.append("          label: b\n");
        sb.append("          max_length: 48\n");
        sb.append("          options: []\n");
        sb.append("          placeholder: ''\n");
        sb.append("          required: true\n");
        sb.append("          type: number\n");
        sb.append("          variable: b\n");
        sb.append("      height: 135\n");
        sb.append("      id: start_node\n");
        sb.append("      position:\n");
        sb.append("        x: 80\n");
        sb.append("        y: 282\n");
        sb.append("      positionAbsolute:\n");
        sb.append("        x: 80\n");
        sb.append("        y: 282\n");
        sb.append("      selected: false\n");
        sb.append("      sourcePosition: right\n");
        sb.append("      targetPosition: left\n");
        sb.append("      type: custom\n");
        sb.append("      width: 242\n");
        sb.append("    - data:\n");
        sb.append("        outputs:\n");
        sb.append("        - value_selector:\n");
        sb.append("          - code_node\n");
        sb.append("          - result\n");
        sb.append("          value_type: number\n");
        sb.append("          variable: result\n");
        sb.append("        - value_selector:\n");
        sb.append("          - code_node\n");
        sb.append("          - result2\n");
        sb.append("          value_type: string\n");
        sb.append("          variable: result2\n");
        sb.append("        selected: false\n");
        sb.append("        title: 输出\n");
        sb.append("        type: end\n");
        sb.append("      height: 88\n");
        sb.append("      id: end_node\n");
        sb.append("      position:\n");
        sb.append("        x: 918\n");
        sb.append("        y: 316\n");
        sb.append("      positionAbsolute:\n");
        sb.append("        x: 918\n");
        sb.append("        y: 316\n");
        sb.append("      selected: false\n");
        sb.append("      sourcePosition: right\n");
        sb.append("      targetPosition: left\n");
        sb.append("      type: custom\n");
        sb.append("      width: 242\n");
        sb.append("    - data:\n");
        sb.append("        code: |\n");
        sb.append("          function main({arg1, arg2}) {\n");
        sb.append("              return {\n");
        sb.append("                  result: arg1 + arg2,\n");
        sb.append("                  result2: arg1+\"\"+arg2+\"\"\n");
        sb.append("              }\n");
        sb.append("          }\n");
        sb.append("        code_language: javascript\n");
        sb.append("        outputs:\n");
        sb.append("          result:\n");
        sb.append("            children: null\n");
        sb.append("            type: number\n");
        sb.append("          result2:\n");
        sb.append("            children: null\n");
        sb.append("            type: string\n");
        sb.append("        selected: true\n");
        sb.append("        title: 代码执行\n");
        sb.append("        type: code\n");
        sb.append("        variables:\n");
        sb.append("        - value_selector:\n");
        sb.append("          - start_node\n");
        sb.append("          - a\n");
        sb.append("          value_type: number\n");
        sb.append("          variable: arg1\n");
        sb.append("        - value_selector:\n");
        sb.append("          - start_node\n");
        sb.append("          - b\n");
        sb.append("          value_type: number\n");
        sb.append("          variable: arg2\n");
        sb.append("      height: 52\n");
        sb.append("      id: code_node\n");
        sb.append("      position:\n");
        sb.append("        x: 440\n");
        sb.append("        y: 293\n");
        sb.append("      positionAbsolute:\n");
        sb.append("        x: 440\n");
        sb.append("        y: 293\n");
        sb.append("      selected: true\n");
        sb.append("      sourcePosition: right\n");
        sb.append("      targetPosition: left\n");
        sb.append("      type: custom\n");
        sb.append("      width: 242\n");
        sb.append("    viewport:\n");
        sb.append("      x: 156\n");
        sb.append("      y: 0\n");
        sb.append("      zoom: 1\n");
        sb.append("  rag_pipeline_variables: []\n");
        return sb.toString();
        }
        return null;
    }
}
