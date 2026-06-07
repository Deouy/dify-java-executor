package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.node.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registry for node factories.
 * Nodes register themselves via static initialization.
 */
public class NodeRegistry {

    private static final Logger log = LoggerFactory.getLogger(NodeRegistry.class);
    private static final Map<String, NodeFactory> factories = new HashMap<>();

    static {
        registerDefaults();
    }

    private static void registerDefaults() {
        register(NodeType.START, StartNode::new);
        register(NodeType.END, EndNode::new);
        register(NodeType.ANSWER, AnswerNode::new);
        register(NodeType.LLM, LlmNode::new);
        register(NodeType.CODE, CodeNode::new);
        register(NodeType.HTTP_REQUEST, HttpRequestNode::new);
        register(NodeType.IF_ELSE, IfElseNode::new);
        register(NodeType.TEMPLATE_TRANSFORM, TemplateTransformNode::new);
        register(NodeType.VARIABLE_ASSIGNER, VariableAssignerNode::new);
        register(NodeType.VARIABLE_AGGREGATOR, VariableAggregatorNode::new);
        register(NodeType.PARAMETER_EXTRACTOR, ParameterExtractorNode::new);
        register(NodeType.QUESTION_CLASSIFIER, QuestionClassifierNode::new);
        register(NodeType.TOOL, ToolNode::new);
        register(NodeType.ITERATION, IterationNode::new);
        register(NodeType.ITERATION_START, IterationStartNode::new);
        register(NodeType.LOOP, LoopNode::new);
        register(NodeType.LOOP_START, LoopStartNode::new);
        register(NodeType.AGENT, AgentNode::new);
        register(NodeType.LIST_FILTER, ListFilterNode::new);
        register(NodeType.DOC_EXTRACTOR, DocExtractorNode::new);

        // YAML 中使用的别名（Dify 导出格式兼容）
        register("assigner", VariableAssignerNode::new);
        register("list_operator", ListFilterNode::new);  // YAML 使用 list-operator
        register("document_extractor", DocExtractorNode::new);  // YAML 使用 document-extractor
        register("end", EndNode::new);

        log.info("Registered {} node types", factories.size());
    }

    /**
     * Register a node factory.
     */
    public static void register(String type, NodeFactory factory) {
        factories.put(type, factory);
        log.debug("Registered node factory for type: {}", type);
    }

    /**
     * Register a node factory using a supplier.
     */
    public static void register(String type, Supplier<NodeFactory> factorySupplier) {
        factories.put(type, factorySupplier.get());
        log.debug("Registered node factory for type: {}", type);
    }

    /**
     * Create a node instance.
     *
     * @param type Node type
     * @param id Node ID
     * @param data Node data
     * @return Node instance
     * @throws IllegalArgumentException if type is not registered
     */
    public static DifyNode create(String type, String id, DifyNodeData data) {
        // 归一化类型名：YAML 中使用连字符（如 "loop-start"），NodeType 使用下划线（如 "loop_start"）
        String normalizedType = type.replace('-', '_');
        NodeFactory factory = factories.get(normalizedType);
        if (factory == null) {
            // 回退到原始类型名
            factory = factories.get(type);
        }
        if (factory == null) {
            throw new IllegalArgumentException("Unsupported node type: " + type + ". Available types: " + factories.keySet());
        }
        return factory.create(id, data);
    }

    /**
     * Check if a node type is registered.
     */
    public static boolean isRegistered(String type) {
        return factories.containsKey(type);
    }

    /**
     * Get all registered node types.
     */
    public static java.util.Set<String> getRegisteredTypes() {
        return java.util.Collections.unmodifiableSet(factories.keySet());
    }
}