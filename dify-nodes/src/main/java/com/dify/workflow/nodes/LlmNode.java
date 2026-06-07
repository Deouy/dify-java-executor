package com.dify.workflow.nodes;

import com.dify.workflow.model.ChatRequest;
import com.dify.workflow.model.ConversationHistoryStore;
import com.dify.workflow.model.DifyMemoryConfig;
import com.dify.workflow.model.DifyModel;
import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.DifyPromptMessage;
import com.dify.workflow.model.DifyStructuredOutputConfig;
import com.dify.workflow.model.LlmCallResult;
import com.dify.workflow.model.LlmService;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LLM node - calls an LLM API to generate a response.
 */
public class LlmNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(LlmNode.class);

    public LlmNode(String id, DifyNodeData data) {
        super(id, NodeType.LLM, data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing LLM node: {}", id);

        DifyModel model = data.model();
        if (model == null) {
            throw new IllegalArgumentException("LLM node has no model configuration: " + id);
        }

        String provider = model.provider();
        String modelName = model.name();

        // Build chat request from prompt template
        // 关键修复:默认 stream=true,让 LlmNode 始终走 callStream 路径,
        //   每收到 SSE delta 通过 context.emitChunk 转发给 WorkflowEventListener.onChunk。
        //   同步累积结果不变(text 变量最终拿到完整 content),但流式事件现在每次 LLM 调用都触发。
        ChatRequest.Builder requestBuilder = ChatRequest.builder()
                .model(modelName)
                .stream(true);

        // 构建 prompt 消息列表
        List<ChatRequest.Message> messages = new ArrayList<>();
        List<DifyPromptMessage> promptTemplate = data.promptTemplate();
        if (promptTemplate != null) {
            for (DifyPromptMessage msg : promptTemplate) {
                String text = msg.text();
                if (text != null) {
                    text = resolveVariables(context, text);
                }
                messages.add(new ChatRequest.Message(msg.role(), text, null, null, null));
            }
        }

        // 结构化输出 prompt 注入（如果启用）
        // 对齐 Dify graphon LLMNode._handle_prompt_based_schema：
        // 把 schema 拼到 system prompt 末尾，告诉 LLM 按 JSON 输出
        // 关键：YAML 中 structured_output_enabled 是 LLM node 顶层字段（与 structured_output 兄弟）
        //   而 DifyNodeData.structuredOutput() 只对 enabled: true/false 形态做了类型化字段
        //   所以 enabled 需要从 data.structuredOutput().enabled() OR data.additionalProperties().get("structured_output_enabled") 两路读
        boolean soEnabled = false;
        Map<String, Object> additionalProps = data.additionalProperties();
        boolean typedEnabled = data.structuredOutput() != null
                && Boolean.TRUE.equals(data.structuredOutput().enabled());
        boolean rawEnabled = additionalProps != null
                && Boolean.TRUE.equals(additionalProps.get("structured_output_enabled"));
        if (typedEnabled || rawEnabled) {
            // schema 来源：DifyStructuredOutputConfig.schema（typed 字段）优先，
            //   fallback 到 additionalProperties.get("structured_output")（兼容旧配置）
            //   注意：data.structuredOutput().schema() 直接是 schema 对象（Map 形态），
            //   而 additionalProperties 中的 "structured_output" 可能是 {schema: {...}} 包装
            @SuppressWarnings("unchecked")
            Map<String, Object> soMapPre = (data.structuredOutput() != null && data.structuredOutput().schema() != null)
                    ? data.structuredOutput().schema()
                    : (additionalProps != null
                        ? (Map<String, Object>) additionalProps.get("structured_output") : null);
            // 兼容两种形态：1) soMapPre 本身就是 schema（含 properties/type 等字段）
            //              2) soMapPre 是包装 {schema: {...}}
            Map<String, Object> actualSchema = soMapPre;
            if (soMapPre != null && soMapPre.containsKey("schema") && soMapPre.get("schema") instanceof Map) {
                actualSchema = (Map<String, Object>) soMapPre.get("schema");
            }
            if (actualSchema != null && !actualSchema.isEmpty()) {
                soEnabled = true;
                String schemaJson = com.alibaba.fastjson2.JSON.toJSONString(actualSchema);
                String hint = "\n\n[结构化输出] 你必须严格按照以下 JSON Schema 输出结果。"
                        + "规则：1) 你的回复必须是合法 JSON 对象；2) 禁止任何解释、前后缀文字、Markdown 代码块包裹；"
                        + "3) 字段名和值类型必须与 schema 完全一致。\n"
                        + "Schema: " + schemaJson;
                // 追加到最后一条 system 消息
                boolean appended = false;
                for (int i = messages.size() - 1; i >= 0; i--) {
                    ChatRequest.Message m = messages.get(i);
                    if ("system".equals(m.role())) {
                        String newText = (m.content() == null ? "" : m.content()) + hint;
                        messages.set(i, new ChatRequest.Message("system", newText, null, null, null));
                        appended = true;
                        break;
                    }
                }
                // 没有 system 消息则插入到列表头部
                if (!appended) {
                    messages.add(0, new ChatRequest.Message("system", hint, null, null, null));
                }
                log.debug("LLM node {} injected structured_output schema hint into system prompt", id);
            }
        }

        // 关键修复:对齐 Dify Python 行为
        // 当 prompt_template 没有 user 消息时,自动用 memory.query_prompt_template 注入一条 user 消息
        // 默认模板 "{{#sys.query#}}\n\n{{#sys.files#}}",会把用户输入 query 解析为 user 消息
        // 不加这个 fallback 时,YAML 模板只有空 system 消息,LLM 收不到 user 输入,会瞎补全回答
        DifyMemoryConfig memoryConfig = data.memory();
        boolean hasUserMessage = messages.stream().anyMatch(m -> "user".equals(m.role()));
        if (!hasUserMessage) {
            String userTemplate = (memoryConfig != null && memoryConfig.queryPromptTemplate() != null)
                    ? memoryConfig.queryPromptTemplate()
                    : "{{#sys.query#}}\n\n{{#sys.files#}}";
            String userText = resolveVariables(context, userTemplate);
            messages.add(new ChatRequest.Message("user", userText, null, null, null));
            log.debug("LLM node injected fallback user message from memory.query_prompt_template");
        }

        // 注入聊天历史记录 (conversation memory)
        if (memoryConfig != null && memoryConfig.isWindowEnabled()) {
            messages = injectHistory(context, messages, memoryConfig);
        }

        requestBuilder.messages(messages);

        // 结构化输出：通过 extraParameters 传 response_format={"type":"json_object"}
        // OpenAI/DeepSeek 等支持该参数，会强制 LLM 输出合法 JSON
        // 用 Map 而非 String，让 AbstractLlmProvider 把 response_format 保留为 JSON object 嵌套
        if (soEnabled) {
            Map<String, Object> extra = new HashMap<>();
            Map<String, String> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_object");
            extra.put("response_format", responseFormat);
            requestBuilder.extraParameters(extra);
        }

        // Get LLM service and call the provider
        LlmService llmService = context.getLlmService();
        if (llmService == null) {
            throw new IllegalStateException("LLM service not configured");
        }

        // 关键修复(S18):发请求前校验 YAML model 是否在 provider 的 supportedModels 白名单中。
        //   防"假通过" — DeepSeek 对未知 model 静默 fallback 造成测试"通过"。
        // S19 修复:强制校验,移除 !supported.isEmpty() 短路。
        //   工厂层 getSupportedModels() 已对 defaultModel 做隐式回退,
        //   这里 supported 仍为空说明 provider 完全没配 model。
        java.util.Set<String> supported = llmService.getSupportedModels(provider);
        if (supported == null || supported.isEmpty()) {
            throw new IllegalArgumentException(
                    "LLM provider '" + provider + "' has no model whitelist configured"
                            + " (set supportedModels or defaultModel on registration)");
        }
        if (!supported.contains(modelName)) {
            throw new IllegalArgumentException(
                    "LLM model '" + modelName + "' not supported by provider '" + provider
                            + "'. Available: " + supported);
        }

        ChatRequest request = requestBuilder.build();

        // 图片 URL 验证: 如果包含图片但 provider 不支持 vision，报错
        if (hasImageContent(request) && !llmService.supportsVision(provider)) {
            throw new UnsupportedOperationException(
                "LLM provider '" + provider + "' does not support vision/image input."
                + " Please use a vision-capable model or remove image references.");
        }

        // 记录 LLM 请求详情到 processData
        Map<String, Object> pd = new HashMap<>();
        pd.put("provider", provider);
        pd.put("model", modelName);
        pd.put("mode", model.mode());
        if (model.completionParams() != null) {
            pd.put("temperature", model.completionParams().get("temperature"));
            pd.put("max_tokens", model.completionParams().get("max_tokens"));
        }
        List<Map<String, String>> msgList = new ArrayList<>();
        for (ChatRequest.Message msg : request.messages()) {
            Map<String, String> entry = new HashMap<>();
            entry.put("role", msg.role() != null ? msg.role() : "user");
            entry.put("content", msg.content() != null ? msg.content() : "");
            msgList.add(entry);
        }
        pd.put("messages", msgList);
        this.processData = pd;

        // 关键修复:根据 request.stream() 走同步或流式路径
        //   流式路径调 callStream,每收到一个 SSE token 通过 context.emitChunk 转发给 listener
        //   同步路径保持原行为不变(默认 request.stream()=false)
        LlmCallResult result;
        if (request.stream()) {
            // 流式:累积所有 delta,在 stream 结束后构造 LlmCallResult
            StringBuilder accumulated = new StringBuilder();
            StringBuilder toolCallsJson = new StringBuilder("[");
            int promptTokens = 0, completionTokens = 0, totalTokens = 0;
            String finishReason = "stop";
            try {
                llmService.callStream(provider, modelName, request, delta -> {
                    if (delta.content() != null) {
                        accumulated.append(delta.content());
                        // 关键:每个 token 透传给 WorkflowEventListener.onChunk
                        context.emitChunk(id, delta.content());
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException("LLM stream call failed: " + e.getMessage(), e);
            }
            // 流完成后构造 LlmCallResult(当前简化版,toolCalls/usage 暂不带)
            result = new LlmCallResult(accumulated.toString(), modelName, null, null, null,
                    finishReason, true, null, null, null);
        } else {
            // 同步路径(原行为)
            result = llmService.call(provider, modelName, request);
        }

        if (!result.isSuccess()) {
            throw new RuntimeException("LLM call failed: " + result.errorMessage());
        }

        // Store the response
        String content = result.content();
        context.setVariable(id, "text", content);
        context.setVariable(id, "_raw_result", result);

        // 结构化输出解析（如果 YAML 中 structured_output_enabled: true）
        // 对齐 Dify 原版：model_instance.invoke_llm_with_structured_output(...) 返回 structured_output 字段
        // 当前先做 JSON 解析（不传 json_schema 到 LLM service），下游节点可通过 {{#node.structured_output#}} 引用
        boolean typedEnabled2 = data.structuredOutput() != null
                && Boolean.TRUE.equals(data.structuredOutput().enabled());
        boolean rawEnabled2 = additionalProps != null
                && Boolean.TRUE.equals(additionalProps.get("structured_output_enabled"));
        if (typedEnabled2 || rawEnabled2) {
            @SuppressWarnings("unchecked")
            Map<String, Object> soMap = (data.structuredOutput() != null && data.structuredOutput().schema() != null)
                    ? data.structuredOutput().schema()
                    : (additionalProps != null
                        ? (Map<String, Object>) additionalProps.get("structured_output") : null);
            Map<String, Object> actualSchema = soMap;
            if (soMap != null && soMap.containsKey("schema") && soMap.get("schema") instanceof Map) {
                actualSchema = (Map<String, Object>) soMap.get("schema");
            }
            if (actualSchema != null && !actualSchema.isEmpty()) {
                Object parsed = tryParseStructuredOutput(content);
                if (parsed != null) {
                    context.setVariable(id, "structured_output", parsed);
                    log.debug("LLM node {} parsed structured_output: {}", id, parsed);
                }
            }
        }

        log.debug("LLM node output: {}", content);
    }

    /**
     * 尝试将 LLM 文本输出解析为结构化 JSON 对象。
     * 多种策略依次尝试：1) 整体 parse；2) 剥 markdown 包裹；3) 剥推理模型前缀；4) 提取首个 {...} 区间。
     * 解析失败返回 null（不抛异常，调用方按需降级到 text 输出）。
     */
    private Object tryParseStructuredOutput(String content) {
        if (content == null || content.isEmpty()) return null;
        // 策略 1: 整体 parse
        Object parsed = tryJsonParse(content.trim());
        if (parsed instanceof Map) return parsed;
        // 策略 2: 剥 markdown 代码块 ```json ... ```
        String stripped = content.trim();
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```\\s*$", "").trim();
            parsed = tryJsonParse(stripped);
            if (parsed instanceof Map) return parsed;
        }
        // 策略 3: 剥推理模型 <think>...</think> 前缀
        stripped = stripped.replaceAll("(?s)^\\s*<think>.*?</think>\\s*", "").trim();
        parsed = tryJsonParse(stripped);
        if (parsed instanceof Map) return parsed;
        // 策略 4: 提取首个 { 到最后一个 } 之间的内容（容错处理 LLM 在 JSON 前后加了解释文字）
        int firstBrace = stripped.indexOf('{');
        int lastBrace = stripped.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            String jsonSub = stripped.substring(firstBrace, lastBrace + 1);
            parsed = tryJsonParse(jsonSub);
            if (parsed instanceof Map) return parsed;
        }
        return null;
    }

    /** 包装 JSON.parse，吞掉异常返回 null */
    private Object tryJsonParse(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return com.alibaba.fastjson2.JSON.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 注入聊天历史记录到 prompt */
    @SuppressWarnings("unchecked")
    private List<ChatRequest.Message> injectHistory(NodeExecutionContext context,
                List<ChatRequest.Message> messages, DifyMemoryConfig memoryConfig) {
        int windowSize = memoryConfig.windowSize();
        List<ChatRequest.Message> history = null;

        // 模式1: chatHistory 参数（优先）
        Object chatHistoryObj = findVariableInPool(context, "chatHistory");
        if (chatHistoryObj instanceof List) {
            List<?> raw = (List<?>) chatHistoryObj;
            history = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof ChatRequest.Message) {
                    ChatRequest.Message msg = (ChatRequest.Message) item;
                    history.add(msg);
                } else if (item instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) item;
                    history.add(new ChatRequest.Message(
                        String.valueOf(m.get("role") != null ? m.get("role") : "user"),
                        String.valueOf(m.get("content") != null ? m.get("content") : ""),
                        null, null, null));
                }
            }
            log.debug("Using chatHistory input: {} messages", history.size());
        }

        // 模式2: conversationId + historyStore（回退）
        if (history == null) {
            ConversationHistoryStore store = context.getHistoryStore();
            String conversationId = context.getConversationId();
            if (store != null && conversationId != null && windowSize > 0) {
                List<ConversationHistoryStore.MessagePair> pairs = store.getHistory(conversationId, windowSize);
                if (pairs != null && !pairs.isEmpty()) {
                    history = new ArrayList<>();
                    for (ConversationHistoryStore.MessagePair pair : pairs) {
                        history.add(ChatRequest.Message.user(pair.query()));
                        if (pair.answer() != null && !pair.answer().isEmpty()) {
                            history.add(ChatRequest.Message.assistant(pair.answer()));
                        }
                    }
                }
            }
        }

        if (history == null || history.isEmpty()) return messages;

        // 去掉聊天历史中的 system 角色消息（模板已有自己的 system prompt）
        history = history.stream()
                .filter(m -> !"system".equals(m.role()))
                .collect(Collectors.toList());
        if (history.isEmpty()) return messages;

        // 按 window.size 截取（chatHistory 模式也支持截取）
        if (windowSize > 0 && history.size() > windowSize * 2) {
            history = history.subList(history.size() - windowSize * 2, history.size());
        }

        // 组织消息顺序: system prompt → 聊天历史 → 当前 user query
        List<ChatRequest.Message> result = new ArrayList<>();
        List<ChatRequest.Message> userMsgs = new ArrayList<>();

        for (ChatRequest.Message msg : messages) {
            if ("system".equals(msg.role()) || "assistant".equals(msg.role())) {
                result.add(msg);
            } else {
                userMsgs.add(msg);
            }
        }

        result.addAll(history);
        result.addAll(userMsgs);
        log.debug("Injected {} history messages into LLM prompt", history.size());
        return result;
    }

    /** 在变量池中查找指定名称的变量 */
    private Object findVariableInPool(NodeExecutionContext context, String varName) {
        com.dify.workflow.model.VariablePool pool = context.getVariablePool();
        if (pool == null) return null;
        for (String nodeId : pool.getNodeIds()) {
            Object val = pool.get(nodeId, varName);
            if (val != null) return val;
        }
        return null;
    }

    /** 检查请求中是否包含图片 URL */
    private boolean hasImageContent(ChatRequest request) {
        if (request.messages() == null) return false;
        for (ChatRequest.Message msg : request.messages()) {
            String content = msg.content();
            if (content != null && (content.contains("image_url")
                    || content.contains("![](")
                    || containsImageUrl(content))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsImageUrl(String content) {
        return content.contains("http") && (content.contains(".png")
                || content.contains(".jpg") || content.contains(".jpeg")
                || content.contains(".gif") || content.contains(".webp"));
    }

}
