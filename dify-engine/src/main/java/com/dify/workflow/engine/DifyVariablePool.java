package com.dify.workflow.engine;

import com.dify.workflow.model.VariablePool;
import com.dify.workflow.model.node.SystemVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Variable pool for workflow execution.
 * Stores variables from all nodes during workflow execution.
 */
public class DifyVariablePool implements VariablePool {

    private static final Logger log = LoggerFactory.getLogger(DifyVariablePool.class);

    private final Map<String, Map<String, Object>> nodeOutputs = new ConcurrentHashMap<>();
    private final Map<String, Object> systemVariables = new ConcurrentHashMap<>();
    private final Map<String, Object> environmentVariables = new ConcurrentHashMap<>();
    private final Map<String, Object> conversationVariables = new ConcurrentHashMap<>();

    public DifyVariablePool() {
    }

    /**
     * 深拷贝变量池，用于迭代节点隔离各次迭代的变量状态。
     * 参考 Dify 的 variable_pool.model_copy(deep=True)。
     */
    public DifyVariablePool copy() {
        DifyVariablePool clone = new DifyVariablePool();
        for (Map.Entry<String, Map<String, Object>> entry : nodeOutputs.entrySet()) {
            clone.nodeOutputs.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
        }
        clone.systemVariables.putAll(systemVariables);
        clone.environmentVariables.putAll(environmentVariables);
        clone.conversationVariables.putAll(conversationVariables);
        return clone;
    }

    @Override
    public Object get(String nodeId, String variableName) {
        // 关键修复:nodeId 是 "conversation" 时从 conversationVariables 读,
        // 而不是 nodeOutputs["conversation"]。这样 assigner 通过
        // setConversationVariable 写入的值,能被任何走 context.getVariable
        // (包括 template-transform 的 value_selector 解析)读出来。
        if (SystemVariable.CONVERSATION.equals(nodeId)) {
            return conversationVariables.get(variableName);
        }
        // 关键修复:nodeId 是 "sys" 时从 systemVariables 读,
        // 而不是 nodeOutputs["sys"]。StartNode.setSystemVariable 通过
        // VariablePool.setSystem 写入 systemVariables 桶,但
        // resolveVariables 走 VariablePool.getSystem 时读 systemVariables,
        // 两条路径现在一致;{{#sys.query#}} 也能正确解析。
        if (SystemVariable.SYS.equals(nodeId)) {
            return systemVariables.get(variableName);
        }
        Map<String, Object> nodeVars = nodeOutputs.get(nodeId);
        if (nodeVars != null) {
            return nodeVars.get(variableName);
        }
        return null;
    }

    @Override
    public void set(String nodeId, String variableName, Object value) {
        log.debug("Setting variable: {}.{} = {}", nodeId, variableName, value);
        nodeOutputs.computeIfAbsent(nodeId, k -> new ConcurrentHashMap<>()).put(variableName, value);
    }

    @Override
    public Map<String, Object> getNodeVariables(String nodeId) {
        return nodeOutputs.getOrDefault(nodeId, new ConcurrentHashMap<>());
    }

    @Override
    public Map<String, Object> getNodeOutputs(String nodeId) {
        return nodeOutputs.getOrDefault(nodeId, new ConcurrentHashMap<>());
    }

    @Override
    public Object getSystem(String field) {
        return systemVariables.get(field);
    }

    @Override
    public void setSystem(String field, Object value) {
        log.debug("Setting system variable: {} = {}", field, value);
        systemVariables.put(field, value);
    }

    @Override
    public Object getEnvironment(String field) {
        return environmentVariables.get(field);
    }

    @Override
    public void setEnvironment(String field, Object value) {
        log.debug("Setting environment variable: {} = {}", field, value);
        environmentVariables.put(field, value);
    }

    @Override
    public Object getConversation(String field) {
        return conversationVariables.get(field);
    }

    @Override
    public void setConversation(String field, Object value) {
        log.debug("Setting conversation variable: conversation.{} = {}", field, value);
        conversationVariables.put(field, value);
    }

    /**
     * Set a system variable.
     *
     * @param name Variable name (e.g., "query", "files")
     * @param value Variable value
     */
    public void setSystemVariable(String name, Object value) {
        log.debug("Setting system variable: {} = {}", name, value);
        systemVariables.put(name, value);
    }

    /**
     * Get a system variable.
     *
     * @param name Variable name
     * @return Variable value or null
     */
    public Object getSystemVariable(String name) {
        return systemVariables.get(name);
    }

    /**
     * Set an environment variable.
     *
     * @param name Variable name
     * @param value Variable value
     */
    public void setEnvironmentVariable(String name, Object value) {
        log.debug("Setting environment variable: {} = {}", name, value);
        environmentVariables.put(name, value);
    }

    /**
     * Get an environment variable.
     *
     * @param name Variable name
     * @return Variable value or null
     */
    public Object getEnvironmentVariable(String name) {
        return environmentVariables.get(name);
    }

    /**
     * Set a conversation variable.
     *
     * @param name Variable name
     * @param value Variable value
     */
    public void setConversationVariable(String name, Object value) {
        log.debug("Setting conversation variable: {} = {}", name, value);
        conversationVariables.put(name, value);
    }

    /**
     * Get a conversation variable.
     *
     * @param name Variable name
     * @return Variable value or null
     */
    public Object getConversationVariable(String name) {
        return conversationVariables.get(name);
    }

    /**
     * Get all node IDs that have outputs.
     *
     * @return Set of node IDs
     */
    public Set<String> getNodeIds() {
        return nodeOutputs.keySet();
    }

    /**
     * Clear all variables.
     */
    public void clear() {
        nodeOutputs.clear();
        systemVariables.clear();
        environmentVariables.clear();
        conversationVariables.clear();
    }

    @Override
    public String toString() {
        return "DifyVariablePool{" +
                "nodeOutputs=" + nodeOutputs +
                ", systemVariables=" + systemVariables +
                ", environmentVariables=" + environmentVariables +
                ", conversationVariables=" + conversationVariables +
                '}';
    }
}
