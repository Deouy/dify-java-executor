package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Objects;

/**
 * Dify provider ID for tool node.
 */
public final class DifyProviderId {
    @JSONField(name = "provider")
    private final String provider;
    @JSONField(name = "label")
    private final String label;

    public DifyProviderId(String provider, String label) {
        this.provider = provider;
        this.label = label;
    }

    public String provider() { return provider; }
    public String label() { return label; }

    public String getProvider() { return provider; }
    public String getLabel() { return label; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyProviderId)) return false;
        DifyProviderId that = (DifyProviderId) o;
        return Objects.equals(provider, that.provider)
                && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, label);
    }

    @Override
    public String toString() {
        return String.format("DifyProviderId[provider=%s, label=%s]", provider, label);
    }
}
