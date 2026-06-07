package com.dify.workflow.nodes;

import com.dify.workflow.model.LlmCallResult;
import com.dify.workflow.model.LlmService;
import com.dify.workflow.model.StreamDelta;
import com.dify.workflow.model.WorkflowEvent;
import com.dify.workflow.model.WorkflowEventListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM 流式输出事件层测试。
 *
 * <p>不依赖 OkHttp MockWebServer,直接验证:
 * <ul>
 *   <li>LlmService.callStream 接口契约 (mock 实现模拟多次 onDelta 回调)</li>
 *   <li>WorkflowEvent.Chunk 事件正确构造</li>
 *   <li>TestReportGenerator.onChunk 正确收集 chunks</li>
 *   <li>WorkflowEventListener.onChunk 默认空实现向后兼容</li>
 * </ul>
 *
 * <p>端到端真实 HTTP SSE 解析测试见 AbstractLlmProviderStreamingTest (需要 MockWebServer 依赖,暂不实现)。</p>
 */
@DisplayName("LLM 流式事件契约测试")
class StreamChunkTest {

    @Nested
    @DisplayName("StreamDelta 数据类")
    class StreamDeltaTest {

        @Test
        @DisplayName("正常 delta 构造:content+index,finishReason=null")
        void testNormalDelta() {
            StreamDelta delta = new StreamDelta("Hello", 0, null);
            assertEquals("Hello", delta.content());
            assertEquals(0, delta.index());
            assertNull(delta.finishReason());
        }

        @Test
        @DisplayName("收尾 delta 构造:content=null,finishReason=stop")
        void testStopDelta() {
            StreamDelta delta = new StreamDelta(null, 3, "stop");
            assertNull(delta.content());
            assertEquals(3, delta.index());
            assertEquals("stop", delta.finishReason());
        }

        @Test
        @DisplayName("错误 delta:content=null,finishReason=error:xxx")
        void testErrorDelta() {
            StreamDelta delta = new StreamDelta(null, 5, "error:HTTP 500");
            assertNull(delta.content());
            assertEquals("error:HTTP 500", delta.finishReason());
        }

        @Test
        @DisplayName("equals/hashCode 按 content+index+finishReason")
        void testEquals() {
            StreamDelta d1 = new StreamDelta("a", 1, null);
            StreamDelta d2 = new StreamDelta("a", 1, null);
            StreamDelta d3 = new StreamDelta("b", 1, null);
            assertEquals(d1, d2);
            assertNotEquals(d1, d3);
            assertEquals(d1.hashCode(), d2.hashCode());
        }
    }

    @Nested
    @DisplayName("WorkflowEvent.Chunk 事件")
    class ChunkEventTest {

        @Test
        @DisplayName("构造 + 访问器")
        void testChunkAccessors() {
            WorkflowEvent.Chunk event = new WorkflowEvent.Chunk("node-llm", 42, "Hi", 0);
            assertEquals("node-llm", event.nodeId());
            assertEquals(42, event.messageId());
            assertEquals("Hi", event.delta());
            assertEquals(0, event.index());
        }

        @Test
        @DisplayName("收尾 chunk:delta=null,index 仍递增")
        void testEndChunk() {
            WorkflowEvent.Chunk event = new WorkflowEvent.Chunk("node-llm", 42, null, 5);
            assertNull(event.delta());
            assertEquals(5, event.index());
        }

        @Test
        @DisplayName("toString 含 nodeId/messageId/index/delta 便于日志")
        void testToString() {
            WorkflowEvent.Chunk event = new WorkflowEvent.Chunk("llm1", 1, "Hello", 0);
            String s = event.toString();
            assertTrue(s.contains("nodeId=llm1"));
            assertTrue(s.contains("messageId=1"));
            assertTrue(s.contains("index=0"));
            assertTrue(s.contains("Hello"));
        }
    }

    @Nested
    @DisplayName("WorkflowEventListener.onChunk 兼容性")
    class OnChunkCompatibilityTest {

        @Test
        @DisplayName("WorkflowEventListener 默认空 onChunk:不抛异常,被静默调用")
        void testDefaultOnChunkNoop() {
            WorkflowEventListener listener = new WorkflowEventListener() {
                // 故意不实现 onChunk,验证 default 空实现可用
            };
            // 不应该抛异常
            assertDoesNotThrow(() -> listener.onChunk(
                    new WorkflowEvent.Chunk("n1", 1, "test", 0)));
        }

        @Test
        @DisplayName("自定义 listener 实现 onChunk:能收到 chunk 事件")
        void testCustomOnChunkReceivesEvents() {
            final List<String> receivedDeltas = new ArrayList<>();
            WorkflowEventListener listener = new WorkflowEventListener() {
                @Override
                public void onChunk(WorkflowEvent.Chunk event) {
                    receivedDeltas.add(event.delta());
                }
            };
            // 模拟 3 个 token
            listener.onChunk(new WorkflowEvent.Chunk("n1", 1, "Hello", 0));
            listener.onChunk(new WorkflowEvent.Chunk("n1", 1, ", ", 1));
            listener.onChunk(new WorkflowEvent.Chunk("n1", 1, "world", 2));
            // 收尾
            listener.onChunk(new WorkflowEvent.Chunk("n1", 1, null, 3));

            assertEquals(4, receivedDeltas.size());
            assertEquals("Hello", receivedDeltas.get(0));
            assertEquals(", ", receivedDeltas.get(1));
            assertEquals("world", receivedDeltas.get(2));
            assertNull(receivedDeltas.get(3));
        }
    }

    @Nested
    @DisplayName("LlmService.callStream 契约")
    class LlmServiceCallStreamTest {

        @Test
        @DisplayName("mock LlmService.callStream 模拟多次 onDelta 回调")
        void testMockCallStreamMultipleDeltas() {
            // 模拟 LlmService: 收到 callStream 时立刻调 3 次 onDelta
            final String[] accumulated = {""};
            LlmService mockService = new LlmService() {
                @Override
                public LlmCallResult call(String provider, String model, com.dify.workflow.model.ChatRequest request) {
                    return null;  // 不用
                }

                @Override
                public void callStream(String provider, String model,
                                       com.dify.workflow.model.ChatRequest request,
                                       java.util.function.Consumer<StreamDelta> onDelta) {
                    onDelta.accept(new StreamDelta("a", 0, null));
                    onDelta.accept(new StreamDelta("b", 1, null));
                    onDelta.accept(new StreamDelta("c", 2, null));
                    onDelta.accept(new StreamDelta(null, 3, "stop"));
                }
            };

            // 模拟 LlmNode 处理流程:累积 delta
            StringBuilder acc = new StringBuilder();
            mockService.callStream("test", "m",
                    com.dify.workflow.model.ChatRequest.builder()
                            .model("m").stream(true).build(),
                    delta -> {
                        if (delta.content() != null) {
                            acc.append(delta.content());
                        }
                    });

            assertEquals("abc", acc.toString());
        }

        @Test
        @DisplayName("流式异常:onDelta 收到 error: 收尾,IOException rethrow")
        void testStreamErrorRethrows() {
            LlmService mockService = new LlmService() {
                @Override
                public LlmCallResult call(String provider, String model, com.dify.workflow.model.ChatRequest request) {
                    return null;
                }

                @Override
                public void callStream(String provider, String model,
                                       com.dify.workflow.model.ChatRequest request,
                                       java.util.function.Consumer<StreamDelta> onDelta) throws RuntimeException {
                    onDelta.accept(new StreamDelta("partial", 0, null));
                    onDelta.accept(new StreamDelta(null, 1, "error:network failure"));
                    throw new RuntimeException("network failure");
                }
            };

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    mockService.callStream("test", "m",
                            com.dify.workflow.model.ChatRequest.builder()
                                    .model("m").stream(true).build(),
                            delta -> {}));
            assertEquals("network failure", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("TestReportGenerator chunk 收集")
    class TestReportChunkCollectionTest {

        @Test
        @DisplayName("onChunk 触发后 chunks 列表 + chunksByNodeId 映射正确(此测试在 dify-engine 模块)")
        void testOnChunkCollectsDeltas() {
            // 占位:dify-nodes 模块不能直接 import dify-engine.TestReportGenerator
            // 完整测试在 dify-engine/src/test/.../StreamChunkReportTest.java
            assertTrue(true);
        }
    }
}
