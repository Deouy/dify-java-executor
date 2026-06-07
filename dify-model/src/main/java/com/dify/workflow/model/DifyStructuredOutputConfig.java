package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Map;
import java.util.Objects;

/**
 * Dify structured output configuration for LLM node.
 *
 * YAML 形态：
 *   structured_output:
 *     schema: { ... }
 *   structured_output_enabled: true
 */
public final class DifyStructuredOutputConfig {
    @JSONField(name = "enabled")
    private final Boolean enabled;
    /** JSON Schema 对象（YAML 中 structured_output.schema 的内容） */
    @JSONField(name = "schema")
    private final Map<String, Object> schema;

    public DifyStructuredOutputConfig(Boolean enabled) {
        this(enabled, null);
    }

    public DifyStructuredOutputConfig(Boolean enabled, Map<String, Object> schema) {
        this.enabled = enabled != null ? enabled : false;
        this.schema = schema;
    }

    public Boolean enabled() { return enabled; }
    public Boolean getEnabled() { return enabled; }
    public Map<String, Object> schema() { return schema; }
    public Map<String, Object> getSchema() { return schema; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyStructuredOutputConfig)) return false;
        DifyStructuredOutputConfig that = (DifyStructuredOutputConfig) o;
        return Objects.equals(enabled, that.enabled) && Objects.equals(schema, that.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, schema);
    }

    @Override
    public String toString() {
        return String.format("DifyStructuredOutputConfig[enabled=%s, schema=%s]", enabled, schema);
    }
}
