# Dify Workflow Engine (Java)

> **项目来源**
>
> Dify 是开源的 LLM 应用编排平台,但**信创(国产化)环境**通常无法直接部署官方 Dify 服务(网络隔离、依赖限制、License 等)。**迁移信创时,业务方需要一套能本地运行 Dify 工作流 YAML 的代码**——本项目正是为此目的自研的 Dify Workflow Engine Java 实现。
>
> 解析并本地执行 Dify 0.x 导出的 YAML 工作流定义,支持 19 类节点 + 2 种 Agent 策略 + 流式事件回调,可替代 HTTP 远程调用,跑在信创环境的 JVM 上。

## 项目结构

```
dify-java-executor/                         # 多模块 Maven 项目,父 pom 管理依赖
├── pom.xml                                  # 父 POM: Java 8 编译目标 / nashorn-core 15.6 / okhttp-sse 4.12.0
│
├── dify-model/                              # DSL 数据模型层
│   └── src/main/java/com/dify/workflow/model/
│       ├── WorkflowEvent.java               # 12 种事件 payload (含 Chunk inner class 流式)
│       ├── WorkflowEventListener.java       # listener 接口 (含 onChunk 默认空实现)
│       ├── VariablePool.java                # 变量池接口
│       ├── ChatRequest.java / Message.java  # LLM chat 请求 DTO
│       ├── LlmService.java / LlmCallResult / StreamDelta  # LLM 调用接口 + 同步/流式返回
│       └── node/                            # 节点类型枚举 (NodeType, SystemVariable)
│
├── dify-parser/                             # YAML 解析 + 变量替换
│   └── src/main/java/com/dify/workflow/parser/
│       ├── DifyYamlParser.java              # SnakeYAML + FastJSON2 解析 Dify DSL
│       └── VariableResolver.java            # {{#nodeId.var#}} / {{#sys.X#}} 等引用替换
│
├── dify-nodes/                              # 节点实现层
│   └── src/main/java/com/dify/workflow/nodes/
│       ├── AbstractDifyNode.java            # 节点基类(模板方法 doExecute)
│       ├── StartNode / EndNode / AnswerNode
│       ├── LlmNode.java                     # LLM 节点 (stream=true 写死,emitChunk 转发)
│       ├── CodeNode.java                    # JS 代码执行 (Nashorn + const/let/解构 source transform)
│       ├── TemplateTransformNode.java       # Jinja2 模板替换
│       ├── VariableAssignerNode / VariableAggregatorNode / ListFilterNode
│       ├── IfElseNode / IterationNode / LoopNode
│       ├── HttpRequestNode.java             # HTTP 请求 (Dify 风格字段 + retry/SSL/超时)
│       ├── ToolNode / QuestionClassifierNode
│       ├── ParameterExtractorNode / DocExtractorNode
│       ├── agent/                           # Agent 节点策略模式
│       │   ├── AgentNode.java               # 策略入口
│       │   ├── AgentExecutionContext.java   # 调 callLlm 双调用(call+callStream emitChunk)
│       │   ├── FunctionCallStrategy.java    # LLM tool/function calling 循环
│       │   ├── ReActStrategy.java           # Thought→Action→Observation
│       │   ├── McpStrategy.java             # MCP 工具调用 (框架预留)
│       │   └── mcp/                         # McpClient JSON-RPC 2.0 + McpToolRegistry
│       └── condition/                       # 条件求值器 (ConditionProcessor)
│
├── dify-engine/                             # 执行引擎层
│   └── src/main/java/com/dify/workflow/engine/
│       ├── DifyWorkflowExecutor.java         # 核心执行器 (DFS 遍历 + emit 事件)
│       ├── DifyWorkflowContext.java         # 执行上下文 (变量池 + listener 透传 emitChunk)
│       ├── DifyVariablePool.java            # 4 桶实现 (nodeOutputs/systemVariables/environmentVariables/conversationVariables)
│       ├── DifyWorkflowFactory.java         # 外部封装工厂类 (Builder + ExecuteOptions)
│       ├── ResponseStreamCoordinator.java   # 协调多 answer 节点 emit 顺序
│       └── llm/                             # LLM Provider 体系
│           ├── LlmProviderFactory.java      # 注册 4 个内置 provider executor
│           ├── LlmProviderConfig.java       # Provider 配置
│           └── providers/                   # 4 个 Provider 实现
│               ├── AbstractLlmProvider.java # callStream SSE 解析 (okhttp3.sse)
│               ├── OpenAiProvider / DeepSeekProvider / MiniMaxProvider / CustomOpenAiProvider
│
├── testworkflow.yml                         # 简单计算工作流 (a+b)
├── testchatflow.yml                         # 基础 Chatflow (LLM + if-else + answer)
├── testchatflow2.yml                        # 主测试工作流 (10 个分支 + 3 个 SSE 流式 test)
├── testchatflow3.yml                        # Chatflow 变体
├── testchatflow-agent.yml                   # Agent 节点测试
├── test-agent.yml / test-basic-nodes.yml    # 单节点单元测试
├── test-conditionals.yml / test-iteration-loop.yml / test-llm.yml
│
├── test-output/                             # 测试报告输出目录
└── test-output.log                          # 集成测试 log (单文件)
```

### 模块依赖关系

```
dify-parser ──┐
              ├─→ dify-engine ←── dify-nodes
dify-model ───┘                       (节点实现)
   ↑
   │ (基础类型)
   └─→ dify-parser / dify-engine / dify-nodes 全部依赖 dify-model
```

**编译顺序**: `dify-model` → `dify-parser` → `dify-engine` + `dify-nodes` (后者依赖前两者)。父 `pom.xml` 用 `dependencyManagement` 统一管理 nashorn-core / okhttp / fastjson2 / snakeyaml 等公共依赖版本。

## 编译与依赖

```bash
# 编译(JDK 8 / Java 17 跨版本兼容,内部 Nashorn 15.6 standalone)
mvn clean install -DskipTests
```

```xml
<dependency>
    <groupId>io.github.deouy</groupId>
    <artifactId>dify-engine</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 快速上手 — `DifyWorkflowFactory` 工厂版

`DifyWorkflowFactory` 是推荐入口。Builder 阶段配全局(LLM Provider / MCP / YAML registry),execute 阶段传 listener / historyStore / conversationId(每次执行特定)。

### 一次性初始化

```java
DifyWorkflowFactory factory = DifyWorkflowFactory.builder()
    // LLM Provider(全局,Builder 默认调 withBuiltinProviders() 注册 openai/deepseek/minimax/custom 4 个内置 executor)
    .llmProvider(LlmProviderConfig.builder()
        .provider("deepseek")
        .baseUrl("https://api.deepseek.com/v1")
        .apiKey("sk-xxx")
        .defaultModel("deepseek-chat")
        .build())
    // MCP Server(全局,Agent 节点用)
    .mcpConfig("baidumap", McpServerConfig.builder()
        .url("https://mcp.map.baidu.com/mcp?ak=xxx")
        .timeout(60)
        .sseReadTimeout(120)
        .build())
    // 注册工作流 YAML(自动提取 app.name 做 key)
    .registerWorkflow(Path.of("testworkflow.yml"))
    .registerWorkflow(Path.of("testchatflow.yml"))
    // 也可传 YAML 字符串
    .registerWorkflow(yamlString)
    .build();
```

### 动态注册 / 注销

工厂方法 `registerWorkflow` / `removeWorkflow` / `mcpConfig` 都可以在 `build()` 之后随时调用,适合**热加载**(从外部配置中心拉新 YAML、动态增 MCP Server、临时屏蔽某 workflow):

```java
// 1. 注册新工作流(覆盖同名 key 的旧 workflow)
String id1 = factory.registerWorkflow(Path.of("new-workflow.yml"));   // 返回 app.name 作 key
String id2 = factory.registerWorkflow(yamlString);                  // 也可传字符串

// 2. 移除已注册的工作流
factory.removeWorkflow("testworkflow");

// 3. 查询已注册的 workflow
Set<String> allWorkflowIds = factory.getWorkflowIds();
boolean has = factory.hasWorkflow("testchatflow");

// 4. 注册 / 覆盖 MCP Server(同 name 覆盖)
factory.mcpConfig("baidumap", McpServerConfig.builder()
        .url("https://mcp.map.baidu.com/mcp?ak=NEW_KEY")
        .timeout(60)
        .sseReadTimeout(120)
        .build());

// 5. 跨工厂复用 YAML 注册表(常用于多环境共享 workflow 定义)
byte[] sharedYaml = Files.readAllBytes(Path.of("shared-workflow.yml"));
factory.registerWorkflow(new String(sharedYaml, StandardCharsets.UTF_8));
```

注意: `LLM Provider` 配置需在 `Builder.build()` 前调 `llmProvider(...)`,工厂**构建后**不能再加 Provider(Provider 是按需在 `Builder` 阶段初始化的)。如果需要切换 LLM Provider,重新 `DifyWorkflowFactory.builder()...build()` 即可,旧 factory 的 workflow registry / MCP 配置可以手动迁移。

### 用法 1 — 简单执行(无 listener,无多轮)

```java
// 只需 YAML app.name + inputs
DifyWorkflowResult result = factory.execute("testworkflow", Map.of("a", 1, "b", 1));
System.out.println(result.status());    // "succeeded"
System.out.println(result.outputs());    // {result=2.0, result2=11}
```

### 用法 2 — 非流式执行(自定义 listener 接节点事件,无 LLM 流)

```java
DifyWorkflowResult result = factory.execute(
    "testchatflow",
    Map.of("c", "LLM", "query", "Hello", "sys.query", "Hello"),
    DifyWorkflowFactory.ExecuteOptions.builder()
        .listener(new WorkflowEventListener() {
            @Override public void onWorkflowStarted(WorkflowEvent.Started e) {
                System.out.println("[开始] " + e.workflowName());
            }
            @Override public void onNodeStarted(WorkflowEvent.NodeStarted e) {
                System.out.println("  [节点] " + e.nodeType() + " (" + e.nodeId() + ")");
            }
            @Override public void onNodeSucceeded(WorkflowEvent.NodeSucceeded e) {
                System.out.println("  [完成] " + e.nodeType() + " → " + e.outputs()
                    + " (" + e.elapsedMs() + "ms)");
            }
            @Override public void onNodeFailed(WorkflowEvent.NodeFailed e) {
                System.out.println("  [失败] " + e.nodeType() + ": " + e.error());
            }
            @Override public void onWorkflowSucceeded(WorkflowEvent.Succeeded e) {
                System.out.println("[结果] " + e.outputs() + " (" + e.elapsedMs() + "ms)");
            }
            @Override public void onWorkflowFailed(WorkflowEvent.Failed e) {
                System.out.println("[失败] " + e.error());
            }
        })
        .build());

System.out.println("answer = " + result.outputs().get("answer"));
```

非流式: listener 收到节点级 `onNodeSucceeded` 时拿到完整 outputs(LLM 节点一次性返回完整 answer)。

### 用法 3 — 流式执行(LLM token-by-token 实时推送)

用 `SseStreamListener` 验证 LLM 流式 chunk 累积 == 最终 answer:

```java
SseStreamListener sse = new SseStreamListener();
DifyWorkflowResult result = factory.execute(
    "testchatflow",
    Map.of("c", "LLM", "query", "Hello", "sys.query", "Hello"),
    DifyWorkflowFactory.ExecuteOptions.builder()
        .listener(sse)
        .build());

// 流式 chunk 实时累积
String accumulated = sse.accumulated();
System.out.println("chunks.size = " + sse.size());           // N 个 token
System.out.println("byNode = " + sse.getChunksByNodeId().keySet());
System.out.println("accumulated = " + accumulated);
// 关键:accumulated == answer(流式拼接 == 同步结果)
System.out.println("answer = " + result.outputs().get("answer"));
assertEquals(result.outputs().get("answer").toString(), accumulated);
```

流式原理: LLM 节点写死 `stream=true` (对齐 Dify Python 4 年设计),每收到 SSE delta 通过 `context.emitChunk()` 转发给所有 listener 的 `onChunk`。下游 answer 节点订阅 chunk 实时透传,中间节点(Code/Tool)只读 `outputs["text"]`。

### 用法 4 — 多轮记忆 + 流式

```java
// 共享 conversationId 和 historyStore,两轮对话累积记忆
InMemoryConversationStore history = new InMemoryConversationStore();
String conversationId = "user-123-session-1";

// 第 1 轮
SseStreamListener sseR1 = new SseStreamListener();
DifyWorkflowResult r1 = factory.execute("testchatflow",
    Map.of("c", "llm3测试", "query", "Hi, I'm D", "sys.query", "Hi, I'm D"),
    DifyWorkflowFactory.ExecuteOptions.builder()
        .listener(sseR1)
        .historyStore(history)
        .conversationId(conversationId)
        .build());
// 手动写回历史(模拟 Dify 后端持久化)
history.saveMessage(conversationId, "Hi, I'm D", String.valueOf(r1.outputs().get("answer")));

// 第 2 轮
SseStreamListener sseR2 = new SseStreamListener();
DifyWorkflowResult r2 = factory.execute("testchatflow",
    Map.of("c", "llm3测试", "query", "What's my name?", "sys.query", "What's my name?"),
    DifyWorkflowFactory.ExecuteOptions.builder()
        .listener(sseR2)
        .historyStore(history)
        .conversationId(conversationId)
        .build());
// r2 的 LLM 看到第 1 轮 history,能回答 "D"
```

### 用法 5 — HTTP 输出层(实际推送给前端)

listener 只暴露 chunk 事件,**不在本仓库范围**集成 Spring MVC / Servlet。集成示例(自行添加):

```java
// Spring MVC controller 推 SSE 给客户端
@GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@RequestParam String query) {
    SseEmitter emitter = new SseEmitter();
    SseStreamListener listener = new SseStreamListener() {
        @Override public void onChunk(WorkflowEvent.Chunk event) {
            try {
                if (event.delta() != null) {
                    emitter.send(SseEmitter.event()
                        .name("chunk")
                        .data(event.delta()));
                }
            } catch (IOException e) { emitter.completeWithError(e); }
        }
        @Override public void onNodeSucceeded(WorkflowEvent.NodeSucceeded e) {
            if ("answer".equals(e.nodeType())) {
                emitter.complete();
            }
        }
    };
    new Thread(() -> {
        factory.execute("testchatflow",
            Map.of("c", "LLM", "query", query, "sys.query", query),
            DifyWorkflowFactory.ExecuteOptions.builder().listener(listener).build());
    }).start();
    return emitter;
}
```

## 事件系统

12 种事件类型,通过 `WorkflowEventListener` 回调:

| 事件 | 触发时机 |
|------|----------|
| `onWorkflowStarted` | 工作流开始 |
| `onWorkflowSucceeded` | 工作流成功 |
| `onWorkflowFailed` | 工作流失败 |
| `onNodeStarted` | 节点开始 |
| `onNodeSucceeded` | 节点成功(含 outputs) |
| `onNodeFailed` | 节点失败(含 error) |
| `onIterationStarted/Next/Completed` | iteration 节点 |
| `onLoopStarted/Next/Completed` | loop 节点 |
| `onChunk` | **流式**: LLM/Agent 节点每收到一个 SSE delta 触发,`event.delta()` 是单 token |

`onChunk` 默认空实现,既有 listener 不需改动即可继续工作。`chunk.index` 单调递增,可用于客户端拼装顺序。

## 节点类型支持状态

### 已实现 (19 个)

| 分类 | 节点 | 说明 |
|------|------|------|
| 基础 | start / end / answer | answer 节点走 ResponseStreamCoordinator 串行化 |
| 数据处理 | llm / code / template-transform / variable_assigner / variable_aggregator / list_filter | LLM + JS + 模板 + 变量操作 |
| 逻辑控制 | if-else / iteration / loop | 含 break 条件和子图执行 |
| 外部集成 | http-request / tool / question_classifier | tool 节点支持嵌套子工作流 |
| 工具 | parameter_extractor / doc-extractor | PDF/Word/Excel/TXT 提取 |
| Agent | agent | FunctionCall / ReAct 策略已实现 |

### 待处理部分

| 模块 | 状态 | 说明 |
|------|------|------|
| 节点异常统一处理 | ⚠️ **未实现** | YAML 中 `error_handle_mode` 字段在 iteration/loop 节点只支持 `terminated`(错误终止),`continue`(错误继续)模式目前无效。节点异常时只回退 ERROR 状态到上游,无 fail_branch / default_value fallback 机制 |
| 知识库节点 | ⚠️ **未实现** | `knowledge_retrieval` / `knowledge_index` 节点故意不支持(本仓库定位是"执行工作流",不是"完整 Dify 替代")。如需 RAG 功能,建议在 YAML 上游加 HTTP 检索节点对接外部向量库 |
| Agent MCP 工具调用 | 🔧 框架预留 | `McpStrategy` 已定义接口,客户端 `McpClient` 实现 JSON-RPC 2.0 调用,但未对接真实百度地图等 MCP Server(`McpClient` 还在调试阶段) |
| LLM 节点"启用推理"标签分离 | ⚠️ **未实现** | YAML 中 `model.completion_params.thinking` 字段(Dify 后端用 deepseek-r1 等推理模型时设为 `true`)LlmNode 不读该字段;LLM 返回的推理内容 `<think>...</think>` 与最终回答未分离,下游 answer 节点会输出混合文本。`tryParseStructuredOutput` 第 3 步已实现剥离 `<think>` 前缀,但仅对结构化输出解析路径生效,普通 `text` 输出未做分离,`processData` 也不记录 reasoning_tokens |
| LLM 节点视觉配置 | ⚠️ **未实现** | YAML 中 `vision.enabled` 字段在 DifyNodeData 有定义但 LlmNode 不读。当前仅对请求内容做"硬校验"——若 message 含 `image_url` / `![](...)` / 常见图片后缀但 provider 不支持 vision 则抛错,反之则全放行。`vision.enabled=false` 时应禁用文件→图片转换、`sys.files` 应自动转成多模态 content 这两条逻辑均未实现,需手动在 prompt 模板里写 `{{#sys.file.url#}}` 才能透传图片 |

### 故意不支持 (3 个触发器)

`trigger_webhook` / `trigger_schedule` / `trigger_plugin` 节点不实现(本仓库定位是被动执行,不是调度平台)。

## 变量系统

| 格式 | 示例 | 说明 |
|------|------|------|
| `{{#nodeId.var#}}` | `{{#start.query#}}` | 节点变量引用 |
| `{{#sys.var#}}` | `{{#sys.query#}}` | 系统变量(`query`/`files`/`user_id`/`conversation_id`) |
| `{{#env.var#}}` | `{{#env.API_KEY#}}` | 环境变量 |
| `{{#conversation.var#}}` | `{{#conversation.user#}}` | 会话变量 |
| `{{ varName }}` | `{{ result }}` | Jinja2 模板(template-transform 专用) |

**Dify 0.x 公开 API 约定**: chatflow API inputs 字段用 `sys.query` / `sys.files` / `sys.user_id` / `sys.conversation_id` 4 个固定键传 system 变量。StartNode 写入侧调 `VariablePool.setSystem()` 写到 `systemVariables` 桶,读取侧从 `getSystem()` 拿。

## 编译 + 测试

```bash
# JDK 8 (推荐,业务方生产环境一般是 JDK 8)
export JAVA_HOME="F:/deveploer/jdk1.8.0_271"
export PATH="$JAVA_HOME/bin:$PATH"

# 编译
mvn clean install -DskipTests

# 跑 chatflow2 全量 12 个分支(11 个原 test + 3 个 SSE test,部分分支共用)
mvn test -pl dify-engine -Dtest='TestChatflow2WorkflowTest$Branches'

# 单独跑流式验证
mvn test -pl dify-engine -Dtest='TestChatflow2WorkflowTest$Branches#testBranch1_LLM_Stream'
mvn test -pl dify-engine -Dtest='TestChatflow2WorkflowTest$Branches#testBranch5_Agent1_Stream'
mvn test -pl dify-engine -Dtest='TestChatflow2WorkflowTest$Branches#testBranch9_Llm3_Stream'
```

## 已知限制

1. **Nashorn 兼容性**: 使用 Nashorn 15.6 standalone (Java 17 已 JEP 372 removed)。`const`/`let` 自动转 `var`,解构参数 `function main({a,b})` 自动去结构化
2. **同步执行**: 所有节点在当前线程同步执行,无并行优化
3. **LLM 配置**: 需要在代码中配置 `LlmProviderFactory`,不从 YAML 自动读取
4. **Break 条件**: Loop 节点只支持 `>` 和 `>=` 比较
5. **Tool 节点注册**: 当 workflow 发布为 tool 时,`app.name` 必须与工具名相同或为其中文拼音

## 使用限制

**Tool 节点注册限制**: 当将 workflow 发布为 tool 时,workflow 的 `app.name` 必须与工具名称相同,或者为工具名称的中文拼音(系统自动将中文名转拼音匹配)。例如:工具名为 "加法计算器" 或 "addCalculator",则 workflow 的 `app.name` 应为 `jiafajisuanqi` 或 `addCalculator`。
