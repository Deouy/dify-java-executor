package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Objects;

/**
 * Dify condition for if-else node.
 */
public final class DifyCondition {
    @JSONField(name = "id")
    private final String id;
    @JSONField(name = "comparison_operator")
    private final String comparisonOperator;
    @JSONField(name = "value")
    private final Object value;
    @JSONField(name = "varType")
    private final String varType;
    @JSONField(name = "variable_selector")
    private final List<String> variableSelector;

    public DifyCondition(String id, String comparisonOperator, Object value, String varType, List<String> variableSelector) {
        this.id = id;
        this.comparisonOperator = comparisonOperator;
        this.value = value;
        this.varType = varType;
        this.variableSelector = variableSelector;
    }

    public String id() { return id; }
    public String comparisonOperator() { return comparisonOperator; }
    public Object value() { return value; }
    public String varType() { return varType; }
    public List<String> variableSelector() { return variableSelector; }

    public String getId() { return id; }
    public String getComparisonOperator() { return comparisonOperator; }
    public Object getValue() { return value; }
    public String getVarType() { return varType; }
    public List<String> getVariableSelector() { return variableSelector; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyCondition)) return false;
        DifyCondition that = (DifyCondition) o;
        return Objects.equals(id, that.id)
                && Objects.equals(comparisonOperator, that.comparisonOperator)
                && Objects.equals(value, that.value)
                && Objects.equals(varType, that.varType)
                && Objects.equals(variableSelector, that.variableSelector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, comparisonOperator, value, varType, variableSelector);
    }

    @Override
    public String toString() {
        return String.format("DifyCondition[id=%s, comparisonOperator=%s, value=%s, varType=%s, variableSelector=%s]",
                id, comparisonOperator, value, varType, variableSelector);
    }
}
