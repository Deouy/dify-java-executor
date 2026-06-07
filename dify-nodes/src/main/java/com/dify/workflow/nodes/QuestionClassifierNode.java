package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyClass;
import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Question Classifier node - classifies user questions into categories.
 */
public class QuestionClassifierNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(QuestionClassifierNode.class);

    public QuestionClassifierNode(String id, DifyNodeData data) {
        super(id, NodeType.QUESTION_CLASSIFIER, data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing question classifier node: {}", id);

        List<DifyClass> classes = data.classes();
        if (classes == null || classes.isEmpty()) {
            throw new IllegalArgumentException("Question classifier node has no classes: " + id);
        }

        String instruction = data.instruction();
        if (instruction != null) {
            instruction = resolveVariables(context, instruction);
        }

        // Placeholder - actual classification would require LLM
        String query = (String) context.getVariable("sys", "query");
        if (query == null) {
            query = "";
        }

        // Simple keyword-based classification (placeholder)
        String matchedClass = classifyQuery(query, classes);

        context.setVariable(id, "class", matchedClass);
        context.setVariable(id, "class_name", matchedClass);

        log.debug("Question classifier node: query='{}' classified as '{}'", query, matchedClass);
    }

    private String classifyQuery(String query, List<DifyClass> classes) {
        // Simple keyword matching - in real implementation would use LLM
        for (DifyClass cls : classes) {
            String name = cls.name() != null ? cls.name() : cls.label();
            if (name != null && query.toLowerCase().contains(name.toLowerCase())) {
                return name;
            }
        }
        // Return first class as default
        return classes.get(0).name() != null ? classes.get(0).name() : classes.get(0).label();
    }
}
