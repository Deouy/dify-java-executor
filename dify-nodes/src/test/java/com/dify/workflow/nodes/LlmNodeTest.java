package com.dify.workflow.nodes;

import com.dify.workflow.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * LlmNode 单元测试。
 * 测试 LLM 节点的大模型调用逻辑。
 */
@ExtendWith(MockitoExtension.class)
class LlmNodeTest {

    @Mock
    private NodeExecutionContext context;

    @Mock
    private LlmService llmService;

    private DifyNodeData createNodeData(DifyModel model, List<DifyPromptMessage> promptTemplate) {
        return DifyNodeData.builder()
            .type("llm")
            .title("大模型")
            .model(model)
            .promptTemplate(promptTemplate)
            .build();
    }

    @Nested
    @DisplayName("LLM 节点配置测试")
    class ConfigurationTest {

        @Test
        @DisplayName("没有 model 配置时抛出异常")
        void testNoModel_throwsException() {
            DifyNodeData data = createNodeData(null, null);
            LlmNode node = new LlmNode("llm_node", data);

            when(context.getLlmService()).thenReturn(llmService);

            assertThrows(Exception.class, () -> node.execute(context));
        }

        @Test
        @DisplayName("正确配置 model 和 prompt")
        void testCorrectConfiguration() {
            DifyModel model = new DifyModel("openai", "gpt-4o", "chat", null, null, null);
            List<DifyPromptMessage> promptTemplate = new ArrayList<>();
            promptTemplate.add(new DifyPromptMessage("system", "You are a helpful assistant.", null));
            promptTemplate.add(new DifyPromptMessage("user", "{{#start_node.query#}}", null));

            DifyNodeData data = createNodeData(model, promptTemplate);
            LlmNode node = new LlmNode("llm_node", data);

            assertEquals("llm", node.getType());
        }
    }

    @Nested
    @DisplayName("LLM 调用测试")
    class LlmCallTest {

        @Test
        @DisplayName("成功调用时设置 text 和 _raw_result 变量")
        void testSuccessfulCall() throws Exception {
            DifyModel model = new DifyModel("openai", "gpt-4o", "chat", null, null, null);
            List<DifyPromptMessage> promptTemplate = new ArrayList<>();
            promptTemplate.add(new DifyPromptMessage("user", "Hello", null));

            DifyNodeData data = createNodeData(model, promptTemplate);
            LlmNode node = new LlmNode("llm_node", data);

            LlmCallResult successResult = LlmCallResult.success("Hello, how can I help you?");

            when(context.getLlmService()).thenReturn(llmService);
            // S19:provider 白名单必须包含 YAML model,否则抛 IllegalArgumentException
            when(llmService.getSupportedModels("openai")).thenReturn(Collections.singleton("gpt-4o"));
            when(llmService.call(eq("openai"), eq("gpt-4o"), any(ChatRequest.class)))
                .thenReturn(successResult);

            node.execute(context);

            verify(context).setVariable("llm_node", "text", "Hello, how can I help you.");
            verify(context).setVariable("llm_node", "_raw_result", successResult);
        }

        @Test
        @DisplayName("LLM 调用失败时抛出异常")
        void testFailedCall() {
            DifyModel model = new DifyModel("openai", "gpt-4o", "chat", null, null, null);
            List<DifyPromptMessage> promptTemplate = new ArrayList<>();
            promptTemplate.add(new DifyPromptMessage("user", "Hello", null));

            DifyNodeData data = createNodeData(model, promptTemplate);
            LlmNode node = new LlmNode("llm_node", data);

            LlmCallResult failedResult = LlmCallResult.failure("API error");

            when(context.getLlmService()).thenReturn(llmService);
            // S19:同上
            when(llmService.getSupportedModels("openai")).thenReturn(Collections.singleton("gpt-4o"));
            when(llmService.call(eq("openai"), eq("gpt-4o"), any(ChatRequest.class)))
                .thenReturn(failedResult);

            assertThrows(RuntimeException.class, () -> node.execute(context));
        }

        @Test
        @DisplayName("没有 LLM 服务时抛出异常")
        void testNoLlmService_throwsException() {
            DifyModel model = new DifyModel("openai", "gpt-4o", "chat", null, null, null);
            DifyNodeData data = createNodeData(model, null);
            LlmNode node = new LlmNode("llm_node", data);

            when(context.getLlmService()).thenReturn(null);

            assertThrows(IllegalStateException.class, () -> node.execute(context));
        }
    }

    @Nested
    @DisplayName("变量解析测试")
    class VariableResolutionTest {

        @Test
        @DisplayName("Prompt 模板中的变量会被解析")
        void testPromptVariableResolution() throws Exception {
            DifyModel model = new DifyModel("openai", "gpt-4o", "chat", null, null, null);
            List<DifyPromptMessage> promptTemplate = new ArrayList<>();
            promptTemplate.add(new DifyPromptMessage("user", "{{#start_node.query#}}", null));

            DifyNodeData data = createNodeData(model, promptTemplate);
            LlmNode node = new LlmNode("llm_node", data);

            LlmCallResult successResult = LlmCallResult.success("Response");

            when(context.getLlmService()).thenReturn(llmService);
            when(context.resolveVariables("{{#start_node.query#}}")).thenReturn("What is the weather?");
            // S19:同上
            when(llmService.getSupportedModels("openai")).thenReturn(Collections.singleton("gpt-4o"));
            when(llmService.call(eq("openai"), eq("gpt-4o"), any(ChatRequest.class)))
                .thenReturn(successResult);

            node.execute(context);

            // Verify variable resolution was called
            verify(context, atLeastOnce()).resolveVariables(anyString());
        }
    }

    @Nested
    @DisplayName("S19 强制校验:YAML model ↔ provider 白名单")
    class StrictValidationTest {

        @Test
        @DisplayName("YAML model 不在 provider 白名单时抛 IllegalArgumentException(消息含 model name)")
        void modelNotInWhitelistThrows() {
            DifyModel model = new DifyModel("openai", "gpt-4o", "chat", null, null, null);
            List<DifyPromptMessage> promptTemplate = new ArrayList<>();
            promptTemplate.add(new DifyPromptMessage("user", "Hello", null));

            DifyNodeData data = createNodeData(model, promptTemplate);
            LlmNode node = new LlmNode("llm_node", data);

            when(context.getLlmService()).thenReturn(llmService);
            // provider 白名单里只有 claude-3,没有 gpt-4o
            when(llmService.getSupportedModels("openai")).thenReturn(Collections.singleton("claude-3"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> node.execute(context));
            assertTrue(ex.getMessage().contains("gpt-4o"),
                    "错误消息应包含 YAML model 名,实际: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("openai"),
                    "错误消息应包含 provider 名,实际: " + ex.getMessage());
        }

        @Test
        @DisplayName("provider 完全没配 model(白名单空)时抛 IllegalArgumentException(消息说明未配置)")
        void providerWithoutModelConfigThrows() {
            DifyModel model = new DifyModel("openai", "gpt-4o", "chat", null, null, null);
            List<DifyPromptMessage> promptTemplate = new ArrayList<>();
            promptTemplate.add(new DifyPromptMessage("user", "Hello", null));

            DifyNodeData data = createNodeData(model, promptTemplate);
            LlmNode node = new LlmNode("llm_node", data);

            when(context.getLlmService()).thenReturn(llmService);
            // provider 白名单空(没配 defaultModel 也没配 supportedModels)
            when(llmService.getSupportedModels("openai")).thenReturn(Collections.<String>emptySet());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> node.execute(context));
            assertTrue(ex.getMessage().contains("openai"),
                    "错误消息应包含 provider 名,实际: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("no model whitelist")
                            || ex.getMessage().contains("has no model"),
                    "错误消息应说明 provider 未配置 model,实际: " + ex.getMessage());
        }

        @Test
        @DisplayName("S19.3:no whitelist 错误消息含配置指引(set supportedModels or defaultModel)")
        void noWhitelistErrorMessageIncludesGuidance() {
            DifyModel model = new DifyModel("openai", "gpt-4o", "chat", null, null, null);
            List<DifyPromptMessage> promptTemplate = new ArrayList<>();
            promptTemplate.add(new DifyPromptMessage("user", "Hello", null));

            DifyNodeData data = createNodeData(model, promptTemplate);
            LlmNode node = new LlmNode("llm_node", data);

            when(context.getLlmService()).thenReturn(llmService);
            when(llmService.getSupportedModels("openai")).thenReturn(Collections.<String>emptySet());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> node.execute(context));
            // S19.3 关键:错误消息应给用户具体修复指引,而不是泛泛说"未配置"
            String msg = ex.getMessage();
            assertTrue(msg.contains("supportedModels") || msg.contains("defaultModel"),
                    "错误消息应提示设置 supportedModels 或 defaultModel,实际: " + msg);
        }
    }
}
