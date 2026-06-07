package com.dify.workflow.engine.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LlmProviderFactory 单元测试。
 * 重点覆盖 S19 修复:getSupportedModels() 的 auto-fallback 行为。
 */
class LlmProviderFactoryTest {

    private LlmProviderFactory.Builder newBuilder() {
        return LlmProviderFactory.builder();
    }

    @Nested
    @DisplayName("getSupportedModels auto-fallback 行为(S19)")
    class GetSupportedModelsFallback {

        @Test
        @DisplayName("仅配 defaultModel:自动回退到 {defaultModel}")
        void defaultModelImplicitFallback() {
            // Builder 的 defaultModel() 会把 defaultModel 隐式加入 supportedModels
            LlmProviderFactory factory = newBuilder()
                    .provider("deepseek", LlmProviderConfig.builder()
                            .provider("deepseek")
                            .baseUrl("https://api.deepseek.com/v1")
                            .apiKey("sk-test")
                            .defaultModel("deepseek-chat")
                            .build())
                    .build();

            Set<String> supported = factory.getSupportedModels("deepseek");
            assertEquals(Collections.singleton("deepseek-chat"), supported,
                    "仅配 defaultModel 时,白名单应回退到 {defaultModel}");
        }

        @Test
        @DisplayName("显式多 model:返回完整 supportedModels 集合")
        void explicitMultipleModels() {
            LlmProviderFactory factory = newBuilder()
                    .provider("deepseek", LlmProviderConfig.builder()
                            .provider("deepseek")
                            .baseUrl("https://api.deepseek.com/v1")
                            .apiKey("sk-test")
                            .defaultModel("deepseek-chat")
                            .supportedModels("deepseek-chat", "deepseek-v3", "deepseek-v4-pro", "deepseek-v4-flash")
                            .build())
                    .build();

            Set<String> supported = factory.getSupportedModels("deepseek");
            assertEquals(4, supported.size());
            assertTrue(supported.contains("deepseek-chat"));
            assertTrue(supported.contains("deepseek-v3"));
            assertTrue(supported.contains("deepseek-v4-pro"));
            assertTrue(supported.contains("deepseek-v4-flash"));
        }

        @Test
        @DisplayName("直接走 10 参构造 + supportedModels 空 + defaultModel 非空:工厂层 fallback")
        void directConstructorWithDefaultModel() {
            // 绕过 Builder,直接用 10 参构造 — supportedModels 是 Collections.emptySet()
            LlmProviderConfig config = new LlmProviderConfig(
                    "deepseek",
                    "https://api.deepseek.com/v1",
                    "sk-test",
                    "deepseek-v3",
                    4096, 0.7, null, 60000, false,
                    Collections.<String>emptySet());
            LlmProviderFactory factory = newBuilder()
                    .provider("deepseek", config)
                    .build();

            // S19:即使 supportedModels 直接为空,只要 defaultModel 非空,工厂层也要回退
            Set<String> supported = factory.getSupportedModels("deepseek");
            assertEquals(Collections.singleton("deepseek-v3"), supported,
                    "10 参构造 supportedModels 为空 + defaultModel 非空,应回退到 {defaultModel}");
        }

        @Test
        @DisplayName("两者都为空:返回 emptySet(由 LlmNode 抛 'provider 未配置 model')")
        void bothEmpty() {
            LlmProviderConfig config = new LlmProviderConfig(
                    "broken",
                    null, null, null,
                    4096, 0.7, null, 60000, false,
                    null);
            LlmProviderFactory factory = newBuilder()
                    .provider("broken", config)
                    .build();

            Set<String> supported = factory.getSupportedModels("broken");
            assertTrue(supported.isEmpty(),
                    "defaultModel 为 null 且 supportedModels 为空时,应返回 emptySet");
        }

        @Test
        @DisplayName("未注册的 provider:返回 emptySet(不抛异常)")
        void unregisteredProvider() {
            LlmProviderFactory factory = newBuilder().build();
            Set<String> supported = factory.getSupportedModels("does-not-exist");
            assertTrue(supported.isEmpty());
        }
    }

    @Nested
    @DisplayName("marketplace ID 解析")
    class MarketplaceIdResolution {

        @Test
        @DisplayName("langgenius/deepseek/deepseek → 解析到 deepseek provider")
        void marketplaceIdResolvesToShortName() {
            LlmProviderFactory factory = newBuilder()
                    .provider("deepseek", LlmProviderConfig.builder()
                            .provider("deepseek")
                            .baseUrl("https://api.deepseek.com/v1")
                            .apiKey("sk-test")
                            .defaultModel("deepseek-chat")
                            .supportedModels("deepseek-chat", "deepseek-v4-pro")
                            .build())
                    .build();

            // marketplace 长格式:取最后一段 "deepseek" 命中
            Set<String> supported = factory.getSupportedModels("langgenius/deepseek/deepseek");
            assertEquals(2, supported.size());
            assertTrue(supported.contains("deepseek-chat"));
            assertTrue(supported.contains("deepseek-v4-pro"));
        }

        @Test
        @DisplayName("短格式:直接命中")
        void shortNameDirectMatch() {
            LlmProviderFactory factory = newBuilder()
                    .provider("openai", LlmProviderConfig.builder()
                            .provider("openai")
                            .baseUrl("https://api.openai.com/v1")
                            .apiKey("sk-test")
                            .defaultModel("gpt-4o")
                            .supportedModels("gpt-4o", "gpt-4o-mini")
                            .build())
                    .build();

            Set<String> supported = factory.getSupportedModels("openai");
            assertEquals(2, supported.size());
        }
    }

    @Nested
    @DisplayName("S19.1 不变式:LlmProviderConfig 10 参构造器 fail-fast")
    class InvariantValidation {

        @Test
        @DisplayName("defaultModel ∉ supportedModels 时 10 参构造器抛 IllegalArgumentException")
        void constructorRejectsMismatchedDefaultModel() {
            // S19.1:fail-fast 应在 LlmProviderConfig 构造器(10 参路径是唯一可绕过 Builder 的入口)
            Set<String> whitelist = new java.util.LinkedHashSet<>();
            whitelist.add("deepseek-v4-pro");
            whitelist.add("deepseek-v3");
            // 故意没把 defaultModel 加进 supportedModels
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new LlmProviderConfig(
                            "deepseek",
                            "https://api.deepseek.com/v1",
                            "sk-test",
                            "deepseek-chat",  // defaultModel
                            4096, 0.7, null, 60000, false,
                            whitelist));
            assertTrue(ex.getMessage().contains("deepseek-chat"),
                    "错误消息应包含 defaultModel 名,实际: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("deepseek-v4-pro"),
                    "错误消息应包含 supportedModels 列表,实际: " + ex.getMessage());
        }

        @Test
        @DisplayName("defaultModel 已在 supportedModels 中(隐式或显式):构造成功")
        void constructorAcceptsConsistentConfig() {
            // 隐式:Builder.defaultModel() 自动加入 supportedModels,9 参构造器经 Builder 创建的 config 一定一致
            assertDoesNotThrow(() -> LlmProviderConfig.builder()
                    .provider("deepseek")
                    .defaultModel("deepseek-chat")
                    .build());
            // 显式:defaultModel 与 supportedModels 一致
            Set<String> whitelist = new java.util.LinkedHashSet<>();
            whitelist.add("deepseek-chat");
            whitelist.add("deepseek-v4-pro");
            assertDoesNotThrow(() -> new LlmProviderConfig(
                    "deepseek", null, null, "deepseek-chat",
                    4096, 0.7, null, 60000, false,
                    whitelist));
        }
    }
}
