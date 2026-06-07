package com.dify.workflow.engine.llm;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * LLM provider configuration with default parameters.
 * These defaults can be overridden by individual node configurations.
 */
public final class LlmProviderConfig {
    private final String provider;
    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final Integer maxTokens;
    private final Double temperature;
    private final Double topP;
    private final Integer timeout;
    private final Boolean supportsVision;
    /**
     * 关键修复(S18):该 provider 支持的 model 名白名单。
     * 用于 LLM 节点发请求前校验 YAML model ∈ 白名单,防"假通过"(DeepSeek 对未知 model 静默 fallback)。
     * 留空或 null = 不校验(向后兼容)。
     * 注册时 LlmProviderFactory 会强制 defaultModel ∈ 此集合。
     */
    private final Set<String> supportedModels;

    public LlmProviderConfig(String provider, String baseUrl, String apiKey, String defaultModel,
                            Integer maxTokens, Double temperature, Double topP, Integer timeout,
                            Boolean supportsVision) {
        this(provider, baseUrl, apiKey, defaultModel, maxTokens, temperature, topP, timeout,
                supportsVision, null);
    }

    public LlmProviderConfig(String provider, String baseUrl, String apiKey, String defaultModel,
                            Integer maxTokens, Double temperature, Double topP, Integer timeout,
                            Boolean supportsVision, Set<String> supportedModels) {
        // S19.1 不变式:若 supportedModels 显式声明了白名单,defaultModel 必须 ∈ 此集合。
        // Builder.defaultModel() 已隐式 add defaultModel,所以 Builder 路径永远满足。
        // 但 10 参构造器是唯一可绕过的入口(支持直接传 Set),需要在此 fail-fast。
        if (supportedModels != null && !supportedModels.isEmpty()
                && defaultModel != null && !defaultModel.isEmpty()
                && !supportedModels.contains(defaultModel)) {
            throw new IllegalArgumentException(
                    "defaultModel '" + defaultModel + "' not in supportedModels: "
                            + supportedModels + " (provider='" + provider + "')");
        }
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.timeout = timeout;
        this.supportsVision = supportsVision;
        this.supportedModels = supportedModels != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(supportedModels))
                : Collections.emptySet();
    }

    public String provider() { return provider; }
    public String baseUrl() { return baseUrl; }
    public String apiKey() { return apiKey; }
    public String defaultModel() { return defaultModel; }
    public Integer maxTokens() { return maxTokens; }
    public Double temperature() { return temperature; }
    public Double topP() { return topP; }
    public Integer timeout() { return timeout; }
    public Boolean supportsVision() { return supportsVision; }
    public Set<String> supportedModels() { return supportedModels; }

    public String getProvider() { return provider; }
    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    public String getDefaultModel() { return defaultModel; }
    public Integer getMaxTokens() { return maxTokens; }
    public Double getTemperature() { return temperature; }
    public Double getTopP() { return topP; }
    public Integer getTimeout() { return timeout; }
    public Boolean getSupportsVision() { return supportsVision; }
    public Set<String> getSupportedModels() { return supportedModels; }

    public static Builder builder() { return new Builder(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LlmProviderConfig)) return false;
        LlmProviderConfig that = (LlmProviderConfig) o;
        return Objects.equals(provider, that.provider)
                && Objects.equals(baseUrl, that.baseUrl)
                && Objects.equals(apiKey, that.apiKey)
                && Objects.equals(defaultModel, that.defaultModel)
                && Objects.equals(maxTokens, that.maxTokens)
                && Objects.equals(temperature, that.temperature)
                && Objects.equals(topP, that.topP)
                && Objects.equals(timeout, that.timeout)
                && Objects.equals(supportsVision, that.supportsVision)
                && Objects.equals(supportedModels, that.supportedModels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, baseUrl, apiKey, defaultModel, maxTokens, temperature, topP, timeout, supportsVision, supportedModels);
    }

    @Override
    public String toString() {
        return String.format("LlmProviderConfig[provider=%s, defaultModel=%s, supportedModels=%s]",
                provider, defaultModel, supportedModels);
    }

    public static final class Builder {
        private String provider;
        private String baseUrl;
        private String apiKey;
        private String defaultModel;
        private Integer maxTokens = 4096;
        private Double temperature = 0.7;
        private Double topP;
        private Integer timeout = 60000;
        private Boolean supportsVision = false;
        /** 关键修复(S18):model 白名单构建器,先累加 defaultModel 再累加 supportedModels(去重) */
        private final Set<String> supportedModels = new LinkedHashSet<>();

        public Builder provider(String provider) { this.provider = provider; return this; }
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            // 关键:把 defaultModel 隐式加进白名单,避免 Builder 写两遍
            if (defaultModel != null && !defaultModel.isEmpty()) {
                this.supportedModels.add(defaultModel);
            }
            return this;
        }
        public Builder maxTokens(Integer maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder temperature(Double temperature) { this.temperature = temperature; return this; }
        public Builder topP(Double topP) { this.topP = topP; return this; }
        public Builder timeout(Integer timeout) { this.timeout = timeout; return this; }
        public Builder supportsVision(Boolean supportsVision) { this.supportsVision = supportsVision; return this; }

        /** 关键修复(S18):批量加 supportedModels(String...) */
        public Builder supportedModels(String... models) {
            if (models != null) {
                this.supportedModels.addAll(Arrays.asList(models));
            }
            return this;
        }

        /** 关键修复(S18):批量加 supportedModels(Set<String>) */
        public Builder supportedModels(Set<String> models) {
            if (models != null) {
                this.supportedModels.addAll(models);
            }
            return this;
        }

        public LlmProviderConfig build() {
            // S19.1 修复:Builder 路径下 defaultModel 已被隐式 add 进 supportedModels
            //   (见 defaultModel() 方法),所以 Builder 永远满足不变式。
            //   真正的不变式检查在 LlmProviderConfig 10 参构造器(唯一可绕过 Builder 的入口)。
            return new LlmProviderConfig(provider, baseUrl, apiKey, defaultModel,
                    maxTokens, temperature, topP, timeout, supportsVision,
                    supportedModels);
        }
    }
}
