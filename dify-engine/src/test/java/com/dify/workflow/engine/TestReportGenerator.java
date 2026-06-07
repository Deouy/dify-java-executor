package com.dify.workflow.engine;

import com.dify.workflow.model.Java8Compat;
import com.dify.workflow.model.WorkflowEvent;
import com.dify.workflow.model.WorkflowEventListener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 测试报告生成器。
 * 监听工作流执行事件，生成类似 test-output.log 的详细节点级日志。
 */
public class TestReportGenerator implements WorkflowEventListener {

    private final String testName;
    private final Map<String, Object> inputs;
    private final StringBuilder report = new StringBuilder();
    private final List<NodeRecord> nodeRecords = new ArrayList<>();
    private final List<String> eventLog = new ArrayList<>();
    // 流式输出 chunk 记录(2026-06-03 增)
    private final List<String> chunks = new ArrayList<>();
    private final Map<String, List<String>> chunksByNodeId = new LinkedHashMap<>();
    private int chunkIndex = 0;

    private String workflowName;
    private String workflowStatus;
    private Map<String, Object> workflowOutputs;
    private long workflowStartTime;
    private long workflowEndTime;

    public TestReportGenerator(String testName, Map<String, Object> inputs) {
        this.testName = testName;
        this.inputs = inputs != null ? inputs : Java8Compat.mapOf();
    }

    // ==================== WorkflowEventListener 实现 ====================

    @Override
    public void onWorkflowStarted(WorkflowEvent.Started event) {
        this.workflowName = event.workflowName();
        this.workflowStartTime = event.startTime();
        eventLog.add(">>> WORKFLOW_STARTED: " + workflowName + ", inputs=" + formatMap(event.inputs()));
    }

    @Override
    public void onWorkflowSucceeded(WorkflowEvent.Succeeded event) {
        this.workflowStatus = "succeeded";
        this.workflowOutputs = event.outputs();
        this.workflowEndTime = event.endTime();
        long elapsed = event.elapsedMs();
        eventLog.add(">>> WORKFLOW_SUCCEEDED: outputs=" + formatMap(event.outputs()) + ", elapsed=" + elapsed + "ms");
    }

    @Override
    public void onWorkflowFailed(WorkflowEvent.Failed event) {
        this.workflowStatus = "failed";
        this.workflowEndTime = event.endTime();
        eventLog.add(">>> WORKFLOW_FAILED: error=" + event.error() + ", elapsed=" + event.elapsedMs() + "ms");
    }

    @Override
    public void onNodeStarted(WorkflowEvent.NodeStarted event) {
        eventLog.add("  >>> NODE_STARTED: " + event.nodeId() + " (" + event.nodeType() + ")");
    }

    @Override
    public void onNodeSucceeded(WorkflowEvent.NodeSucceeded event) {
        eventLog.add("  >>> NODE_SUCCEEDED: " + event.nodeId() + " (" + event.nodeType()
                + ") outputs=" + formatMap(event.outputs()) + " elapsed=" + event.elapsedMs() + "ms");

        NodeRecord record = new NodeRecord();
        record.nodeId = event.nodeId();
        record.nodeType = event.nodeType();
        record.nodeTitle = event.nodeTitle();
        record.inputs = event.inputs();
        record.outputs = event.outputs();
        record.elapsedMs = event.elapsedMs();
        record.status = "FINISH";
        record.inIterationId = event.inIterationId();
        record.inLoopId = event.inLoopId();
        nodeRecords.add(record);
    }

    @Override
    public void onNodeFailed(WorkflowEvent.NodeFailed event) {
        eventLog.add("  >>> NODE_FAILED: " + event.nodeId() + " (" + event.nodeType()
                + ") error=" + event.error() + " elapsed=" + event.elapsedMs() + "ms");

        NodeRecord record = new NodeRecord();
        record.nodeId = event.nodeId();
        record.nodeType = event.nodeType();
        record.nodeTitle = event.nodeTitle();
        record.inputs = event.inputs();
        record.error = event.error();
        record.elapsedMs = event.elapsedMs();
        record.status = "ERROR";
        record.inIterationId = event.inIterationId();
        record.inLoopId = event.inLoopId();
        nodeRecords.add(record);
    }

    @Override
    public void onIterationStarted(WorkflowEvent.IterationStarted event) {
        eventLog.add("  >>> ITERATION_STARTED: " + event.nodeId() + " (" + event.nodeType()
                + ") totalSteps=" + event.totalSteps());
    }

    @Override
    public void onIterationNext(WorkflowEvent.IterationNext event) {
        eventLog.add("  >>> ITERATION_NEXT: " + event.nodeId() + " index=" + event.index()
                + " output=" + formatMap(event.output()));
    }

    @Override
    public void onIterationCompleted(WorkflowEvent.IterationCompleted event) {
        eventLog.add("  >>> ITERATION_COMPLETED: " + event.nodeId() + " (" + event.nodeType()
                + ") steps=" + event.steps() + " elapsed=" + (event.endTime() - event.startTime()) + "ms");
    }

    @Override
    public void onLoopStarted(WorkflowEvent.LoopStarted event) {
        eventLog.add("  >>> LOOP_STARTED: " + event.nodeId() + " (" + event.nodeType()
                + ") totalSteps=" + event.totalSteps());
    }

    @Override
    public void onLoopNext(WorkflowEvent.LoopNext event) {
        eventLog.add("  >>> LOOP_NEXT: " + event.nodeId() + " index=" + event.index()
                + " output=" + formatMap(event.output()));
    }

    @Override
    public void onLoopCompleted(WorkflowEvent.LoopCompleted event) {
        eventLog.add("  >>> LOOP_COMPLETED: " + event.nodeId() + " (" + event.nodeType()
                + ") steps=" + event.steps() + " elapsed=" + (event.endTime() - event.startTime()) + "ms");
    }

    /**
     * 流式输出 chunk 事件(2026-06-03 增)。
     * 记录 delta 到 chunks 列表(全局)+ chunksByNodeId 映射(按节点),
     * 同时追加到 eventLog 便于和节点事件交叉对比。
     */
    @Override
    public void onChunk(WorkflowEvent.Chunk event) {
        String delta = event.delta() == null ? "<END>" : event.delta();
        chunks.add(delta);
        chunksByNodeId.computeIfAbsent(event.nodeId(), k -> new ArrayList<>()).add(delta);
        eventLog.add("  >>> CHUNK[" + chunkIndex + "][" + event.nodeId() + "]: " + delta);
        chunkIndex++;
    }

    /** 暴露 chunks 列表,供测试断言使用 */
    public List<String> getChunks() { return Collections.unmodifiableList(chunks); }
    public Map<String, List<String>> getChunksByNodeId() { return Collections.unmodifiableMap(chunksByNodeId); }

    // ==================== 报告生成 ====================

    /**
     * 生成完整报告字符串。
     */
    public String generateReport() {
        report.setLength(0);
        report.append("============================================================\n");
        report.append("【测试】").append(testName).append("\n");
        report.append("参数: ").append(formatMap(inputs)).append("\n\n");

        // 事件日志
        for (String event : eventLog) {
            report.append(event).append("\n");
        }

        // 节点执行明细
        report.append("\n节点执行明细:\n");
        int idx = 1;
        for (NodeRecord record : nodeRecords) {
            // 跳过子节点（iteration-start, loop-start 等内部节点已在事件日志中显示）
            if (record.nodeId != null && (record.nodeId.endsWith("start") || record.nodeId.contains("-"))) {
                // 迭代/循环内部节点不在明细中重复显示
                if (record.inIterationId != null || record.inLoopId != null) {
                    continue;
                }
            }
            report.append("  [").append(idx).append("] 节点ID: ").append(record.nodeId).append("\n");
            report.append("      类型: ").append(record.nodeType).append("\n");
            report.append("      状态: ").append(record.status).append("\n");
            report.append("      耗时: ").append(record.elapsedMs).append("ms\n");
            if (record.error != null) {
                report.append("      错误: ").append(record.error).append("\n");
            }
            if (record.outputs != null && !record.outputs.isEmpty()) {
                report.append("      输出: ").append(formatMap(record.outputs)).append("\n");
            }
            idx++;
        }

        // 最终输出
        report.append("\n最终输出:\n");
        if (workflowOutputs != null) {
            for (Map.Entry<String, Object> entry : workflowOutputs.entrySet()) {
                Object value = entry.getValue();
                String type = value != null ? value.getClass().getSimpleName() : "null";
                report.append("  ").append(entry.getKey()).append(" = ")
                        .append(value).append(" (类型: ").append(type).append(")\n");
            }
        }

        // 流式输出 chunks 序列(2026-06-03 增)
        if (!chunks.isEmpty()) {
            report.append("\n流式 chunks 序列 (共 ").append(chunks.size()).append(" 个):\n");
            for (Map.Entry<String, List<String>> entry : chunksByNodeId.entrySet()) {
                report.append("  [").append(entry.getKey()).append("] (").append(entry.getValue().size()).append(" chunks):\n");
                for (int i = 0; i < entry.getValue().size(); i++) {
                    String c = entry.getValue().get(i);
                    report.append("    [").append(i).append("] ").append(c).append("\n");
                }
            }
        }

        // 测试结果
        report.append("\n测试结果: ");
        if ("succeeded".equals(workflowStatus)) {
            report.append("✅ 通过\n");
        } else if ("failed".equals(workflowStatus)) {
            report.append("❌ 失败\n");
        } else {
            report.append("⚠️ 未知\n");
        }

        report.append("============================================================\n\n");
        return report.toString();
    }

    /**
     * 将报告写入文件。
     */
    public void writeToFile(Path filePath) throws Exception {
        String content = generateReport();
        Java8Compat.writeString(filePath, content);
    }

    /**
     * 将报告追加到现有文件。
     */
    public void appendToFile(Path filePath) throws Exception {
        String content = generateReport();
        Java8Compat.writeString(filePath, content, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    public String getWorkflowStatus() {
        return workflowStatus;
    }

    public Map<String, Object> getWorkflowOutputs() {
        return workflowOutputs;
    }

    // ==================== 工具方法 ====================

    private String formatMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(entry.getKey()).append("=").append(truncate(String.valueOf(entry.getValue()), 200));
        }
        sb.append("}");
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    // ==================== 内部类 ====================

    private static class NodeRecord {
        String nodeId;
        String nodeType;
        String nodeTitle;
        Map<String, Object> inputs;
        Map<String, Object> outputs;
        String error;
        long elapsedMs;
        String status;
        String inIterationId;
        String inLoopId;
    }

    // ==================== 静态工具方法 ====================

    /**
     * 创建报告生成器（便捷方法）。
     */
    public static TestReportGenerator create(String testName, Map<String, Object> inputs) {
        return new TestReportGenerator(testName, inputs);
    }

    /**
     * 生成单行测试摘要。
     */
    public static String summarize(String testName, String status, long elapsedMs) {
        return String.format("[%s] %s - %s (%dms)", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), testName, status, elapsedMs);
    }
}
