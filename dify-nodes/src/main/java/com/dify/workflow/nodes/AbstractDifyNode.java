package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.VariablePool;
import com.dify.workflow.parser.VariableResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for Dify nodes.
 * Provides common functionality for node execution.
 */
public abstract class AbstractDifyNode implements DifyNode {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String id;
    protected final String type;
    protected final DifyNodeData data;
    protected final VariableResolver variableResolver;

    /** 节点执行过程的中间数据（LLM请求详情等），由子类在执行时填充 */
    protected Map<String, Object> processData;

    protected AbstractDifyNode(String id, String type, DifyNodeData data) {
        this.id = id;
        this.type = type;
        this.data = data;
        this.variableResolver = new VariableResolver();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public DifyNodeData getData() {
        return data;
    }

    @Override
    public void execute(NodeExecutionContext context) throws Exception {
        log.info("Executing node: {} (type: {})", id, type);
        beforeExecute(context);
        try {
            doExecute(context);
        } catch (Exception e) {
            log.error("Node execution failed: {} - {}", id, e.getMessage(), e);
            context.setNodeStatus(id, "ERROR");
            throw e;
        } finally {
            afterExecute(context);
        }
    }

    /**
     * Hook method called before doExecute.
     */
    protected void beforeExecute(NodeExecutionContext context) {
        context.setNodeStatus(id, "RUNNING");
        context.setCurrentNodeId(id);
        log.debug("Node {} status set to RUNNING", id);
    }

    /**
     * Main execution logic - to be implemented by subclasses.
     */
    protected abstract void doExecute(NodeExecutionContext context) throws Exception;

    /**
     * Hook method called after doExecute.
     */
    protected void afterExecute(NodeExecutionContext context) {
        context.setNodeStatus(id, "FINISH");
        log.debug("Node {} status set to FINISH", id);
    }

    /**
     * 获取节点执行过程数据（LLM请求详情等）。
     * 由执行器在节点完成后读取并写入事件。
     */
    public Map<String, Object> getProcessData() {
        return processData;
    }

    /**
     * Get a variable from the pool.
     */
    protected Object getVariable(NodeExecutionContext context, String nodeId, String variableName) {
        return context.getVariable(nodeId, variableName);
    }

    /**
     * Resolve variables in a template string.
     */
    protected String resolveVariables(NodeExecutionContext context, String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        return variableResolver.resolve(template, ref -> {
            VariableResolver.VariableRef.Type type = ref.type();
            if (type == VariableResolver.VariableRef.Type.SYSTEM) {
                return context.getVariablePool().getSystem(ref.field()) != null
                        ? String.valueOf(context.getVariablePool().getSystem(ref.field())) : null;
            } else if (type == VariableResolver.VariableRef.Type.ENVIRONMENT) {
                return context.getVariablePool().getEnvironment(ref.field()) != null
                        ? String.valueOf(context.getVariablePool().getEnvironment(ref.field())) : null;
            } else if (type == VariableResolver.VariableRef.Type.CONVERSATION) {
                return context.getVariablePool().getConversation(ref.field()) != null
                        ? String.valueOf(context.getVariablePool().getConversation(ref.field())) : null;
            } else if (type == VariableResolver.VariableRef.Type.NODE) {
                // Use ref.nodeId() to get the source node, not the current node
                return context.getVariable(ref.nodeId(), ref.field()) != null
                        ? String.valueOf(context.getVariable(ref.nodeId(), ref.field())) : null;
            }
            return null;
        });
    }
}
