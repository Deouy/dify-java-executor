package com.dify.workflow.model;

/**
 * 工作流执行事件监听器。
 * 用户实现此接口以接收工作流执行过程中的各类事件，
 * 可用于日志记录、监控、调试等场景。
 * 参考 Dify 的 GraphEngineEvent → QueueEvent → SSE Stream Response 流水线。
 */
public interface WorkflowEventListener {

    default void onWorkflowStarted(WorkflowEvent.Started event) {}

    default void onWorkflowSucceeded(WorkflowEvent.Succeeded event) {}

    default void onWorkflowFailed(WorkflowEvent.Failed event) {}

    default void onNodeStarted(WorkflowEvent.NodeStarted event) {}

    default void onNodeSucceeded(WorkflowEvent.NodeSucceeded event) {}

    default void onNodeFailed(WorkflowEvent.NodeFailed event) {}

    default void onIterationStarted(WorkflowEvent.IterationStarted event) {}

    default void onIterationNext(WorkflowEvent.IterationNext event) {}

    default void onIterationCompleted(WorkflowEvent.IterationCompleted event) {}

    default void onLoopStarted(WorkflowEvent.LoopStarted event) {}

    default void onLoopNext(WorkflowEvent.LoopNext event) {}

    default void onLoopCompleted(WorkflowEvent.LoopCompleted event) {}

    /**
     * 流式输出 chunk 事件 — LLM/Agent 节点每收到一个 token 触发一次。
     * 默认空实现,既有 listener 不需改动即可继续工作。
     * 流结束时,可能触发一条 delta=null,index 递增的 chunk 作为收尾信号。
     */
    default void onChunk(WorkflowEvent.Chunk event) {}
}
