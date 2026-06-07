package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Objects;

/**
 * Dify retry configuration for LLM node.
 */
public final class DifyRetryConfig {
    @JSONField(name = "enabled")
    private final Boolean enabled;
    @JSONField(name = "max_retries")
    private final Integer maxRetries;
    @JSONField(name = "retry_interval")
    private final Integer retryInterval;
    @JSONField(name = "exponential_backoff")
    private final DifyExponentialBackoffConfig exponentialBackoff;

    public DifyRetryConfig(Boolean enabled, Integer maxRetries, Integer retryInterval,
                           DifyExponentialBackoffConfig exponentialBackoff) {
        this.enabled = enabled != null ? enabled : false;
        this.maxRetries = maxRetries != null ? maxRetries : 1;
        this.retryInterval = retryInterval != null ? retryInterval : 1000;
        this.exponentialBackoff = exponentialBackoff;
    }

    public Boolean enabled() { return enabled; }
    public Integer maxRetries() { return maxRetries; }
    public Integer retryInterval() { return retryInterval; }
    public DifyExponentialBackoffConfig exponentialBackoff() { return exponentialBackoff; }

    public Boolean getEnabled() { return enabled; }
    public Integer getMaxRetries() { return maxRetries; }
    public Integer getRetryInterval() { return retryInterval; }
    public DifyExponentialBackoffConfig getExponentialBackoff() { return exponentialBackoff; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyRetryConfig)) return false;
        DifyRetryConfig that = (DifyRetryConfig) o;
        return Objects.equals(enabled, that.enabled)
                && Objects.equals(maxRetries, that.maxRetries)
                && Objects.equals(retryInterval, that.retryInterval)
                && Objects.equals(exponentialBackoff, that.exponentialBackoff);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, maxRetries, retryInterval, exponentialBackoff);
    }

    @Override
    public String toString() {
        return String.format("DifyRetryConfig[enabled=%s, maxRetries=%s, retryInterval=%s, exponentialBackoff=%s]",
                enabled, maxRetries, retryInterval, exponentialBackoff);
    }

    /**
     * Exponential backoff configuration.
     */
    public static final class DifyExponentialBackoffConfig {
        @JSONField(name = "enabled")
        private final Boolean enabled;
        @JSONField(name = "multiplier")
        private final Integer multiplier;
        @JSONField(name = "max_interval")
        private final Integer maxInterval;

        public DifyExponentialBackoffConfig(Boolean enabled, Integer multiplier, Integer maxInterval) {
            this.enabled = enabled;
            this.multiplier = multiplier;
            this.maxInterval = maxInterval;
        }

        public Boolean enabled() { return enabled; }
        public Integer multiplier() { return multiplier; }
        public Integer maxInterval() { return maxInterval; }

        public Boolean getEnabled() { return enabled; }
        public Integer getMultiplier() { return multiplier; }
        public Integer getMaxInterval() { return maxInterval; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DifyExponentialBackoffConfig)) return false;
            DifyExponentialBackoffConfig that = (DifyExponentialBackoffConfig) o;
            return Objects.equals(enabled, that.enabled)
                    && Objects.equals(multiplier, that.multiplier)
                    && Objects.equals(maxInterval, that.maxInterval);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabled, multiplier, maxInterval);
        }

        @Override
        public String toString() {
            return String.format("DifyExponentialBackoffConfig[enabled=%s, multiplier=%s, maxInterval=%s]",
                    enabled, multiplier, maxInterval);
        }
    }
}
