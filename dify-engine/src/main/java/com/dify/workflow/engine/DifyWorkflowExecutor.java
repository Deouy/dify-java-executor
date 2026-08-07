package com.dify.workflow.engine;

import com.dify.workflow.model.DifyDslModel;
import com.dify.workflow.model.DifyEdge;
import com.dify.workflow.model.DifyGraph;
import com.dify.workflow.model.DifyNode;
import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.DifyVariable;
import com.dify.workflow.model.DifyEnvironmentVariable;
import com.dify.workflow.model.DifyConversationVariable;
import com.dify.workflow.model.DifyWorkflow;
import com.dify.workflow.model.Java8Compat;
import com.dify.workflow.model.WorkflowEvent;
import com.dify.workflow.model.WorkflowEventListener;
import com.dify.workflow.model.node.NodeType;
import com.dify.workflow.engine.llm.LlmProviderFactory;
import com.dify.workflow.parser.DifyParseException;
import com.dify.workflow.parser.DifyYamlParser;
import com.dify.workflow.parser.VariableResolver;
import com.google.common.graph.ElementOrder;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.dify.workflow.nodes.StartNode;
import com.dify.workflow.nodes.EndNode;
import com.dify.workflow.nodes.AnswerNode;
import com.dify.workflow.nodes.LlmNode;
import com.dify.workflow.nodes.CodeNode;
import com.dify.workflow.nodes.HttpRequestNode;
import com.dify.workflow.nodes.IfElseNode;
import com.dify.workflow.nodes.TemplateTransformNode;
import com.dify.workflow.nodes.VariableAssignerNode;
import com.dify.workflow.nodes.ParameterExtractorNode;
import com.dify.workflow.nodes.QuestionClassifierNode;
import com.dify.workflow.nodes.ToolNode;
import com.dify.workflow.nodes.IterationNode;
import com.dify.workflow.nodes.IterationStartNode;
import com.dify.workflow.nodes.AgentNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dify workflow executor.
 *
 * Executes a Dify workflow defined in YAML format.
 */
public class DifyWorkflowExecutor {

    private static final Logger log = LoggerFactory.getLogger(DifyWorkflowExecutor.class);

    private final DifyDslModel dslModel;
    private final DifyYamlParser parser;
    private final VariableResolver variableResolver;
    private final LlmProviderFactory llmProviderFactory;
    private final Map<String, List<DifyEdge>> sourceEdgesMap = new ConcurrentHashMap<>();
    private final Map<String, List<DifyEdge>> targetEdgesMap = new ConcurrentHashMap<>();
    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final WorkflowEventListener eventListener;
    private final ResponseStreamCoordinator responseCoordinator = new ResponseStreamCoordinator();
    private final com.dify.workflow.model.ConversationHistoryStore historyStore;
    private final String conversationId;
    private final Map<String, String> subWorkflows;
    private final Map<String, com.dify.workflow.model.McpServerConfig> mcpConfigs;

    private DifyWorkflowExecutor(Builder builder) {
        this.dslModel = builder.dslModel;
        this.llmProviderFactory = builder.llmProviderFactory;
        this.eventListener = builder.eventListener;
        this.historyStore = builder.historyStore;
        this.conversationId = builder.conversationId;
        this.subWorkflows = builder.buildSubWorkflowsMap();
        this.mcpConfigs = builder.mcpConfigs != null ? builder.mcpConfigs : Java8Compat.mapOf();
        this.parser = new DifyYamlParser();
        this.variableResolver = new VariableResolver();
        buildEdgeMaps();
    }

    private void buildEdgeMaps() {
        DifyGraph graph = dslModel.workflow().graph();
        if (graph.edges() != null) {
            for (DifyEdge edge : graph.edges()) {
                sourceEdgesMap.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge);
                targetEdgesMap.computeIfAbsent(edge.target(), k -> new ArrayList<>()).add(edge);
            }
        }
    }

    /**
     * 计算从 start (root) 到 answer 的所有完整路径,每条路径单独收集 if-else 分支边。
     *
     * <p>对齐 Dify 原版 {@code graphon.graph_engine.response_coordinator.coordinator._build_paths_map}:</p>
     * <ul>
     *   <li><b>正向</b> find_paths(root → answer) 枚举所有完整路径(每条路径上每条边只出现一次,
     *       多分支汇合的拓扑会产出多条路径)。</li>
     *   <li>对每条路径,过滤:边的源节点是 if-else / container / response 等"分支/拦截"类型时,
     *       把该边保留到路径的阻塞集中。</li>
     *   <li>返回的 list 中,每个 inner list 表示一条路径;运行中只要任一路径的所有边都被取走,
     *       该 answer 的流式 chunk 即开始发出(对齐 Dify 的"任一路径清空即激活")。</li>
     * </ul>
     *
     * @param answerNodeId answer 节点 ID
     * @return 从 start 到 answer 的所有路径,每条路径包含"必须被取走的分支边 ID"列表
     */
    private List<List<String>> computePathEdges(String answerNodeId) {
        List<List<String>> allPaths = new ArrayList<>();
        if (answerNodeId == null) return allPaths;

        DifyNode startNode = dslModel.workflow().graph().nodes().stream()
                .filter(n -> "start".equals(n.data() != null ? n.data().type() : null))
                .findFirst().orElse(null);
        if (startNode == null) {
            log.warn("No start node found; cannot compute paths for answer {}", answerNodeId);
            return allPaths;
        }

        // 正向 find_paths:枚举所有从 start 到 answer 的完整路径
        findAllPaths(startNode.id(), answerNodeId, new ArrayList<>(), new HashSet<>(), allPaths);

        // 对每条路径,过滤出"分支/拦截"边
        List<List<String>> filteredPaths = new ArrayList<>();
        for (List<String> path : allPaths) {
            List<String> blocking = new ArrayList<>();
            for (String edgeId : path) {
                DifyEdge edge = findEdgeById(edgeId);
                if (edge == null) continue;
                DifyNode sourceNode = dslModel.workflow().graph().nodes().stream()
                        .filter(n -> edge.source().equals(n.id()))
                        .findFirst().orElse(null);
                if (sourceNode != null && sourceNode.data() != null
                        && ("if-else".equals(sourceNode.data().type())
                            || "container".equals(sourceNode.data().type()))) {
                    blocking.add(edgeId);
                }
            }
            filteredPaths.add(blocking);
        }
        return filteredPaths;
    }

    /** 正向 DFS 枚举所有从 currentNodeId 到 targetNodeId 的完整路径(边 ID 序列) */
    private void findAllPaths(String currentNodeId, String targetNodeId,
                              List<String> currentPath, Set<String> visitedNodes,
                              List<List<String>> result) {
        if (currentNodeId.equals(targetNodeId)) {
            result.add(new ArrayList<>(currentPath));
            return;
        }
        visitedNodes.add(currentNodeId);
        List<DifyEdge> outgoing = sourceEdgesMap.get(currentNodeId);
        if (outgoing != null) {
            for (DifyEdge edge : outgoing) {
                String next = edge.target();
                if (next == null || visitedNodes.contains(next)) continue;
                if (edge.id() != null) {
                    currentPath.add(edge.id());
                }
                findAllPaths(next, targetNodeId, currentPath, visitedNodes, result);
                if (!currentPath.isEmpty()) {
                    currentPath.remove(currentPath.size() - 1);
                }
            }
        }
        visitedNodes.remove(currentNodeId);
    }

    /** 按 edge.id() 查找 edge */
    private DifyEdge findEdgeById(String edgeId) {
        if (edgeId == null) return null;
        for (List<DifyEdge> edges : sourceEdgesMap.values()) {
            for (DifyEdge edge : edges) {
                if (edgeId.equals(edge.id())) {
                    return edge;
                }
            }
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create executor from YAML string.
     */
    public static DifyWorkflowExecutor fromYaml(String yaml) {
        DifyYamlParser parser = new DifyYamlParser();
        DifyDslModel model = parser.parse(yaml);
        return builder().dslModel(model).build();
    }

    /**
     * Create executor from YAML file.
     */
    public static DifyWorkflowExecutor fromYamlFile(Path filePath) {
        DifyYamlParser parser = new DifyYamlParser();
        DifyDslModel model = parser.parse(filePath);
        return builder().dslModel(model).build();
    }

    /**
     * Execute the workflow with the given inputs.
     *
     * @param inputs Input parameters
     * @return Workflow execution result
     */
    public DifyWorkflowResult execute(Map<String, Object> inputs) {
        log.info("Starting workflow execution");

        DifyWorkflowContext context = new DifyWorkflowContext(dslModel, inputs, llmProviderFactory);
        // 注入子图执行器，供 iteration/loop 节点使用
        context.setSubGraphRunner(this::executeSubGraph);
        // 注入 eventListener,供 LLM/Agent 流式 chunk 透传
        context.setEventListener(eventListener);
        // 注入 ResponseStreamCoordinator(关键修复:让 emitChunk 走协调器重写而非直发)
        context.setResponseCoordinator(responseCoordinator);
        responseCoordinator.setEventListener(eventListener);
        responseCoordinator.setChunkMessageId(context.getChunkMessageId());
        // 注入聊天历史存储和会话ID
        if (historyStore != null) {
            context.setHistoryStore(historyStore);
        }
        if (conversationId != null) {
            context.setConversationId(conversationId);
        }
        // 注入子工作流注册表，供 Agent 节点使用
        if (!subWorkflows.isEmpty()) {
            context.setSubWorkflows(subWorkflows);
        }
        // 注入 MCP Server 配置，供 Agent 的 MCP 策略使用
        if (!mcpConfigs.isEmpty()) {
            context.setMcpConfigs(mcpConfigs);
        }

        String workflowName = dslModel.app() != null ? dslModel.app().name() : "unknown";
        long workflowStartTime = System.currentTimeMillis();
        List<NodeExecutionRecord> records = new ArrayList<>();

        // 发射工作流开始事件
        emitEvent(eventListener -> eventListener.onWorkflowStarted(
            new WorkflowEvent.Started(workflowName, inputs != null ? new HashMap<>(inputs) : Java8Compat.mapOf(), workflowStartTime)));

        try {
            // Find start node
            DifyNode startNode = context.findStartNode();
            if (startNode == null) {
                throw new DifyParseException("No start node found in workflow");
            }

            log.info("Found start node: {}", startNode.id());

            // 注册所有 response 节点 (answer/end) 到流式协调器
            responseCoordinator.registerAll(context.getNodes());

            // 关键修复:为每个 type=answer 节点创建 AnswerSession,
            // 模板解析 + 多路径表(从 start 到本 answer 的所有路径,每条含必经 if-else 分支边)
            for (DifyNode node : context.getNodes()) {
                String type = node.data() != null ? node.data().type() : null;
                if ("answer".equals(type)) {
                    String template = node.data() != null ? node.data().answer() : null;
                    List<List<String>> paths = computePathEdges(node.id());
                    responseCoordinator.registerAnswerNode(node.id(), template,
                            variableResolver, paths);
                } else if ("end".equals(type)) {
                    // EndNode 占位(本次不实现,仅留接口)
                    Object outputs = node.data() != null ? node.data().outputs() : null;
                    List<?> outputsList = (outputs instanceof List) ? (List<?>) outputs : java.util.Collections.emptyList();
                    responseCoordinator.registerEndNode(node.id(), outputsList, variableResolver);
                }
            }

            // Initialize start node variables from inputs
            initializeStartNode(context, startNode, inputs);

            // Initialize conversation and environment variables
            initializeContextVariables(context);

            // Build and validate the graph
            MutableGraph<String> graph = buildGraph(context);
            validateGraph(graph);

            // Execute starting from the start node
            visited.clear();
            executeNode(startNode.id(), context, records);

            // Check if workflow completed successfully
            if (context.hasFailed()) {
                log.error("Workflow execution failed: {}", context.getErrorMessage());
                long elapsed = System.currentTimeMillis() - workflowStartTime;
                emitEvent(eventListener -> eventListener.onWorkflowFailed(
                    new WorkflowEvent.Failed(context.getErrorMessage(), 1, System.currentTimeMillis(), elapsed)));
                return DifyWorkflowResult.failed(context.getErrorMessage(), records);
            }

            log.info("Workflow execution completed successfully");
            long elapsed = System.currentTimeMillis() - workflowStartTime;
            emitEvent(eventListener -> eventListener.onWorkflowSucceeded(
                new WorkflowEvent.Succeeded(context.getOutputs(), System.currentTimeMillis(), elapsed)));
            return DifyWorkflowResult.succeeded(context.getOutputs(), records);

        } catch (Exception e) {
            log.error("Workflow execution failed", e);
            long elapsed = System.currentTimeMillis() - workflowStartTime;
            emitEvent(eventListener -> eventListener.onWorkflowFailed(
                new WorkflowEvent.Failed(e.getMessage(), 1, System.currentTimeMillis(), elapsed)));
            return DifyWorkflowResult.failed(e.getMessage(), records);
        }
    }

    private void initializeStartNode(DifyWorkflowContext context, DifyNode startNode, Map<String, Object> inputs) {
        DifyNodeData data = startNode.data();
        if (data.variables() != null) {
            for (DifyVariable variable : data.variables()) {
                Object value = inputs != null ? inputs.get(variable.variable()) : null;
                if (value == null && variable.defaultValue() != null) {
                    value = variable.defaultValue();
                }
                context.setVariable(startNode.id(), variable.variable(), value);
                log.debug("Initialized start variable: {} = {}", variable.variable(), value);
            }
        }
    }

    /**
     * 初始化会话变量和环境变量到变量池。
     * 参考 Dify 的 build_bootstrap_variables() + VariablePool.from_bootstrap()。
     */
    private void initializeContextVariables(DifyWorkflowContext context) {
        DifyWorkflow workflow = dslModel.workflow();

        // 环境变量 → 走 environmentVariables 专用通道(与 conversation 通道对称)
        // 关键修复:必须用 VariablePool.setEnvironment,不能走通用 setVariable。
        // 通用 setVariable 会写到 nodeOutputs["env"],而 AbstractDifyNode.resolveVariables
        // 的 ENVIRONMENT 分支从 getEnvironment(field) 走 environmentVariables 桶,
        // 两条路径永不相交,会导致 {{#env.X#}} 字面回显。
        if (workflow.environmentVariables() != null) {
            for (DifyEnvironmentVariable envVar : workflow.environmentVariables()) {
                String val = envVar.value();
                if (val != null && !val.isEmpty()) {
                    context.getVariablePool().setEnvironment(envVar.name(), val);
                    log.debug("Initialized env variable: {} = {}", envVar.name(), val);
                }
            }
        }

        // 会话变量 → 走 conversationVariables 专用通道（即使空值也初始化）
        // 关键修复:必须用 setConversationVariable,不能走通用 setVariable。
        // 通用 setVariable 会写到 nodeOutputs["conversation"],而 VariablePool.get
        // 对 "conversation" 永远走 conversationVariables 桶,两条路径永不相交,
        // 会导致 {{#conversation.X#}} 字面回显。
        if (workflow.conversationVariables() != null) {
            for (DifyConversationVariable convVar : workflow.conversationVariables()) {
                String val = convVar.value() != null ? convVar.value() : "";
                context.setConversationVariable(convVar.name(), val);
                log.debug("Initialized conversation variable: {} = {}", convVar.name(), val);
            }
        }
    }

    private MutableGraph<String> buildGraph(DifyWorkflowContext context) {
        MutableGraph<String> graph = GraphBuilder.directed()
                .allowsSelfLoops(false)
                .nodeOrder(ElementOrder.<String>natural())
                .build();

        DifyGraph difyGraph = context.getGraph();

        // Add all node IDs to the graph
        for (DifyNode node : difyGraph.nodes()) {
            graph.addNode(node.id());
        }

        // Add edges
        if (difyGraph.edges() != null) {
            for (DifyEdge edge : difyGraph.edges()) {
                graph.putEdge(edge.source(), edge.target());
            }
        }

        return graph;
    }

    private void validateGraph(MutableGraph<String> graph) {
        // Check for cycles using DFS
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();

        for (String nodeId : graph.nodes()) {
            if (hasCycle(nodeId, graph, visiting, visited)) {
                throw new DifyParseException("Workflow graph contains a cycle");
            }
        }
    }

    private boolean hasCycle(String nodeId, MutableGraph<String> graph, Set<String> visiting, Set<String> visited) {
        if (visiting.contains(nodeId)) {
            return true;
        }
        if (visited.contains(nodeId)) {
            return false;
        }

        visiting.add(nodeId);
        for (String successor : graph.successors(nodeId)) {
            if (hasCycle(successor, graph, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
        return false;
    }

    private void executeNode(String nodeId, DifyWorkflowContext context, List<NodeExecutionRecord> records) {
        executeNodeInternal(nodeId, context, records, null, null);
    }

    private void executeNodeInternal(String nodeId, DifyWorkflowContext context, List<NodeExecutionRecord> records,
                                      Set<String> subGraphNodeIds, Set<String> subVisited) {
        Set<String> effectiveVisited = subVisited != null ? subVisited : visited;

        if (effectiveVisited.contains(nodeId)) {
            return;
        }

        DifyNode node = context.findNodeById(nodeId);
        if (node == null) {
            log.warn("Node not found: {}", nodeId);
            return;
        }

        // 跳过属于迭代/循环体的节点 — 它们由容器节点（iteration/loop）驱动
        DifyNodeData data = node.data();
        if (subGraphNodeIds == null) {
            boolean isBodyNode = (data.isInIteration() != null && data.isInIteration())
                    || (data.isInLoop() != null && data.isInLoop())
                    || (data.parentId() != null && !data.parentId().isEmpty());
            if (isBodyNode && !"iteration".equals(data.type()) && !"loop".equals(data.type())
                    && !"iteration_start".equals(data.type()) && !"loop_start".equals(data.type())) {
                log.debug("Skipping body node {} (belongs to container, driven by iteration/loop)", nodeId);
                return;
            }
        }

        // 提前标记 visited，防止扇出汇合场景（A→B→F, A→C→F）中 F 被重复执行
        // 先递归确保未访问的前驱完成，再回来执行当前节点
        List<DifyEdge> incomingEdges = targetEdgesMap.get(nodeId);
        if (incomingEdges != null) {
            for (DifyEdge edge : incomingEdges) {
                if (subGraphNodeIds != null && !subGraphNodeIds.contains(edge.source())) {
                    continue;
                }
                if (!effectiveVisited.contains(edge.source())) {
                    executeNodeInternal(edge.source(), context, records, subGraphNodeIds, subVisited);
                }
            }
        }

        // 前驱递归过程中，本节点可能已被嵌套调用执行完毕
        if (effectiveVisited.contains(nodeId)) {
            return;
        }
        effectiveVisited.add(nodeId);

        String nodeType = data.type();
        String nodeTitle = data.title() != null ? data.title() : "";
        String inIterationId = data.iterationId();
        String inLoopId = data.loopId();

        // 检查前驱节点状态，任何前驱失败则跳过
        if (incomingEdges != null) {
            for (DifyEdge edge : incomingEdges) {
                if (subGraphNodeIds != null && !subGraphNodeIds.contains(edge.source())) {
                    continue;
                }
                if (context.getNodeStatusEnum(edge.source()) == DifyWorkflowContext.NodeStatus.ERROR) {
                    log.warn("Predecessor node {} failed, skipping node {}", edge.source(), nodeId);
                    context.setNodeStatus(nodeId, DifyWorkflowContext.NodeStatus.SKIP);
                    records.add(createRecord(node, context, DifyWorkflowContext.NodeStatus.SKIP, null));
                    return;
                }
            }
        }

        log.info("Executing node: {} (type: {})", nodeId, nodeType);
        long startTime = System.currentTimeMillis();
        context.setNodeStatus(nodeId, DifyWorkflowContext.NodeStatus.RUNNING);
        responseCoordinator.onNodeStarted(nodeId);

        // 捕获节点输入：从前驱节点收集输入变量
        final Map<String, Object> nodeInputs = collectNodeInputs(context, nodeId);

        // 发射节点开始事件
        emitEvent(l -> l.onNodeStarted(
            new WorkflowEvent.NodeStarted(nodeId, nodeType, nodeTitle, startTime, inIterationId, inLoopId)));

        try {
            com.dify.workflow.nodes.DifyNode executorNode = createNodeExecutor(node);
            executorNode.execute(context);
            long endTime = System.currentTimeMillis();

            context.setNodeStatus(nodeId, DifyWorkflowContext.NodeStatus.FINISH);

            Map<String, Object> outputs = context.getVariablePool().getNodeOutputs(nodeId);
            records.add(createRecordWithTiming(node, context, DifyWorkflowContext.NodeStatus.FINISH, outputs, startTime, endTime));

            // 获取节点执行过程数据（LLM请求详情等）
            final Map<String, Object> processData = executorNode instanceof com.dify.workflow.nodes.AbstractDifyNode
                    ? ((com.dify.workflow.nodes.AbstractDifyNode) executorNode).getProcessData() : null;

            // 发射节点成功事件
            emitEvent(l -> l.onNodeSucceeded(
                new WorkflowEvent.NodeSucceeded(nodeId, nodeType, nodeTitle, nodeInputs, outputs, processData,
                    startTime, endTime, endTime - startTime, inIterationId, inLoopId)));

            // Execute successors
            executeSuccessorsInternal(nodeId, context, records, subGraphNodeIds, subVisited);

            // 通知协调器节点完成，激活下一个等待的 response 节点
            String activated = responseCoordinator.onNodeCompleted(nodeId);
            if (activated != null) {
                executeNodeInternal(activated, context, records, subGraphNodeIds, subVisited);
            }

            // 关键修复:若是 LLM 节点,通知协调器 flush 该 LLM chunk buffer
            // (对应 Dify 原版 LLM 节点完成 → coordinator 刷出 buffer 到下游 answer)
            if (node != null && node.data() != null && "llm".equals(node.data().type())) {
                responseCoordinator.onLlmNodeCompleted(nodeId);
            }

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("Node execution failed: {} - {}", nodeId, e.getMessage(), e);
            context.setNodeStatus(nodeId, DifyWorkflowContext.NodeStatus.ERROR);
            records.add(createRecordWithTiming(node, context, DifyWorkflowContext.NodeStatus.ERROR, null, startTime, endTime));

            // 发射节点失败事件
            emitEvent(l -> l.onNodeFailed(
                new WorkflowEvent.NodeFailed(nodeId, nodeType, nodeTitle, nodeInputs, e.getMessage(),
                    startTime, endTime, endTime - startTime, inIterationId, inLoopId)));

            context.setFailed(e.getMessage());
        }
    }

    private com.dify.workflow.nodes.DifyNode createNodeExecutor(com.dify.workflow.model.DifyNode node) {
        String type = node.data().type();
        String id = node.id();
        DifyNodeData data = node.data();

        return com.dify.workflow.nodes.NodeRegistry.create(type, id, data);
    }

    private void executeSuccessors(String nodeId, DifyWorkflowContext context, List<NodeExecutionRecord> records) {
        executeSuccessorsInternal(nodeId, context, records, null, null);
    }

    private void executeSuccessorsInternal(String nodeId, DifyWorkflowContext context, List<NodeExecutionRecord> records,
                                            Set<String> subGraphNodeIds, Set<String> subVisited) {
        List<DifyEdge> outgoingEdges = sourceEdgesMap.get(nodeId);
        if (outgoingEdges == null) {
            return;
        }

        // 流式协调器排序：response 节点 (answer/end) 优先执行
        List<DifyEdge> sortedEdges = new ArrayList<>(outgoingEdges);
        sortedEdges.sort((e1, e2) -> {
            boolean e1Resp = responseCoordinator.isResponse(e1.target());
            boolean e2Resp = responseCoordinator.isResponse(e2.target());
            if (e1Resp && !e2Resp) return -1;
            if (!e1Resp && e2Resp) return 1;
            return 0;
        });

        // if-else 分支筛选
        String selectedCase = null;
        DifyNode node = context.findNodeById(nodeId);
        if (node != null && "if-else".equals(node.data().type())) {
            Object selected = context.getVariable(nodeId, "_selected_case");
            if (selected != null) {
                selectedCase = selected.toString();
                log.info("If-else node {} selected branch: {}", nodeId, selectedCase);
            }
        }

        for (DifyEdge edge : sortedEdges) {
            // 子图模式：跳过不在子图节点集内的目标
            if (subGraphNodeIds != null && !subGraphNodeIds.contains(edge.target())) {
                continue;
            }
            // if-else 分支筛选
            // 关键修复:必须按 selected_case 与 edge.sourceHandle 精确匹配,
            //   不能二值化为 "true"/"false"。Dify if-else 支持 N 个 case(每个 case 一个 UUID),
            //   边上的 sourceHandle 直接等于 case_id(或 "false" 兜底)。
            //   Python 端 if_else_node.py:49 用 edge_source_handle=selected_case_id or "false" 实现。
            if (selectedCase != null && edge.sourceHandle() != null) {
                String edgeHandle = edge.sourceHandle();
                if (!selectedCase.equals(edgeHandle)) {
                    log.debug("Skipping edge {} (handle={} != selected_case={})",
                            edge.id(), edgeHandle, selectedCase);
                    continue;
                }
            }
            // 关键修复:通知协调器该边被取走,用于路径表清零门控 answer 会话激活
            responseCoordinator.onEdgeTaken(edge.id());
            executeNodeInternal(edge.target(), context, records, subGraphNodeIds, subVisited);
        }
    }

    private NodeExecutionRecord createRecordWithTiming(DifyNode node, DifyWorkflowContext context,
                                                       DifyWorkflowContext.NodeStatus status,
                                                       Map<String, Object> outputs,
                                                       long startTime, long endTime) {
        return new NodeExecutionRecord(
                node.id(),
                node.data().type(),
                status.name(),
                null, // inputs
                outputs,
                startTime,
                endTime,
                status == DifyWorkflowContext.NodeStatus.ERROR ? context.getErrorMessage() : null
        );
    }

    private NodeExecutionRecord createRecord(DifyNode node, DifyWorkflowContext context,
                                            DifyWorkflowContext.NodeStatus status, Object result) {
        return new NodeExecutionRecord(
                node.id(),
                node.data().type(),
                status.name(),
                null, // inputs
                context.getVariablePool().getNodeOutputs(node.id()),
                null, // start time
                null, // end time
                status == DifyWorkflowContext.NodeStatus.ERROR ? context.getErrorMessage() : null
        );
    }

    /**
     * 执行子图（迭代/循环体内的节点）。
     * 参考 Dify 的 Graph.init(root_node_id) + GraphEngine 子图执行模式。
     *
     * @param rootNodeId      子图入口节点ID
     * @param containerNodeId 容器节点ID（iteration / loop 节点）
     * @param context         执行上下文
     */
    void executeSubGraph(String rootNodeId, String containerNodeId, DifyWorkflowContext context) {
        log.debug("Executing sub-graph: rootNodeId={}, containerNodeId={}", rootNodeId, containerNodeId);

        // 构建子图节点集：parentId 匹配的所有节点 + 入口节点
        Set<String> subGraphNodeIds = new HashSet<>();
        for (DifyNode node : context.getNodes()) {
            if (containerNodeId.equals(node.data().parentId())) {
                subGraphNodeIds.add(node.id());
            }
        }
        // 使用 containerNodeId 的 iteration_id 或 loop_id 匹配
        for (DifyNode node : context.getNodes()) {
            if (containerNodeId.equals(node.data().iterationId()) || containerNodeId.equals(node.data().loopId())) {
                subGraphNodeIds.add(node.id());
            }
        }
        subGraphNodeIds.add(rootNodeId);

        // 独立 visited 集合，不影响主流程
        Set<String> subVisited = new HashSet<>();
        List<NodeExecutionRecord> dummyRecords = new ArrayList<>();

        executeNodeInternal(rootNodeId, context, dummyRecords, subGraphNodeIds, subVisited);
    }

    /** 收集节点的输入变量（从前驱节点获取） */
    private Map<String, Object> collectNodeInputs(DifyWorkflowContext context, String nodeId) {
        Map<String, Object> inputs = new HashMap<>();
        List<DifyEdge> incomingEdges = targetEdgesMap.get(nodeId);
        if (incomingEdges != null) {
            for (DifyEdge edge : incomingEdges) {
                Map<String, Object> predOutputs = context.getVariablePool().getNodeOutputs(edge.source());
                if (predOutputs != null && !predOutputs.isEmpty()) {
                    for (Map.Entry<String, Object> predEntry : predOutputs.entrySet()) {
                        inputs.put(edge.source() + "." + predEntry.getKey(), predEntry.getValue());
                    }
                }
            }
        }
        return inputs;
    }

    /** 发射事件的安全辅助方法 */
    private void emitEvent(java.util.function.Consumer<WorkflowEventListener> emitter) {
        if (eventListener != null) {
            try {
                emitter.accept(eventListener);
            } catch (Exception e) {
                log.warn("Event listener error: {}", e.getMessage());
            }
        }
    }

    public DifyDslModel getDslModel() {
        return dslModel;
    }

    public LlmProviderFactory getLlmProviderFactory() {
        return llmProviderFactory;
    }

    /**
     * Builder for DifyWorkflowExecutor.
     */
    public static class Builder {
        private DifyDslModel dslModel;
        private LlmProviderFactory llmProviderFactory;
        private WorkflowEventListener eventListener;
        private String conversationId;
        private com.dify.workflow.model.ConversationHistoryStore historyStore;
        private List<String> subWorkflows;
        private Map<String, com.dify.workflow.model.McpServerConfig> mcpConfigs;

        public Builder dslModel(DifyDslModel dslModel) {
            this.dslModel = dslModel;
            return this;
        }

        public Builder yaml(String yaml) {
            DifyYamlParser parser = new DifyYamlParser();
            this.dslModel = parser.parse(yaml);
            return this;
        }

        public Builder yamlFile(Path filePath) {
            DifyYamlParser parser = new DifyYamlParser();
            this.dslModel = parser.parse(filePath);
            return this;
        }

        public Builder llmProviderFactory(LlmProviderFactory llmProviderFactory) {
            this.llmProviderFactory = llmProviderFactory;
            return this;
        }

        public Builder eventListener(WorkflowEventListener eventListener) {
            this.eventListener = eventListener;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder historyStore(com.dify.workflow.model.ConversationHistoryStore historyStore) {
            this.historyStore = historyStore;
            return this;
        }

        /**
         * 注册子工作流 YAML 列表，自动提取 app.name 作为 key。
         *
         * @param subWorkflowYamls 子工作流 YAML 列表
         */
        public Builder subWorkflows(List<String> subWorkflowYamls) {
            this.subWorkflows = subWorkflowYamls;
            return this;
        }

        public Builder mcpConfigs(Map<String, com.dify.workflow.model.McpServerConfig> mcpConfigs) {
            this.mcpConfigs = mcpConfigs;
            return this;
        }

        public DifyWorkflowExecutor build() {
            if (this.llmProviderFactory == null) {
                this.llmProviderFactory = LlmProviderFactory.builder()
                        .withBuiltinProviders()
                        .build();
            }
            return new DifyWorkflowExecutor(this);
        }

        /**
         * 将子工作流 YAML 列表转换为 Map（key = app.name）。
         */
        private Map<String, String> buildSubWorkflowsMap() {
            if (this.subWorkflows == null || this.subWorkflows.isEmpty()) {
                return Java8Compat.mapOf();
            }
            DifyYamlParser parser = new DifyYamlParser();
            Map<String, String> map = new java.util.HashMap<>();
            for (String yaml : this.subWorkflows) {
                String appName = parser.extractAppName(yaml);
                map.put(appName, yaml);
            }
            return map;
        }
    }
}