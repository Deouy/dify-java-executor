package com.dify.workflow.model.node;
import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Dify node type constants.
 */
public final class NodeType {

    private NodeType() {}

    // Basic nodes
    public static final String START = "start";
    public static final String END = "end";
    public static final String ANSWER = "answer";

    // Data processing nodes
    public static final String LLM = "llm";
    public static final String CODE = "code";
    public static final String TEMPLATE_TRANSFORM = "template-transform";
    public static final String VARIABLE_AGGREGATOR = "variable_aggregator";
    public static final String VARIABLE_ASSIGNER = "variable_assigner";

    // Logic control nodes
    public static final String IF_ELSE = "if-else";
    public static final String ITERATION = "iteration";
    public static final String ITERATION_START = "iteration_start";
    public static final String LOOP = "loop";
    public static final String LOOP_START = "loop_start";

    // External integration nodes
    public static final String HTTP_REQUEST = "http-request";
    public static final String TOOL = "tool";
    public static final String AGENT = "agent";
    public static final String QUESTION_CLASSIFIER = "question_classifier";

    // Tool nodes
    public static final String PARAMETER_EXTRACTOR = "parameter_extractor";
    public static final String LIST_FILTER = "list_filter";
    public static final String DOC_EXTRACTOR = "doc-extractor";

    // Ignored nodes (not supported)
    public static final String KNOWLEDGE_RETRIEVAL = "knowledge-retrieval";
    public static final String KNOWLEDGE_INDEX = "knowledge-index";
    public static final String TRIGGER_WEBHOOK = "trigger-webhook";
    public static final String TRIGGER_SCHEDULE = "trigger-schedule";
    public static final String TRIGGER_PLUGIN = "trigger-plugin";
    public static final String MCP_SERVER = "mcp_server";

    /**
     * Check if a node type is supported.
     */
    public static boolean isSupported(String type) {
        if (type == null) return false;
        switch (type) {
            case KNOWLEDGE_RETRIEVAL:
            case KNOWLEDGE_INDEX:
            case TRIGGER_WEBHOOK:
            case TRIGGER_SCHEDULE:
            case TRIGGER_PLUGIN:
            case MCP_SERVER:
                return false;
            default:
                return true;
        }
    }

    /**
     * Check if a node type should be ignored.
     */
    public static boolean isIgnored(String type) {
        return !isSupported(type);
    }
}
