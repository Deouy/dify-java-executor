package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyEndOutput;
import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * End node - marks the end of a workflow branch.
 * Collects outputs and prepares the final workflow result.
 */
public class EndNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(EndNode.class);

    public EndNode(String id, DifyNodeData data) {
        super(id, "end", data);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing end node: {}", id);

        Object outputsObj = data.outputs();
        if (outputsObj instanceof List) {
            List<?> outputsList = (List<?>) outputsObj;
            for (Object item : outputsList) {
                if (item instanceof Map) {
                    Map<?, ?> outputMap = (Map<?, ?>) item;
                    String variable = (String) outputMap.get("variable");
                    Object valueSelectorObj = outputMap.get("value_selector");
                    if (variable != null && valueSelectorObj instanceof List) {
                        List<?> valueSelector = (List<?>) valueSelectorObj;
                        if (valueSelector.size() >= 2) {
                            String sourceNodeId = String.valueOf(valueSelector.get(0));
                            String variableName = String.valueOf(valueSelector.get(1));
                            Object value = context.getVariable(sourceNodeId, variableName);
                            context.setOutput(variable, value);
                            log.debug("End node output: {} = {}", variable, value);
                        }
                    }
                }
            }
        }

        // Set completion marker
        context.setVariable(id, "_completed", true);
    }
}
