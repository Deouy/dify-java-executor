package com.dify.workflow.engine.llm.providers;

import com.dify.workflow.engine.llm.LlmProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI Provider 实现。
 * 使用 OpenAI 官方 API 格式。
 */
public class OpenAiProvider extends AbstractLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);

    public OpenAiProvider(LlmProviderConfig config) {
        super(config);
    }

    /**
     * 返回 OpenAI Chat Completions API URL。
     * 使用配置的 baseUrl + /chat/completions。
     */
    @Override
    protected String getApiUrl() {
        return config.baseUrl() + "/chat/completions";
    }
}
