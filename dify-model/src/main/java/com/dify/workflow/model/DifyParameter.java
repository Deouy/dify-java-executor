package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Objects;

/**
 * Dify parameter extractor parameter definition.
 */
public final class DifyParameter {
    @JSONField(name = "name")
    private final String name;
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
    @JSONField(name = "instruction")
    private final String instruction;

    public DifyParameter(String name, String label, String type, Boolean required, Integer maxLength,
                         List<String> options, Object defaultValue, String instruction) {
        this.name = name;
        this.label = label;
        this.type = type;
        this.required = required != null ? required : false;
        this.maxLength = maxLength;
        this.options = options;
        this.defaultValue = defaultValue;
        this.instruction = instruction;
    }

    public String name() { return name; }
    public String label() { return label; }
    public String type() { return type; }
    public Boolean required() { return required; }
    public Integer maxLength() { return maxLength; }
    public List<String> options() { return options; }
    public Object defaultValue() { return defaultValue; }
    public String instruction() { return instruction; }

    public String getName() { return name; }
    public String getLabel() { return label; }
    public String getType() { return type; }
    public Boolean getRequired() { return required; }
    public Integer getMaxLength() { return maxLength; }
    public List<String> getOptions() { return options; }
    public Object getDefaultValue() { return defaultValue; }
    public String getInstruction() { return instruction; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyParameter)) return false;
        DifyParameter that = (DifyParameter) o;
        return Objects.equals(name, that.name)
                && Objects.equals(label, that.label)
                && Objects.equals(type, that.type)
                && Objects.equals(required, that.required)
                && Objects.equals(maxLength, that.maxLength)
                && Objects.equals(options, that.options)
                && Objects.equals(defaultValue, that.defaultValue)
                && Objects.equals(instruction, that.instruction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, label, type, required, maxLength, options, defaultValue, instruction);
    }

    @Override
    public String toString() {
        return String.format("DifyParameter[name=%s, label=%s, type=%s, required=%s, maxLength=%s, options=%s, defaultValue=%s, instruction=%s]",
                name, label, type, required, maxLength, options, defaultValue, instruction);
    }
}
