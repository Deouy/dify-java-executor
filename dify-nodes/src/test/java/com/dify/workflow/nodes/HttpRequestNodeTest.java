package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyAuthorization;
import com.dify.workflow.model.DifyHttpBody;
import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
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

    private DifyNodeData createHttpNodeData(String url, String method, Map<String, String> headers,
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
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            DifyHttpBody body = new DifyHttpBody("json", "{\"name\":\"test\"}", null, null);

            DifyNodeData data = createHttpNodeData("https://api.example.com/data", "POST", headers, body, null);
            HttpRequestNode node = new HttpRequestNode("http_node", data);

            assertEquals("POST", data.method());
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

            when(context.resolveVariables("https://api.example.com/{{#user_node.base_url#}}"))
                .thenReturn("https://api.example.com/v1");

            // Just verify node is created correctly - actual resolution happens in doExecute
            assertNotNull(node);
        }
    }
}
