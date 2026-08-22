package com.dify.workflow.engine.llm.providers;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.dify.workflow.engine.llm.LlmProviderConfig;
import com.dify.workflow.model.ChatRequest;
import com.dify.workflow.model.LlmCallResult;
import com.dify.workflow.model.StreamDelta;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * LLM Provider 抽象基类。
 * 封装通用的 HTTP 请求逻辑和响应解析逻辑。
 * 所有 Provider 实现只需实现抽象方法定义 API URL 和错误解析。
 *
 * 子类只需实现:
 * - getApiUrl(): 返回 API 端点 URL
 * - parseErrorMessage(): 从响应体中提取错误消息
 *
 * 使用模板方法模式，统一处理请求构建和响应解析流程。
 */
public abstract class AbstractLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AbstractLlmProvider.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /**
     * HTTP 客户端，由子类配置超时时间。
     */
    protected final OkHttpClient httpClient;

    /**
     * Provider 配置信息。
     */
    protected final LlmProviderConfig config;

    /**
     * 构造方法。
     * 初始化 HTTP 客户端和配置。
     */
    protected AbstractLlmProvider(LlmProviderConfig config) {
        this.config = config;
        // 根据配置创建 HTTP 客户端，设置超时时间
        int timeout = config.timeout() != null ? config.timeout() : 60000;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * 调用 LLM API。
     * 模板方法：构建请求 → 发送请求 → 解析响应。
     *
     * @param request Chat 请求
     * @return LLM 调用结果
     */
    public LlmCallResult call(ChatRequest request) {
        try {
            // 1. 构建请求体
            JSONObject requestBody = buildRequestBody(request);
            log.info("LLM request body: {}", requestBody.toJSONString());

            // 2. 构建 HTTP 请求头
            Headers headers = buildHeaders();

            // 3. 构建 HTTP 请求
            String apiUrl = getApiUrl();
            Request httpRequest = new Request.Builder()
                    .url(apiUrl)
                    .post(RequestBody.create(JSON_MEDIA_TYPE, requestBody.toJSONString()))
                    .headers(headers)
                    .build();
            log.info("LLM request apiUrl: {}", apiUrl);
            // 4. 发送请求并解析响应
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                // 5. 检查 HTTP 状态码
                if (!response.isSuccessful()) {
                    log.error("API call failed with HTTP {}: {}", response.code(), responseBody);
                    return LlmCallResult.failure("HTTP " + response.code() + ": " + responseBody);
                }

                // 6. 解析响应体
                return parseResponse(responseBody);
            }

        } catch (Exception e) {
            log.error("API call failed", e);
            return LlmCallResult.failure(e.getMessage());
        }
    }

    /**
     * 流式调用 LLM,使用 okhttp3.sse.EventSource 解析 SSE。
     * 每收到一个 data: {...} 块触发一次 onDelta 回调,stream 结束(收到 [DONE]
     * 或 finishReason=stop)时发最后一条 delta=null 收尾。
     *
     * <p>OpenAI/DeepSeek 兼容 SSE 格式:
     * <pre>
     * data: {"choices":[{"delta":{"content":"Hello"}}]}
     *
     * data: {"choices":[{"delta":{"content":", "}}]}
     *
     * data: {"choices":[{"delta":{"content":"world"}}]}
     *
     * data: [DONE]
     * </pre>
     */
    public void callStream(ChatRequest request, Consumer<StreamDelta> onDelta) throws IOException {
        if (onDelta == null) {
            throw new IllegalArgumentException("onDelta callback is required");
        }
        // 1. 构造请求体(注入 stream: true)
        JSONObject requestBody = buildRequestBody(request);
        requestBody.put("stream", true);
        log.info("LLM stream request body: {}", requestBody.toJSONString());

        // 2. 构造 HTTP 请求头(加 Accept: text/event-stream)
        Headers.Builder hb = new Headers.Builder();
        for (int i = 0; i < buildHeaders().size(); i++) {
            hb.add(buildHeaders().name(i), buildHeaders().value(i));
        }
        hb.set("Accept", "text/event-stream");

        String apiUrl = getApiUrl();
        Request httpRequest = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(JSON_MEDIA_TYPE, requestBody.toJSONString()))
                .headers(hb.build())
                .build();

        // 3. 阻塞转异步:CountDownLatch 等 EventSource 关闭
        final int[] idx = {0};
        final CountDownLatch latch = new CountDownLatch(1);
        final IOException[] failure = new IOException[1];

        EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onOpen(EventSource eventSource, Response response) {
                log.debug("SSE stream opened: status={}", response.code());
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                // data 收尾标记 [DONE]
                if ("[DONE]".equals(data)) {
                    onDelta.accept(new StreamDelta(null, idx[0]++, "stop"));
                    return;
                }
                try {
                    JSONObject obj = JSON.parseObject(data);
                    if (obj == null || !obj.containsKey("choices")) {
                        return;
                    }
                    JSONArray choices = obj.getJSONArray("choices");
                    if (choices == null || choices.isEmpty()) {
                        return;
                    }
                    JSONObject firstChoice = choices.getJSONObject(0);
                    if (firstChoice == null) {
                        return;
                    }
                    JSONObject delta = firstChoice.getJSONObject("delta");
                    if (delta == null) {
                        return;
                    }
                    // 关键修复(2026-08-16):同时提取 content 和推理字段。
                    //   DeepSeek: delta.reasoning_content
                    //   vLLM 0.22+: delta.reasoning
                    //   两者独立递增。原代码只读 content,会丢思维链。
                    String content = delta.getString("content");
                    String reasoningContent = delta.getString("reasoning_content");
                    if (reasoningContent == null || reasoningContent.isEmpty()) {
                        reasoningContent = delta.getString("reasoning");
                    }
                    if (content != null || reasoningContent != null) {
                        onDelta.accept(new StreamDelta(content, idx[0]++, null, reasoningContent));
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse SSE chunk: data={}, err={}", data, e.getMessage());
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                log.debug("SSE stream closed");
                latch.countDown();
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                String errMsg;
                String responseBody = "";
                if (t != null) {
                    errMsg = t.getMessage();
                } else if (response != null) {
                    errMsg = "HTTP " + response.code();
                    // 关键修复:读取响应体,DeepSeek 400 错误通常带具体原因(JSON),
                    //   原代码只输出 HTTP code 会丢失调试信息(2026-08-16)。
                    try {
                        if (response.body() != null) {
                            responseBody = response.body().string();
                            errMsg = errMsg + " | body=" + responseBody;
                        }
                    } catch (Exception readErr) {
                        log.warn("Failed to read error response body: {}", readErr.getMessage());
                    }
                } else {
                    errMsg = "unknown error";
                }
                log.error("SSE stream failure: {}", errMsg);
                onDelta.accept(new StreamDelta(null, idx[0]++, "error:" + errMsg));
                failure[0] = (t instanceof IOException) ? (IOException) t
                        : new IOException(errMsg, t);
                latch.countDown();
            }
        };

        EventSource es = EventSources.createFactory(httpClient).newEventSource(httpRequest, listener);
        try {
            // 阻塞等流结束(转异步为同步)
            boolean finished = latch.await(10, TimeUnit.MINUTES);
            if (!finished) {
                es.cancel();
                throw new IOException("SSE stream timeout after 10 minutes");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            es.cancel();
            throw new IOException("SSE stream interrupted", ie);
        }
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    /**
     * 构建 HTTP 请求头。
     * 默认包含 Authorization 和 Content-Type。
     * 子类可重写以添加额外的请求头。
     */
    protected Headers buildHeaders() {
        return new Headers.Builder()
                .add("Authorization", "Bearer " + config.apiKey())
                .add("Content-Type", "application/json")
                .build();
    }

    /**
     * 构建请求体 JSON。
     * 包含 model、messages、tools 和可选参数（temperature、max_tokens、top_p）。
     */
    protected JSONObject buildRequestBody(ChatRequest request) {
        JSONObject builder = new JSONObject();

        // 设置模型名称
        builder.put("model", request.model() != null ? request.model() : config.defaultModel());

        // 构建消息数组
        JSONArray messagesArray = new JSONArray();
        if (request.messages() != null) {
            for (ChatRequest.Message msg : request.messages()) {
                JSONObject msgNode = buildMessageNode(msg);
                messagesArray.add(msgNode);
            }
        }
        builder.put("messages", messagesArray);

        // 设置 tools（用于 Function Calling）
        if (request.tools() != null && !request.tools().isEmpty()) {
            builder.put("tools", request.tools());
        }

        // 设置 temperature（优先使用请求中的值，否则使用配置值）
        if (request.temperature() != null) {
            builder.put("temperature", request.temperature());
        } else if (config.temperature() != null) {
            builder.put("temperature", config.temperature());
        }

        // 设置 max_tokens
        if (request.maxTokens() != null) {
            builder.put("max_tokens", request.maxTokens());
        } else if (config.maxTokens() != null) {
            builder.put("max_tokens", config.maxTokens());
        }

        // 设置 top_p
        if (request.topP() != null) {
            builder.put("top_p", request.topP());
        }

        // 添加额外参数
        // 字符串值直接传递；Map/List 值转成 JSONObject/JSONArray 以保留 JSON 嵌套结构
        // （如 OpenAI 的 response_format={"type":"json_object"} 需要传 Map 而非 String）
        // Boolean/Number 值原样透传(JSONObject.put 会自动序列化)
        if (request.extraParameters() != null) {
            for (Map.Entry<String, Object> entry : request.extraParameters().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map) {
                    value = new JSONObject((Map) value);
                } else if (value instanceof List) {
                    value = new JSONArray((List) value);
                }
                // Boolean/Number/String 都直接 put(JSONObject 会正确序列化)
                // 注意:value.toString() 会把 boolean 转成 "true"/"false" 字符串,
                //   对 OpenAI 的 thinking 等 boolean 参数是错的。关键修复(2026-08-07)。
                builder.put(entry.getKey(), value);
            }
        }

        return builder;
    }

    /**
     * 构建单个消息节点。
     * 支持 tool_calls 和 tool_call_id。
     */
    private JSONObject buildMessageNode(ChatRequest.Message msg) {
        JSONObject msgNode = new JSONObject();
        msgNode.put("role", msg.role());

        // 设置内容（assistant 消息可能没有内容但有 tool_calls）
        if (msg.content() != null) {
            msgNode.put("content", msg.content());
        }

        // 设置 tool_calls（assistant 消息）
        if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
            JSONArray toolCallsArray = new JSONArray();
            for (ChatRequest.ToolCall tc : msg.toolCalls()) {
                JSONObject tcNode = new JSONObject();
                tcNode.put("id", tc.id());
                JSONObject funcNode = new JSONObject();
                funcNode.put("name", tc.function().name());
                funcNode.put("arguments", tc.function().arguments());
                tcNode.put("function", funcNode);
                toolCallsArray.add(tcNode);
            }
            msgNode.put("tool_calls", toolCallsArray);
        }

        // 设置 tool_call_id（tool 消息）
        if (msg.toolCallId() != null) {
            msgNode.put("tool_call_id", msg.toolCallId());
        }

        return msgNode;
    }

    /**
     * 解析响应体。
     * 模板方法：解析 JSON → 检查错误 → 提取内容 → 提取 token 用量 → 提取 tool_calls。
     */
    protected LlmCallResult parseResponse(String responseBody) {
        try {
            JSONObject root = JSON.parseObject(responseBody);

            // 检查是否有错误
            if (root.containsKey("error")) {
                String errorMsg = parseErrorMessage(root.get("error"));
                return LlmCallResult.failure(errorMsg);
            }

            // 提取 choices
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return LlmCallResult.failure("No choices in response");
            }

            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");

            // 提取内容
            String content = message.getString("content");

            // 提取思维链:DeepSeek 用 reasoning_content,vLLM 0.22+ 用 reasoning
            String reasoningContent = message.getString("reasoning_content");
            if (reasoningContent == null || reasoningContent.isEmpty()) {
                reasoningContent = message.getString("reasoning");
            }

            // 提取 tool_calls
            List<ChatRequest.ToolCall> toolCalls = null;
            List<ChatRequest.Message> assistantMessages = new java.util.ArrayList<>();

            if (message.containsKey("tool_calls")) {
                JSONArray toolCallsArray = message.getJSONArray("tool_calls");
                toolCalls = new java.util.ArrayList<>();
                for (int i = 0; i < toolCallsArray.size(); i++) {
                    JSONObject tc = toolCallsArray.getJSONObject(i);
                    String id = tc.getString("id");
                    JSONObject func = tc.getJSONObject("function");
                    String funcName = func.getString("name");
                    String arguments = func.getString("arguments");
                    toolCalls.add(new ChatRequest.ToolCall(id, new ChatRequest.Function(funcName, arguments)));
                }
            }

            // 构建 assistant 消息
            List<ChatRequest.Message> msgs = new java.util.ArrayList<>();
            msgs.add(new ChatRequest.Message("assistant", content, null, toolCalls, null));
            assistantMessages.addAll(msgs);

            // 提取 token 用量
            Integer promptTokens = null;
            Integer completionTokens = null;
            Integer totalTokens = null;

            JSONObject usage = root.getJSONObject("usage");
            if (usage != null) {
                if (usage.containsKey("prompt_tokens")) {
                    promptTokens = usage.getInteger("prompt_tokens");
                }
                if (usage.containsKey("completion_tokens")) {
                    completionTokens = usage.getInteger("completion_tokens");
                }
                if (usage.containsKey("total_tokens")) {
                    totalTokens = usage.getInteger("total_tokens");
                }
            }

            // 提取 finish_reason
            String finishReason = "stop";
            if (firstChoice.containsKey("finish_reason")) {
                finishReason = firstChoice.getString("finish_reason");
            }

            return new LlmCallResult(content, null, promptTokens, completionTokens, totalTokens,
                    finishReason, true, null, toolCalls, assistantMessages, reasoningContent);

        } catch (Exception e) {
            log.error("Failed to parse response", e);
            return LlmCallResult.failure("Failed to parse response: " + e.getMessage());
        }
    }

    /**
     * 获取 API URL。
     * 子类实现，返回具体的 API 端点。
     *
     * @return API URL，例如 "https://api.openai.com/v1/chat/completions"
     */
    protected abstract String getApiUrl();

    /**
     * 解析错误消息。
     * 子类实现，根据不同 Provider 的错误格式提取错误消息。
     *
     * @param errorObj 错误对象（可能是字符串或 JSONObject）
     * @return 错误消息字符串
     */
    protected String parseErrorMessage(Object errorObj) {
        if (errorObj == null) {
            return "Unknown error";
        }
        if (errorObj instanceof JSONObject) {
            return ((JSONObject) errorObj).getString("message");
        }
        return errorObj.toString();
    }
}
