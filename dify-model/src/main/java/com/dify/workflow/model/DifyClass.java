package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Objects;

/**
 * Dify question classifier class definition.
 */
public final class DifyClass {
    @JSONField(name = "id")
    private final String id;
    @JSONField(name = "name")
    private final String name;
    @JSONField(name = "label")
    private final String label;

    public DifyClass(String id, String name, String label) {
        this.id = id;
        this.name = name;
        this.label = label;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String label() { return label; }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getLabel() { return label; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyClass)) return false;
        DifyClass that = (DifyClass) o;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, label);
    }

    @Override
    public String toString() {
        return String.format("DifyClass[id=%s, name=%s, label=%s]", id, name, label);
    }
}
