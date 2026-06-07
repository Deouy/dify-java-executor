package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Objects;

/**
 * Dify variable definition (used in start node, code node, etc.).
 */
public final class DifyVariable {
    @JSONField(name = "variable")
    private final String variable;
    @JSONField(name = "label")
    private final String label;
    @JSONField(name = "type")
    private final String type;
    @JSONField(name = "required")
    private final Boolean required;
    @JSONField(name = "max_length")
    private final Integer maxLength;
    @JSONField(name = "options")
    private final List<String> options;
    @JSONField(name = "default")
    private final Object defaultValue;
    @JSONField(name = "value_selector")
    private final List<String> valueSelector;
    @JSONField(name = "value_type")
    private final String valueType;

    public DifyVariable(String variable, String label, String type, Boolean required, Integer maxLength,
                        List<String> options, Object defaultValue, List<String> valueSelector, String valueType) {
        this.variable = variable;
        this.label = label;
        this.type = type;
        this.required = required != null ? required : false;
        this.maxLength = maxLength;
        this.options = options;
        this.defaultValue = defaultValue;
        this.valueSelector = valueSelector;
        this.valueType = valueType;
    }

    public String variable() { return variable; }
    public String label() { return label; }
    public String type() { return type; }
    public Boolean required() { return required; }
    public Integer maxLength() { return maxLength; }
    public List<String> options() { return options; }
    public Object defaultValue() { return defaultValue; }
    public List<String> valueSelector() { return valueSelector; }
    public String valueType() { return valueType; }

    public String getVariable() { return variable; }
    public String getLabel() { return label; }
    public String getType() { return type; }
    public Boolean getRequired() { return required; }
    public Integer getMaxLength() { return maxLength; }
    public List<String> getOptions() { return options; }
    public Object getDefaultValue() { return defaultValue; }
    public List<String> getValueSelector() { return valueSelector; }
    public String getValueType() { return valueType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyVariable)) return false;
        DifyVariable that = (DifyVariable) o;
        return Objects.equals(variable, that.variable)
                && Objects.equals(label, that.label)
                && Objects.equals(type, that.type)
                && Objects.equals(required, that.required)
                && Objects.equals(maxLength, that.maxLength)
                && Objects.equals(options, that.options)
                && Objects.equals(defaultValue, that.defaultValue)
                && Objects.equals(valueSelector, that.valueSelector)
                && Objects.equals(valueType, that.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable, label, type, required, maxLength, options, defaultValue, valueSelector, valueType);
    }

    @Override
    public String toString() {
        return String.format("DifyVariable[variable=%s, label=%s, type=%s, required=%s, maxLength=%s, options=%s, defaultValue=%s, valueSelector=%s, valueType=%s]",
                variable, label, type, required, maxLength, options, defaultValue, valueSelector, valueType);
    }
}
