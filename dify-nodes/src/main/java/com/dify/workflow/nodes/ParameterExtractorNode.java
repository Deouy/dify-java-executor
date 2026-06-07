package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.DifyParameter;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parameter Extractor node - extracts structured parameters from text.
 */
public class ParameterExtractorNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(ParameterExtractorNode.class);

    public ParameterExtractorNode(String id, DifyNodeData data) {
        super(id, NodeType.PARAMETER_EXTRACTOR, data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing parameter extractor node: {}", id);

        List<DifyParameter> parameters = data.parameters();
        if (parameters == null || parameters.isEmpty()) {
            throw new IllegalArgumentException("Parameter extractor node has no parameters: " + id);
        }

        String resultType = data.resultType();
        if (resultType == null) {
            resultType = "string";
        }

        // Build output structure based on result type
        Map<String, Object> extractedParams = new LinkedHashMap<>();

        for (DifyParameter param : parameters) {
            String name = param.name();
            String label = param.label();
            String type = param.type();

            // Use instruction to guide parameter extraction
            String instruction = param.instruction();
            if (instruction != null) {
                instruction = resolveVariables(context, instruction);
            }

            // Placeholder - actual extraction would require LLM
            Object value = extractParameterValue(context, param);

            extractedParams.put(name, value);
            log.debug("Extracted parameter: {} ({}) = {}", name, type, value);
        }

        context.setVariable(id, "parameters", extractedParams);

        if ("string".equals(resultType)) {
            context.setVariable(id, "text", buildStringOutput(extractedParams));
        }
    }

    private Object extractParameterValue(NodeExecutionContext context, DifyParameter param) {
        // In a real implementation, this would use an LLM to extract the parameter
        // For now, return a placeholder
        String name = param.name();
        Object defaultValue = param.defaultValue();
        return defaultValue != null ? defaultValue : "";
    }

    private String buildStringOutput(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            sb.append(key).append(": ").append(value).append("\n");
        });
        return sb.toString().trim();
    }
}
