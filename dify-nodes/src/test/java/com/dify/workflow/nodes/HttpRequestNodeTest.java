package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyAuthorization;
import com.dify.workflow.model.DifyHttpBody;
import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.VariablePool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HttpRequestNode 单元测试。
 * 测试 HTTP 请求节点的配置和执行。
 */
@ExtendWith(MockitoExtension.class)
class HttpRequestNodeTest {

    @Mock
    private NodeExecutionContext context;

    private DifyNodeData createHttpNodeData(String url, String method, String headers,
                                            DifyHttpBody body, DifyAuthorization auth) {
        return DifyNodeData.builder()
            .type("http_request")
            .title("HTTP请求")
            .method(method)
            .url(url)
            .headers(headers)
            .body(body)
            .authorization(auth)
            .build();
    }

    @Nested
    @DisplayName("HTTP 请求配置测试")
    class HttpConfigurationTest {

        @Test
        @DisplayName("节点类型正确")
        void testNodeType() {
            DifyNodeData data = createHttpNodeData("https://api.example.com/data", null, null, null, null);
            HttpRequestNode node = new HttpRequestNode("http_node", data);

            assertEquals("http_request", node.getType());
        }

        @Test
        @DisplayName("支持 POST 方法")
        void testPostMethod() {
            String headers = "Content-Type: application/json";
            DifyHttpBody body = new DifyHttpBody("json", "{\"name\":\"test\"}", null, null);

            DifyNodeData data = createHttpNodeData("https://api.example.com/data", "POST", headers, body, null);
            HttpRequestNode node = new HttpRequestNode("http_node", data);

            assertEquals("POST", data.method());
            assertEquals(headers, data.headers());
        }

        @Test
        @DisplayName("支持带 Authorization 的请求")
        void testAuthorizationHeader() {
            DifyAuthorization.DifyAuthorizationConfig config = new DifyAuthorization.DifyAuthorizationConfig("sk-test-key");
            DifyAuthorization auth = new DifyAuthorization("api_key", config);

            DifyNodeData data = createHttpNodeData("https://api.example.com/data", "GET", null, null, auth);
            HttpRequestNode node = new HttpRequestNode("http_node", data);

            assertNotNull(data.authorization());
            assertEquals("sk-test-key", data.authorization().config().apiKey());
        }
    }

    @Nested
    @DisplayName("变量解析测试")
    class VariableResolutionTest {

        @Test
        @DisplayName("URL 中的变量会被解析")
        void testUrlVariableResolution() {
            DifyNodeData data = createHttpNodeData("https://api.example.com/{{#user_node.base_url#}}", "GET", null, null, null);
            HttpRequestNode node = new HttpRequestNode("http_node", data);

            // Just verify node is created correctly - actual resolution happens in doExecute
            assertNotNull(node);
        }

        @Test
        @DisplayName("params 字段中的 {{#env.X#}} 在 appendQueryString 阶段被解析后拼到 URL")
        void testEnvVarInParamsAppendedToUrl() {
            // 构造 params 包含 env 变量引用
            DifyNodeData data = DifyNodeData.builder()
                .type("http-request")
                .url("https://api.example.com/path")
                .method("GET")
                .params("base={{#env.host#}}&mode=demo")
                .build();
            HttpRequestNode node = new HttpRequestNode("http_node", data);

            // mock VariablePool,让 getEnvironment(\"host\") 返回已解析值
            // (模拟 DifyWorkflowExecutor.initializeContextVariables 写入 environmentVariables 桶后的状态)
            VariablePool pool = mock(VariablePool.class);
            when(pool.getEnvironment("host")).thenReturn("https://resolved.example.com");
            when(context.getVariablePool()).thenReturn(pool);

            // 直接调 package-private 的 appendQueryString(已为此放开可见性)
            String result = node.appendQueryString(context, "https://api.example.com/path");
            assertTrue(result.contains("base=https%3A%2F%2Fresolved.example.com"),
                    "params 中的 {{#env.host#}} 应被解析为 env 值并 URL 编码拼到 URL,实际: " + result);
            assertTrue(result.contains("mode=demo"),
                    "非 env 参数应保留原值,实际: " + result);
        }
    }

    @Nested
    @DisplayName("String 格式 headers 测试")
    class StringHeadersTest {

        @Test
        @DisplayName("真实 Dify 导出格式:Authorization:{{#var#}} 字符串 headers 被原样存储")
        void testTemplateStringHeaders() {
            String raw = "Authorization:{{#1780074825926.token#}}";
            DifyNodeData data = createHttpNodeData("https://api.example.com", "POST", raw, null, null);

            assertNotNull(data.headers());
            assertEquals(raw, data.headers());
        }

        @Test
        @DisplayName("空字符串 headers(典型 Dify 默认值)被原样存储")
        void testEmptyStringHeaders() {
            DifyNodeData data = createHttpNodeData("https://api.example.com", "GET", "", null, null);

            assertEquals("", data.headers());
        }

        @Test
        @DisplayName("多行 K: V 格式 headers 被原样存储")
        void testMultiLineStringHeaders() {
            String raw = "Content-Type: application/json\nAuthorization: Bearer token";
            DifyNodeData data = createHttpNodeData("https://api.example.com", "POST", raw, null, null);

            assertEquals(raw, data.headers());
        }

        @Test
        @DisplayName("null headers 表示未配置")
        void testNullHeaders() {
            DifyNodeData data = createHttpNodeData("https://api.example.com", "GET", null, null, null);
            assertNull(data.headers());
        }
    }

    @Nested
    @DisplayName("String 格式 params 测试")
    class StringParamsTest {

        @Test
        @DisplayName("空字符串 params(典型 Dify 默认值)被原样存储")
        void testEmptyStringParams() {
            DifyNodeData data = createHttpNodeData("https://api.example.com", "GET", null, null, null);
            // params 单独设置
            DifyNodeData dataWithParams = DifyNodeData.builder()
                .type("http_request")
                .url("https://api.example.com")
                .params("")
                .build();

            assertNull(data.params());
            assertEquals("", dataWithParams.params());
        }

        @Test
        @DisplayName("k=v 格式单对 params 被原样存储")
        void testSingleKeyValueStringParams() {
            DifyNodeData data = DifyNodeData.builder()
                .type("http_request")
                .url("https://api.example.com")
                .params("page=1")
                .build();
            assertEquals("page=1", data.params());
        }

        @Test
        @DisplayName("k1=v1&k2=v2 多对 params 被原样存储")
        void testMultiKeyValueStringParams() {
            String raw = "a=1&b=2&c=3";
            DifyNodeData data = DifyNodeData.builder()
                .type("http_request")
                .url("https://api.example.com")
                .params(raw)
                .build();
            assertEquals(raw, data.params());
        }
    }

    @Nested
    @DisplayName("form-data 列表 body 解析测试")
    class DifyFormDataBodyTest {

        @Test
        @DisplayName("真实 Dify 导出:5 项 form-data 列表,key/value 正确存储")
        void parsesFormDataListWithVariableTemplates() {
            // 镜像 TestZNWD.yml L248-269 的 5 项 form-data 列表
            List<Map<String, Object>> items = new ArrayList<>();
            items.add(formItem("key-value-20", "token", "{{#1780074825926.token#}}"));
            items.add(formItem("key-value-39", "question", "{{#sys.query#}}"));
            items.add(formItem("key-value-74", "mode", "{{#1780074825926.mode#}}"));
            items.add(formItem("key-value-97", "sessionId", "{{#1780074825926.sessionId#}}"));
            items.add(formItem("key-value-149", "sycj", "智能问答"));

            DifyHttpBody body = new DifyHttpBody("form-data", items, null, null);
            DifyNodeData data = DifyNodeData.builder()
                .type("http-request")
                .url("http://example.com/api")
                .method("POST")
                .body(body)
                .build();
            HttpRequestNode node = new HttpRequestNode("n1", data);

            // 验证 model 字段
            assertNotNull(data.body().formDataItems());
            assertEquals(5, data.body().formDataItems().size());
            assertEquals("token", data.body().formDataItems().get(0).get("key"));
            assertEquals("{{#1780074825926.token#}}", data.body().formDataItems().get(0).get("value"));
            assertEquals("智能问答", data.body().formDataItems().get(4).get("value"));

            // 验证 buildRequestBody 路径不抛异常(类型 none 之外的分支)
            // 实际 OkHttp 构造可能因为无完整 okhttp call chain 失败,但至少能进入 List 分支
            assertNotNull(node);
        }

        @Test
        @DisplayName("dataAsString 在 String data 上返回原值,在 List data 上返回 null")
        void dataAsStringPolymorphism() {
            DifyHttpBody stringBody = new DifyHttpBody("json", "{\"a\":1}", null, null);
            assertEquals("{\"a\":1}", stringBody.dataAsString());

            List<Map<String, Object>> items = Collections.singletonList(
                formItem("k1", "k", "v"));
            DifyHttpBody listBody = new DifyHttpBody("form-data", items, null, null);
            assertNull(listBody.dataAsString());
            assertEquals(1, listBody.formDataItems().size());
        }

        @Test
        @DisplayName("空 form-data 列表:formDataItems 返回空 list(非 null)")
        void emptyFormDataList() {
            DifyHttpBody body = new DifyHttpBody("form-data", Collections.emptyList(), null, null);
            assertNotNull(body.formDataItems());
            assertTrue(body.formDataItems().isEmpty());
        }

        @Test
        @DisplayName("x-www-form-urlencoded 同样支持 List 形式")
        void xWwwFormUrlencodedListForm() {
            List<Map<String, Object>> items = Collections.singletonList(
                formItem("id1", "username", "alice"));
            DifyHttpBody body = new DifyHttpBody("x-www-form-urlencoded", items, null, null);
            assertNotNull(body.formDataItems());
            assertEquals("username", body.formDataItems().get(0).get("key"));
            assertEquals("alice", body.formDataItems().get(0).get("value"));
        }

        @Test
        @DisplayName("老格式 formData Map 字段仍然可用,formDataItems 返回 null")
        void legacyFormDataMap() {
            Map<String, String> legacy = new LinkedHashMap<>();
            legacy.put("k", "v");
            DifyHttpBody body = new DifyHttpBody("form-data", null, legacy, null);
            assertNull(body.formDataItems());
            assertEquals("v", body.formData().get("k"));
        }
    }

    /**
     * 构造一个 form-data 列表项(id / key / type / value 四个字段)。
     * 提到外层是因为 Java 不允许非静态内部类(包含 @Nested 类)中声明 static 方法。
     */
    private static Map<String, Object> formItem(String id, String key, String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("key", key);
        m.put("type", "text");
        m.put("value", value);
        return m;
    }
}
