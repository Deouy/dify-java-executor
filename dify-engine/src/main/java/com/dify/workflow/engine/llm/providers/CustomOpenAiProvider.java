package com.dify.workflow.engine.llm.providers;

import com.dify.workflow.engine.llm.LlmProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom OpenAI-compatible Provider 实现。
 * 允许用户配置任何遵循 OpenAI Chat Completions 格式的 API。
 */
public class CustomOpenAiProvider extends AbstractLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(CustomOpenAiProvider.class);

    public CustomOpenAiProvider(LlmProviderConfig config) {
        super(config);
    }

    /**
     * 返回自定义 API URL。
     * 使用配置的 baseUrl + /chat/completions。
     */
    @Override
    protected String getApiUrl() {
        return config.baseUrl() + "/chat/completions";
    }
}
