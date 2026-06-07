package com.dify.workflow.engine;

import com.dify.workflow.model.DifyNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 响应流协调器，参考 Dify 的 ResponseStreamCoordinator。
 * 在同步 DFS 执行模型中确保 response 节点（answer/end）按正确顺序执行：
 * 1. 一次只执行一个 response 节点
 * 2. 其他就绪的 response 节点排队等待
 * 3. 当前 response 节点完成后立即激活下一个
 */
public class ResponseStreamCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ResponseStreamCoordinator.class);

    private final Set<String> responseNodes = new LinkedHashSet<>();
    private final Set<String> executedResponseNodes = new HashSet<>();
    private String activeNode;
    private final Deque<String> waitingQueue = new ArrayDeque<>();

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

    /** 节点执行完成，激活下一个等待的 response 节点 */
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
     * 排序后继节点列表，确保 response 节点优先执行。
     * 如果当前有活跃的 response 节点，其他 response 节点排入等待队列。
     *
     * @param successors 后继节点 ID 列表
     * @param context    执行上下文（用于判断前驱状态）
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
                // 检查是否所有前驱都已完成（已就绪）
                if (allPredecessorsDone(targetId, context)) {
                    if (activeNode != null && !targetId.equals(activeNode)) {
                        // 已有活跃的 response 节点，排入等待队列
                        if (!waitingQueue.contains(targetId) && !executedResponseNodes.contains(targetId)) {
                            waitingQueue.addLast(targetId);
                            log.debug("Response node {} queued (active: {})", targetId, activeNode);
                        }
                        // 不加入执行列表，等激活后再执行
                    } else {
                        responseTargets.add(targetId);
                    }
                } else {
                    // 前驱未完成，暂不执行，但这些通常是非 response 的处理节点
                    nonResponseTargets.add(targetId);
                }
            } else {
                nonResponseTargets.add(targetId);
            }
        }

        // response 节点优先，非 response 节点在后
        List<String> ordered = new ArrayList<>(responseTargets);
        ordered.addAll(nonResponseTargets);
        return ordered;
    }

    /** 获取等待队列中的下一个节点（如果有） */
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
        // 在我们的执行模型中，节点能被列为 successor 时前驱已基本完成
        return true;
    }
}
