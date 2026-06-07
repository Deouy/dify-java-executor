package com.dify.workflow.engine.llm.providers;

import com.dify.workflow.engine.llm.LlmProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DeepSeek Provider 实现。
 * 使用 OpenAI 兼容 API 格式。
 */
public class DeepSeekProvider extends AbstractLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekProvider.class);

    public DeepSeekProvider(LlmProviderConfig config) {
        super(config);
    }

    /**
     * 返回 DeepSeek Chat Completions API URL。
     * 使用配置的 baseUrl + /chat/completions。
     */
    @Override
    protected String getApiUrl() {
        return config.baseUrl() + "/chat/completions";
    }
}
