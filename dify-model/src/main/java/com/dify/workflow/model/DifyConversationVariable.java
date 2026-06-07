package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Objects;

/**
 * Dify conversation variable definition.
 */
public final class DifyConversationVariable {
    @JSONField(name = "id")
    private final String id;
    @JSONField(name = "name")
    private final String name;
    @JSONField(name = "value")
    private final String value;
    @JSONField(name = "value_type")
    private final String valueType;
    @JSONField(name = "description")
    private final String description;
    @JSONField(name = "selector")
    private final List<String> selector;

    public DifyConversationVariable(String id, String name, String value, String valueType,
                                    String description, List<String> selector) {
        this.id = id;
        this.name = name;
        this.value = value != null ? value : "";
        this.valueType = valueType != null ? valueType : "string";
        this.description = description;
        this.selector = selector;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String value() { return value; }
    public String valueType() { return valueType; }
    public String description() { return description; }
    public List<String> selector() { return selector; }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getValue() { return value; }
    public String getValueType() { return valueType; }
    public String getDescription() { return description; }
    public List<String> getSelector() { return selector; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyConversationVariable)) return false;
        DifyConversationVariable that = (DifyConversationVariable) o;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(value, that.value)
                && Objects.equals(valueType, that.valueType)
                && Objects.equals(description, that.description)
                && Objects.equals(selector, that.selector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, value, valueType, description, selector);
    }

    @Override
    public String toString() {
        return String.format("DifyConversationVariable[id=%s, name=%s, value=%s, valueType=%s, description=%s, selector=%s]",
                id, name, value, valueType, description, selector);
    }
}
