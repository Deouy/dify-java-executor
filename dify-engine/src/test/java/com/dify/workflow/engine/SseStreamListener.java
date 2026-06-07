package com.dify.workflow.engine;

import com.dify.workflow.model.WorkflowEvent;
import com.dify.workflow.model.WorkflowEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流式 chunk 事件收集器 (SSE 风格测试用 listener)。
 *
 * <p>与 TestReportGenerator 风格不同:
 * <ul>
 *   <li>TestReportGenerator 是"边执行边写报告字符串"(日志式)</li>
 *   <li>SseStreamListener 是"收集结构化数据供测试断言"</li>
 * </ul>
 *
 * <p>每个 LLM/Agent 节点触发 onChunk 时,这里收集:
 * <ul>
 *   <li>delta 文本(收尾 chunk 用 "&lt;END&gt;" 占位)</li>
 *   <li>chunk 索引(用于校验单调递增)</li>
 *   <li>按触发节点 ID 分组(便于多 LLM 节点场景断言)</li>
 * </ul>
 */
public class SseStreamListener implements WorkflowEventListener {

    private final List<String> chunks = new ArrayList<>();
    private final List<Integer> indexes = new ArrayList<>();
    private final Map<String, List<String>> chunksByNodeId = new LinkedHashMap<>();

    @Override
    public void onChunk(WorkflowEvent.Chunk event) {
        // 收尾 chunk(delta=null)用 "<END>" 占位,方便阅读且与正常 chunk 区分
        String delta = event.delta() == null ? "<END>" : event.delta();
        System.out.println(delta);
        chunks.add(delta);
        indexes.add(event.index());
        chunksByNodeId.computeIfAbsent(event.nodeId(), k -> new ArrayList<>()).add(delta);
    }

    /**
     * 拼接累积内容(去掉收尾的 "&lt;END&gt;" 标记)。
     * 关键断言:应等于 LLM 节点最终 answer。
     */
    public String accumulated() {
        StringBuilder sb = new StringBuilder();
        for (String c : chunks) {
            if (!"<END>".equals(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public List<String> getChunks() {
        return Collections.unmodifiableList(chunks);
    }

    public List<Integer> getIndexes() {
        return Collections.unmodifiableList(indexes);
    }

    public Map<String, List<String>> getChunksByNodeId() {
        return Collections.unmodifiableMap(chunksByNodeId);
    }

    public int size() {
        return chunks.size();
    }
}
