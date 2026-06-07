package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Objects;

/**
 * Dify end node output definition.
 */
public final class DifyEndOutput {
    @JSONField(name = "variable")
    private final String variable;
    @JSONField(name = "value_selector")
    private final List<String> valueSelector;
    @JSONField(name = "value_type")
    private final String valueType;

    public DifyEndOutput(String variable, List<String> valueSelector, String valueType) {
        this.variable = variable;
        this.valueSelector = valueSelector;
        this.valueType = valueType;
    }

    public String variable() { return variable; }
    public List<String> valueSelector() { return valueSelector; }
    public String valueType() { return valueType; }

    public String getVariable() { return variable; }
    public List<String> getValueSelector() { return valueSelector; }
    public String getValueType() { return valueType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyEndOutput)) return false;
        DifyEndOutput that = (DifyEndOutput) o;
        return Objects.equals(variable, that.variable)
                && Objects.equals(valueSelector, that.valueSelector)
                && Objects.equals(valueType, that.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable, valueSelector, valueType);
    }

    @Override
    public String toString() {
        return String.format("DifyEndOutput[variable=%s, valueSelector=%s, valueType=%s]", variable, valueSelector, valueType);
    }
}
