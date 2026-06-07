package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Map;
import java.util.Objects;

/**
 * Dify model configuration for LLM node.
 */
public final class DifyModel {
    @JSONField(name = "provider")
    private final String provider;
    @JSONField(name = "name")
    private final String name;
    @JSONField(name = "mode")
    private final String mode;
    @JSONField(name = "completion_type")
    private final String completionType;
    @JSONField(name = "prompt_cache")
    private final DifyPromptCacheConfig promptCache;
    @JSONField(name = "completion_params")
    private final Map<String, Object> completionParams;

    public DifyModel(String provider, String name, String mode, String completionType,
                     DifyPromptCacheConfig promptCache, Map<String, Object> completionParams) {
        this.provider = provider;
        this.name = name;
        this.mode = mode;
        this.completionType = completionType;
        this.promptCache = promptCache;
        this.completionParams = completionParams;
    }

    public String provider() { return provider; }
    public String name() { return name; }
    public String mode() { return mode; }
    public String completionType() { return completionType; }
    public DifyPromptCacheConfig promptCache() { return promptCache; }
    public Map<String, Object> completionParams() { return completionParams; }

    public String getProvider() { return provider; }
    public String getName() { return name; }
    public String getMode() { return mode; }
    public String getCompletionType() { return completionType; }
    public DifyPromptCacheConfig getPromptCache() { return promptCache; }
    public Map<String, Object> getCompletionParams() { return completionParams; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyModel)) return false;
        DifyModel that = (DifyModel) o;
        return Objects.equals(provider, that.provider)
                && Objects.equals(name, that.name)
                && Objects.equals(mode, that.mode)
                && Objects.equals(completionType, that.completionType)
                && Objects.equals(promptCache, that.promptCache)
                && Objects.equals(completionParams, that.completionParams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, name, mode, completionType, promptCache, completionParams);
    }

    @Override
    public String toString() {
        return String.format("DifyModel[provider=%s, name=%s, mode=%s, completionType=%s, promptCache=%s, completionParams=%s]",
                provider, name, mode, completionType, promptCache, completionParams);
    }
}
