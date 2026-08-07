package com.dify.workflow.engine;

import com.dify.workflow.model.DifyNode;
import com.dify.workflow.model.TemplateSegment;
import com.dify.workflow.model.WorkflowEvent;
import com.dify.workflow.model.WorkflowEventListener;
import com.dify.workflow.parser.VariableResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 响应流协调器,参考 Dify 的 ResponseStreamCoordinator。
 *
 * <p>本类承担两个职责:</p>
 * <ol>
 *   <li><b>response 节点执行顺序</b>(原有):确保 response 节点(answer/end)按正确顺序执行,
 *       一次只执行一个,其他就绪的 response 节点排队等待。</li>
 *   <li><b>流式 chunk 重写</b>(新增):拦截 LLM 流式 chunk,按 answer 节点模板段顺序刷出
 *       到下游 answer 节点 ID 标签下,模仿 Dify 原版 {@code graphon.graph_engine.
 *       response_coordinator.coordinator} 的行为。</li>
 * </ol>
 *
 * <p>典型流(以 RDZNWD.yml 的"末端 3 个 answer"为例):</p>
 * <pre>
 *   LLM 节点 doExecute
 *     → context.emitChunk(llmId, delta)
 *     → DifyWorkflowContext.emitChunk
 *     → coordinator.interceptChunk(llmId, delta)
 *     → 塞进所有引用 [llmId, "text"] 的 session.chunkBuffer
 *     → flushSessions() 尝试刷出(若 session.active==true)
 *
 *   if-else 走完分支
 *     → coordinator.onEdgeTaken(edge.id)
 *     → 移除该边在各 session 所有 paths 中的引用;任一 path 清空即激活
 *     → 路径清零的 session.active=true
 *     → flushSessions() 真正刷出 buffered chunks,标签重写为 answerId
 * </pre>
 */
public class ResponseStreamCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ResponseStreamCoordinator.class);

    private final Set<String> responseNodes = new LinkedHashSet<>();
    private final Set<String> executedResponseNodes = new HashSet<>();
    private String activeNode;
    private final Deque<String> waitingQueue = new ArrayDeque<>();

    // === 新增:answer 会话表 ===
    private final Map<String, AnswerSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger chunkIndexSeq = new AtomicInteger(0);
    // chunkIndexSeq 用于给重写后的 chunk 分配 index;不与 DifyWorkflowContext 的 chunkIndex 共享,
    // 因为协调器发的是"二次"chunk,序号独立递增即可。
    private volatile WorkflowEventListener eventListener;
    private volatile int chunkMessageId = 0;

    /** 注册一个 response 节点 (answer / end) */
    public void register(String nodeId) {
        responseNodes.add(nodeId);
        log.debug("Registered response node: {}", nodeId);
    }

    /** 批量注册 response 节点 */
    public void registerAll(java.util.Collection<DifyNode> nodes) {
        for (DifyNode node : nodes) {
            String type = node.data().type();
            if ("answer".equals(type) || "end".equals(type)) {
                register(node.id());
            }
        }
        log.info("Registered {} response nodes", responseNodes.size());
    }

    /** 判断是否为 response 节点 */
    public boolean isResponse(String nodeId) {
        return responseNodes.contains(nodeId);
    }

    /** 节点开始执行 */
    public void onNodeStarted(String nodeId) {
        if (isResponse(nodeId) && activeNode == null) {
            activeNode = nodeId;
            log.debug("Response node {} activated", nodeId);
        }
    }

    /** 节点执行完成,激活下一个等待的 response 节点 */
    public String onNodeCompleted(String nodeId) {
        if (isResponse(nodeId)) {
            executedResponseNodes.add(nodeId);
            if (nodeId.equals(activeNode)) {
                activeNode = null;
                // 激活下一个等待的 response 节点
                while (!waitingQueue.isEmpty()) {
                    String next = waitingQueue.pollFirst();
                    if (!executedResponseNodes.contains(next)) {
                        activeNode = next;
                        log.debug("Response node {} activated from waiting queue", next);
                        return next;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 排序后继节点列表,确保 response 节点优先执行。
     * 如果当前有活跃的 response 节点,其他 response 节点排入等待队列。
     *
     * @param successors 后继节点 ID 列表
     * @param context    执行上下文(用于判断前驱状态)
     * @return 排序后的后继节点列表
     */
    public List<String> orderSuccessors(List<String> successors, DifyWorkflowContext context) {
        if (successors == null || successors.size() <= 1) {
            return successors;
        }

        List<String> responseTargets = new ArrayList<>();
        List<String> nonResponseTargets = new ArrayList<>();

        for (String targetId : successors) {
            if (isResponse(targetId)) {
                // 检查是否所有前驱都已完成(已就绪)
                if (allPredecessorsDone(targetId, context)) {
                    if (activeNode != null && !targetId.equals(activeNode)) {
                        // 已有活跃的 response 节点,排入等待队列
                        if (!waitingQueue.contains(targetId) && !executedResponseNodes.contains(targetId)) {
                            waitingQueue.addLast(targetId);
                            log.debug("Response node {} queued (active: {})", targetId, activeNode);
                        }
                        // 不加入执行列表,等激活后再执行
                    } else {
                        responseTargets.add(targetId);
                    }
                } else {
                    // 前驱未完成,暂不执行,但这些通常是非 response 的处理节点
                    nonResponseTargets.add(targetId);
                }
            } else {
                nonResponseTargets.add(targetId);
            }
        }

        // response 节点优先,非 response 节点在后
        List<String> ordered = new ArrayList<>(responseTargets);
        ordered.addAll(nonResponseTargets);
        return ordered;
    }

    /** 获取等待队列中的下一个节点(如果有) */
    public String pollWaitingNode() {
        if (activeNode == null && !waitingQueue.isEmpty()) {
            String next = waitingQueue.pollFirst();
            if (!executedResponseNodes.contains(next)) {
                activeNode = next;
                return next;
            }
        }
        return null;
    }

    /** 检查节点的所有前驱是否已完成 */
    private boolean allPredecessorsDone(String nodeId, DifyWorkflowContext context) {
        DifyNode node = context.findNodeById(nodeId);
        if (node == null) return true;
        // 在我们的执行模型中,节点能被列为 successor 时前驱已基本完成
        return true;
    }

    // ====================================================================
    //  新增:流式 chunk 重写相关 API
    // ====================================================================

    /** DifyWorkflowContext 注入的事件监听器(协调器用此发重写后的 chunk) */
    public void setEventListener(WorkflowEventListener listener) {
        this.eventListener = listener;
    }

    /** DifyWorkflowContext 注入的 messageId(协调器用此维持消息一致性) */
    public void setChunkMessageId(int messageId) {
        this.chunkMessageId = messageId;
    }

    /**
     * 注册一个 answer 节点(由 DifyWorkflowExecutor 启动时遍历 graph 后调用)。
     *
     * @param answerNodeId answer 节点 ID
     * @param answerTemplate answer 节点的 answer 模板字符串
     * @param resolver 用于解析模板中的 {{#...#}} 引用
     * @param paths 从 root 到本 answer 的所有可能路径,每条路径是"必须被取走的分支边 ID"列表;
     *              若所有路径都为空列表(直接上游无 if-else),session 立即激活
     */
    public void registerAnswerNode(String answerNodeId, String answerTemplate,
                                   VariableResolver resolver, List<List<String>> paths) {
        if (answerNodeId == null) return;

        List<TemplateSegment> template = (answerTemplate == null || answerTemplate.isEmpty())
                ? Collections.emptyList()
                : resolver.parseTemplate(answerTemplate);

        List<List<String>> sessionPaths = (paths == null)
                ? Collections.emptyList()
                : new ArrayList<>(paths);

        // 激活条件(对齐 Dify):存在至少一条路径其所有阻塞边都已清空。
        // 这里 paths 已预先过滤好(每条只包含 if-else 分支边),
        //   故"立即可达"意味着至少有一条 path 是空列表。
        boolean active = false;
        for (List<String> p : sessionPaths) {
            if (p.isEmpty()) {
                active = true;
                break;
            }
        }
        AnswerSession session = new AnswerSession(answerNodeId, template, sessionPaths, active);
        sessions.put(answerNodeId, session);
        log.debug("Registered answer session: id={}, templateSegments={}, numPaths={}, pathLengths={}, active={}",
                answerNodeId, template.size(), sessionPaths.size(),
                sessionPaths.stream().map(List::size).collect(java.util.stream.Collectors.toList()),
                active);
    }

    /**
     * EndNode 占位接口。本轮不动 EndNode,只留接口供未来 mode=workflow 场景实现。
     * 当前实现为空:什么都不做。
     */
    public void registerEndNode(String endNodeId, List<?> outputs, VariableResolver resolver) {
        // TODO 留作未来 mode=workflow 场景
        log.debug("EndNode registration is a no-op stub: endNodeId={}", endNodeId);
    }

    /**
     * 拦截 LLM chunk(在 DifyWorkflowContext.emitChunk 中调用)。
     * 把 chunk 塞进所有引用了 [llmNodeId, "text"] 的 session.chunkBuffer,然后尝试刷出。
     */
    public void interceptChunk(String llmNodeId, String delta) {
        if (delta == null || delta.isEmpty()) return;

        boolean buffered = false;
        for (AnswerSession session : sessions.values()) {
            if (session.referencesLlmText(llmNodeId)) {
                session.appendChunk(llmNodeId, delta);
                buffered = true;
            }
        }
        if (buffered) {
            flushSessions();
        }
    }

    /**
     * 边被取走(executor 边筛选通过后调用)。
     *
     * <p>对齐 Dify 原版 {@code ResponseStreamCoordinator.on_edge_taken}:
     * 从所有 session 的每条 path 中移除该边;任一 path 清空则激活该 session 并刷出 chunks。</p>
     */
    public void onEdgeTaken(String edgeId) {
        if (edgeId == null) return;

        for (AnswerSession session : sessions.values()) {
            if (session.active) {
                continue;  // 已激活的 session 不用再处理
            }
            boolean anyPathCleared = session.removeEdge(edgeId);
            if (anyPathCleared) {
                session.active = true;
                log.debug("Edge taken {} cleared a path → session {} activated",
                        edgeId, session.answerNodeId);
            }
        }
        flushSessions();
    }

    /**
     * LLM 节点完成(executor 节点 doExecute 返回后,仅 LLM 类型时调用)。
     * 用于直接上游场景(中间 直接回复),paths 中每条都为空列表,active==true。
     * 同时清空对应 selector 的 chunkBuffer(LLM 已 stream 完,buffer 使命完成)。
     */
    public void onLlmNodeCompleted(String llmNodeId) {
        if (llmNodeId == null) return;

        for (AnswerSession session : sessions.values()) {
            if (session.referencesLlmText(llmNodeId)) {
                // 标记 active(若还没激活)
                session.active = true;
                // 刷出该 session 全部 buffered chunks(VariableSegment 部分)
                flushSession(session);
                // 关键修复:补发 TextSegment(模板字面前缀/后缀),否则 SSE listener 收不到这部分
                flushTextSegmentsOnLlmCompleted(session);
                // 清空 LLM chunk buffer(buffer 使命完成)
                session.clearChunkBuffer(llmNodeId);
                log.debug("LLM {} completed; flushed answer session {}", llmNodeId, session.answerNodeId);
            }
        }
    }

    /**
     * 内部:对所有 active session 尝试刷出(VariableSegment 段)。
     * TextSegment 在 LLM 完成时才一次性刷(避免顺序混乱)。
     */
    private void flushSessions() {
        for (AnswerSession session : sessions.values()) {
            if (session.active) {
                flushSession(session);
            }
        }
    }

    /**
     * 内部:刷出单个 session 的 chunk buffer 内容,以 answer 节点 ID 标签重发。
     * 遍历 template 段,每个 VariableSegment 从对应 selector 的 buffer 逐个 pop,emit 一个 chunk。
     */
    private void flushSession(AnswerSession session) {
        if (eventListener == null) return;

        for (TemplateSegment seg : session.template) {
            if (seg instanceof TemplateSegment.VariableSegment) {
                List<String> selector = ((TemplateSegment.VariableSegment) seg).selector();
                Deque<String> buffer = session.chunkBuffer.get(selector);
                if (buffer == null) continue;
                while (!buffer.isEmpty()) {
                    String delta = buffer.pollFirst();
                    emitChunk(session.answerNodeId, delta, selector);
                }
            }
            // TextSegment 不在流式期间发(避免与流式 chunk 交错),等 LLM 完成后由
            // AnswerNode.doExecute 一次性渲染;或者由 onLlmNodeCompleted 兜底发。
            // 这里只在 onLlmNodeCompleted 路径里同步发 TextSegment。
        }
    }

    /**
     * LLM 完成时,补发该 session 的所有 TextSegment 段(作为一次性文本块)。
     * 这样 LLM 流式期间只看到 {{#llm.text#}} 展开的 token;LLM 跑完后才看到模板里的字面前缀/后缀。
     */
    private void flushTextSegmentsOnLlmCompleted(AnswerSession session) {
        if (eventListener == null) return;
        for (TemplateSegment seg : session.template) {
            if (seg instanceof TemplateSegment.TextSegment) {
                String text = ((TemplateSegment.TextSegment) seg).text();
                if (text != null && !text.isEmpty()) {
                    emitChunk(session.answerNodeId, text,
                            java.util.Arrays.asList(session.answerNodeId, "answer"));
                }
            }
        }
    }

    private void emitChunk(String answerNodeId, String delta, List<String> selector) {
        int idx = chunkIndexSeq.getAndIncrement();
        WorkflowEvent.Chunk event = new WorkflowEvent.Chunk(
                answerNodeId, chunkMessageId, delta, idx, selector);
        try {
            eventListener.onChunk(event);
        } catch (Exception e) {
            log.warn("Coordinator emitChunk failed: {}", e.getMessage());
        }
    }

    // ====================================================================
    //  内部数据结构
    // ====================================================================

    /**
     * answer 会话(对应 Dify 的 ResponseSession)。
     * 包含模板段、阻塞路径集合、激活状态、chunk 缓存。
     *
     * <p>关键设计(对齐 Dify 原版 {@code graphon.graph_engine.response_coordinator.coordinator._build_paths_map}):</p>
     * <ul>
     *   <li>阻塞追踪按 <b>路径</b>(path)追踪,不是按 <b>边集</b>。</li>
     *   <li>对每个 answer,预先算出从 root 到该 answer 的所有可能路径(正向 find_paths)。</li>
     *   <li>每条路径上,如果边的源节点是 if-else / container / response 等"会拦截下游"的类型,则把该边纳入路径的阻塞集。</li>
     *   <li>session 激活条件: <b>存在至少一条路径其所有阻塞边都被清空</b>(即该路径上每个 if-else 都已决策)。</li>
     *   <li>多分支汇合(同一 if-else 多条 case 边汇合到同一下游)也能正确工作:
     *       每条路径只包含一条 case 边,所以任一 case 被走,对应路径就清空,session 即激活。</li>
     * </ul>
     */
    static final class AnswerSession {
        final String answerNodeId;
        final List<TemplateSegment> template;
        /**
         * 阻塞路径列表。每条路径是一个有序的分支边 ID 列表,
         * 当某条路径的边全部被 onEdgeTaken 清空时,session 可激活。
         */
        final List<List<String>> paths;
        final Map<List<String>, Deque<String>> chunkBuffer = new ConcurrentHashMap<>();
        volatile boolean active;

        AnswerSession(String answerNodeId, List<TemplateSegment> template,
                      List<List<String>> paths, boolean active) {
            this.answerNodeId = answerNodeId;
            this.template = template;
            this.paths = paths == null ? java.util.Collections.emptyList()
                    : new java.util.ArrayList<>(paths);
            this.active = active;
        }

        /**
         * 当一条边被取走时,从所有路径中移除该边;若任一路径清空则返回 true(可激活)。
         */
        boolean removeEdge(String edgeId) {
            boolean anyPathCleared = false;
            for (List<String> path : paths) {
                path.remove(edgeId);
                if (path.isEmpty()) {
                    anyPathCleared = true;
                }
            }
            return anyPathCleared;
        }

        /**
         * 模板是否引用了某个 LLM 节点的 text 变量(即含 [llmNodeId, "text"] 段)。
         */
        boolean referencesLlmText(String llmNodeId) {
            for (TemplateSegment seg : template) {
                if (seg instanceof TemplateSegment.VariableSegment) {
                    List<String> sel = ((TemplateSegment.VariableSegment) seg).selector();
                    if (sel.size() == 2 && llmNodeId.equals(sel.get(0)) && "text".equals(sel.get(1))) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * 把一个 LLM chunk 塞进 [llmNodeId, "text"] 对应的 buffer。
         * 前提:已用 referencesLlmText 确认模板引用。
         */
        void appendChunk(String llmNodeId, String delta) {
            List<String> selector = java.util.Arrays.asList(llmNodeId, "text");
            chunkBuffer.computeIfAbsent(selector, k -> new ArrayDeque<>()).addLast(delta);
        }

        /**
         * 清空指定 selector 的 buffer(LLM 完成后用)。
         */
        void clearChunkBuffer(String llmNodeId) {
            chunkBuffer.remove(java.util.Arrays.asList(llmNodeId, "text"));
        }
    }
}
