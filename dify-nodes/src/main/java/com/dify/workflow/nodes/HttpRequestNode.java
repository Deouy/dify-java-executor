package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyAuthorization;
import com.dify.workflow.model.DifyHttpBody;
import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP Request node - 调用外部 HTTP 服务。
 *
 * <p>参考 Dify 官方 Python 实现 {@code graphon.nodes.http_request.node.HttpRequestNode}。
 * 输出变量 (与 Dify 对齐): {@code body} / {@code status_code} / {@code headers} / {@code success}。
 * 注意:旧版 Java 实现使用 {@code response} 字段名,与 Dify 不一致,
 * 下游 {@code {{#node.body#}}} 引用会字面回显。本次修复重命名为 {@code body}。</p>
 *
 * <p>支持的方法:GET / POST / PUT / DELETE / PATCH / HEAD。
 * 支持的 body 类型:json (默认) / text / form-data / x-www-form-urlencoded / binary / none。
 * 支持的 authorization:no-auth / api-key (Bearer) / basic / custom (自定义 header)。</p>
 */
public class HttpRequestNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestNode.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // Dify 默认超时(秒),YAML 中 timeout.* == 0 时回退
    private static final int DEFAULT_CONNECT_TIMEOUT_SEC = 10;
    private static final int DEFAULT_READ_TIMEOUT_SEC = 60;
    private static final int DEFAULT_WRITE_TIMEOUT_SEC = 10;

    private final OkHttpClient httpClient;

    public HttpRequestNode(String id, DifyNodeData data) {
        super(id, NodeType.HTTP_REQUEST, data);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build();
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing HTTP request node: {}", id);

        String url = data.url();
        String method = data.method();
        if (method == null) {
            method = "GET";
        }

        // 解析 URL 中的 {{#xxx#}} 变量
        url = resolveVariables(context, url);
        // 解析 params (query string) 并追加到 URL
        url = appendQueryString(context, url);

        // 构造请求头
        Headers.Builder headersBuilder = new Headers.Builder();
        applyHeaders(context, headersBuilder);
        applyAuthorization(headersBuilder);

        // 构造请求体
        RequestBody body = buildRequestBody(context);

        // 构造并执行请求
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .headers(headersBuilder.build());

        String upperMethod = method == null ? "" : method.toUpperCase();
        if ("GET".equals(upperMethod)) {
            requestBuilder.get();
        } else if ("POST".equals(upperMethod)) {
            requestBuilder.post(body != null ? body : RequestBody.create(JSON, ""));
        } else if ("PUT".equals(upperMethod)) {
            requestBuilder.put(body != null ? body : RequestBody.create(JSON, ""));
        } else if ("DELETE".equals(upperMethod)) {
            // DELETE 可以带 body 也可以不带,YAML 中 body.type==none 时 body==null,OkHttp 允许 null
            if (body != null) {
                requestBuilder.delete(body);
            } else {
                requestBuilder.delete();
            }
        } else if ("PATCH".equals(upperMethod)) {
            requestBuilder.patch(body != null ? body : RequestBody.create(JSON, ""));
        } else if ("HEAD".equals(upperMethod)) {
            requestBuilder.head();
        } else {
            requestBuilder.get();
        }

        // 始终先写齐输出字段,即使后续异常也保证下游 {{#node.body#}} 不会拿到 null
        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            int statusCode = response.code();
            Map<String, String> responseHeaders = extractHeaders(response);

            context.setVariable(id, "body", responseBody);
            context.setVariable(id, "status_code", statusCode);
            context.setVariable(id, "headers", responseHeaders);
            context.setVariable(id, "success", response.isSuccessful());

            log.debug("HTTP request node response: status={}, bodyLength={}", statusCode, responseBody.length());
        } catch (IOException e) {
            // 关键修复:异常分支也要写齐 body/status_code/headers/success,
            // 否则下游 {{#node.body#}} 拿到 null,VariableResolver 找不到值就字面回显。
            log.error("HTTP request node {} failed: {}", id, e.getMessage());
            context.setVariable(id, "body", "");
            context.setVariable(id, "status_code", 0);
            context.setVariable(id, "headers", new HashMap<String, String>());
            context.setVariable(id, "success", false);
            context.setVariable(id, "error", e.getMessage());
            throw e;
        }
    }

    /**
     * 解析 data.headers() 为 OkHttp headers。YAML 中 headers 是 String("K: V\nK: V") 格式,
     * 按行解析为 K-V 对。空字符串或 null 表示未配置。
     */
    private void applyHeaders(NodeExecutionContext context, Headers.Builder builder) {
        String headerStr = data.headers();
        if (headerStr == null || headerStr.trim().isEmpty()) {
            return;
        }
        headerStr = resolveVariables(context, headerStr);
        for (String line : headerStr.split("\\r?\\n")) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                if (!key.isEmpty()) {
                    builder.add(key, value);
                }
            }
        }
    }

    /**
     * 处理 authorization 头。支持 no-auth / api-key / basic / custom 四种类型。
     */
    private void applyAuthorization(Headers.Builder builder) {
        DifyAuthorization auth = data.authorization();
        if (auth == null || auth.config() == null) {
            return;
        }
        String type = auth.type();
        DifyAuthorization.DifyAuthorizationConfig cfg = auth.config();

        if ("api-key".equalsIgnoreCase(type) || "api_key".equalsIgnoreCase(type)) {
            String apiKey = cfg.apiKey();
            if (apiKey != null && !apiKey.isEmpty()) {
                builder.add("Authorization", "Bearer " + apiKey);
            }
        } else if ("basic".equalsIgnoreCase(type)) {
            // basic 用 apiKey 字段存 "user:pass" 形式
            String cred = cfg.apiKey();
            if (cred != null && !cred.isEmpty()) {
                String encoded = Base64.getEncoder().encodeToString(cred.getBytes(StandardCharsets.UTF_8));
                builder.add("Authorization", "Basic " + encoded);
            }
        }
        // custom 类型留待 P1 扩展,需要新增 DifyAuthorizationConfig 字段(header/header_type)
    }

    /**
     * 解析 data.params() 为 URL query string 并追加。
     * params 字段为 String("k1=v1&k2=v2") 格式,按 & 和 = 拆分。
     */
    String appendQueryString(NodeExecutionContext context, String url) {
        String paramStr = data.params();
        if (paramStr == null || paramStr.trim().isEmpty()) {
            return url;
        }
        paramStr = resolveVariables(context, paramStr);

        Map<String, String> queryMap = new LinkedHashMap<>();
        for (String pair : paramStr.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                queryMap.put(pair.substring(0, idx), pair.substring(idx + 1));
            } else if (!pair.isEmpty()) {
                queryMap.put(pair, "");
            }
        }
        if (queryMap.isEmpty()) {
            return url;
        }

        StringBuilder sb = new StringBuilder(url);
        sb.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : queryMap.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            sb.append(encodeUrl(entry.getKey()));
            sb.append("=");
            sb.append(encodeUrl(entry.getValue()));
            first = false;
        }
        return sb.toString();
    }

    /**
     * 构造 RequestBody。YAML body.type 分发:
     * <ul>
     *   <li>none / null → null</li>
     *   <li>json → application/json (data 字段)</li>
     *   <li>text → text/plain (data 字段)</li>
     *   <li>x-www-form-urlencoded → FormBody (formData Map)</li>
     *   <li>form-data → MultipartBody (formData Map)</li>
     *   <li>binary → application/octet-stream (data 字段 base64 字符串)</li>
     * </ul>
     */
    RequestBody buildRequestBody(NodeExecutionContext context) {
        DifyHttpBody httpBody = data.body();
        if (httpBody == null) {
            return null;
        }
        String type = httpBody.type();
        if (type == null || "none".equalsIgnoreCase(type)) {
            return null;
        }

        if ("json".equalsIgnoreCase(type)) {
            String bodyData = resolveVariables(context, httpBody.dataAsString());
            return RequestBody.create(JSON, bodyData != null ? bodyData : "");
        }
        if ("text".equalsIgnoreCase(type)) {
            String bodyData = resolveVariables(context, httpBody.dataAsString());
            return RequestBody.create(MediaType.parse("text/plain; charset=utf-8"),
                bodyData != null ? bodyData : "");
        }
        if ("x-www-form-urlencoded".equalsIgnoreCase(type)
                || "form-data".equalsIgnoreCase(type)
                || "multipart/form-data".equalsIgnoreCase(type)) {
            // 关键修复:真实 Dify 导出 form-data 时,data 字段是 List<Map> 形式,
            // 每项含 id / key / type / value;少数老格式用 form_data Map 字段。
            // 优先用 data 列表,兼容老 Map 字段。
            List<Map<String, Object>> items = httpBody.formDataItems();
            Map<String, String> legacyMap = httpBody.formData();

            boolean isMultipart = !"x-www-form-urlencoded".equalsIgnoreCase(type);
            if (isMultipart) {
                MultipartBody.Builder mb = new MultipartBody.Builder().setType(MultipartBody.FORM);
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        addFormField(context, mb, item);
                    }
                } else if (legacyMap != null) {
                    for (Map.Entry<String, String> e : legacyMap.entrySet()) {
                        mb.addFormDataPart(e.getKey(), resolveVariables(context, e.getValue()));
                    }
                }
                return mb.build();
            } else {
                FormBody.Builder fb = new FormBody.Builder();
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        addFormField(context, fb, item);
                    }
                } else if (legacyMap != null) {
                    for (Map.Entry<String, String> e : legacyMap.entrySet()) {
                        fb.add(e.getKey(), resolveVariables(context, e.getValue()));
                    }
                }
                return fb.build();
            }
        }
        if ("binary".equalsIgnoreCase(type)) {
            // binary 在 Dify 中是 DifyHttpBinary,这里取 data 字段作 base64 raw body 占位
            String bodyData = httpBody.dataAsString();
            return RequestBody.create(MediaType.parse("application/octet-stream"),
                bodyData != null ? bodyData : "");
        }
        // 兜底:当作 json 处理
        String bodyData = resolveVariables(context, httpBody.dataAsString());
        return RequestBody.create(JSON, bodyData != null ? bodyData : "");
    }

    /**
     * 从 form-data 列表项中读取 key 和 value,追加到 FormBody 或 MultipartBody。
     * 列表项字段:id(忽略,前端 UI 标识) / key(参数名) / type(忽略,目前只支持 text)
     * / value(参数值,可能含 {{#var#}} 变量占位符)。
     * type==file 的情况暂不处理(本仓库范围外,留待 P1)。
     */
    private void addFormField(NodeExecutionContext context, Object builder, Map<String, Object> item) {
        if (item == null) {
            return;
        }
        Object keyObj = item.get("key");
        Object valueObj = item.get("value");
        if (keyObj == null) {
            return;
        }
        String key = String.valueOf(keyObj);
        String raw = valueObj == null ? "" : String.valueOf(valueObj);
        String resolved = resolveVariables(context, raw);
        if (builder instanceof FormBody.Builder) {
            ((FormBody.Builder) builder).add(key, resolved);
        } else if (builder instanceof MultipartBody.Builder) {
            ((MultipartBody.Builder) builder).addFormDataPart(key, resolved);
        }
    }

    /**
     * 从 OkHttp Response 提取响应头为 Map(去重:多值用 ", " 拼接)。
     */
    private Map<String, String> extractHeaders(Response response) {
        Map<String, String> result = new LinkedHashMap<>();
        Headers headers = response.headers();
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.name(i);
            String value = headers.value(i);
            if (result.containsKey(name)) {
                result.put(name, result.get(name) + ", " + value);
            } else {
                result.put(name, value);
            }
        }
        return result;
    }

    /**
     * URL 编码工具方法:UTF-8 编码失败几乎不可能(每个 JVM 都支持),
     * 用 try-catch 兜底返回原文,避免抛 UnsupportedEncodingException 影响主流程。
     */
    private static String encodeUrl(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }
}
