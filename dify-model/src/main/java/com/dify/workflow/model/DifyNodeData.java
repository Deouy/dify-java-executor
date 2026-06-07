package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.annotation.JSONType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dify node data containing the actual node type and its configuration.
 *
 * The 'type' field indicates the actual node type (start, llm, code, if-else, etc.)
 */
public final class DifyNodeData {
    @JSONField(name = "type")
    private final String type;
    @JSONField(name = "title")
    private final String title;
    @JSONField(name = "desc")
    private final String desc;
    @JSONField(name = "selected")
    private final Boolean selected;

    // Start node fields
    @JSONField(name = "variables")
    private final List<DifyVariable> variables;

    // LLM node fields
    @JSONField(name = "model")
    private final DifyModel model;
    @JSONField(name = "prompt_template")
    private final List<DifyPromptMessage> promptTemplate;
    @JSONField(name = "vision")
    private final DifyVisionConfig vision;
    @JSONField(name = "memory")
    private final DifyMemoryConfig memory;
    @JSONField(name = "context")
    private final DifyContextConfig context;
    @JSONField(name = "structured_output")
    private final DifyStructuredOutputConfig structuredOutput;
    @JSONField(name = "retry_config")
    private final DifyRetryConfig retryConfig;

    // Code node fields
    @JSONField(name = "code")
    private final String code;
    @JSONField(name = "language")
    private final String language;
    // 关键修复:Dify DSL 里 code/template-transform/start 三种节点都使用 "variables"
    //   作为变量定义字段。FastJSON2 遇到重复 JSON 字段名时只匹配第一个,会导致后续
    //   节点类型的 variables 解析不到。这里把 Code 节点的输入变量也指向同一个
    //   `variables` 字段(line 24 的 start 节点字段),accessor 仍叫 inputVariables
    //   保持调用方代码不变 — 通过 inputVariables() 方法返回同一个 fields 列表。
    @JSONField(name = "input_variables")
    private final List<DifyVariable> inputVariables;
    @JSONField(name = "output_variables")
    private final List<DifyVariable> outputVariables;

    // HTTP Request node fields
    @JSONField(name = "method")
    private final String method;
    @JSONField(name = "url")
    private final String url;
    @JSONField(name = "headers")
    private final Map<String, String> headers;
    @JSONField(name = "params")
    private final Map<String, String> params;
    @JSONField(name = "body")
    private final DifyHttpBody body;
    @JSONField(name = "authorization")
    private final DifyAuthorization authorization;

    // If-Else node fields
    @JSONField(name = "cases")
    private final List<DifyCase> cases;
    @JSONField(name = "output_type")
    private final String outputType;

    // End node fields
    @JSONField(name = "outputs")
    private final Object outputs;

    // Answer node fields
    @JSONField(name = "answer")
    private final String answer;

    // Iteration node fields
    @JSONField(name = "parallel_nums")
    private final Integer parallelNums;
    @JSONField(name = "max_concurrency")
    private final Integer maxConcurrency;

    // Iteration/Loop common fields
    @JSONField(name = "iterator_selector")
    private final List<String> iteratorSelector;
    @JSONField(name = "output_selector")
    private final List<String> outputSelector;
    @JSONField(name = "start_node_id")
    private final String startNodeId;
    @JSONField(name = "parentId")
    private final String parentId;
    @JSONField(name = "iteration_id")
    private final String iterationId;
    @JSONField(name = "loop_id")
    private final String loopId;
    @JSONField(name = "isInIteration")
    private final Boolean isInIteration;
    @JSONField(name = "isInLoop")
    private final Boolean isInLoop;
    @JSONField(name = "loop_count")
    private final Integer loopCount;

    // Template Transform node fields
    @JSONField(name = "template")
    private final String template;

    // Variable Aggregator node fields
    @JSONField(name = "enable")
    private final Boolean enable;
    @JSONField(name = "advanced_settings")
    private final Map<String, Object> advancedSettings;

    // Parameter Extractor node fields
    @JSONField(name = "parameters")
    private final List<DifyParameter> parameters;
    @JSONField(name = "result_type")
    private final String resultType;

    // Question Classifier node fields
    @JSONField(name = "classes")
    private final List<DifyClass> classes;
    @JSONField(name = "instruction")
    private final String instruction;

    // Tool node fields
    @JSONField(name = "provider_id")
    private final Object providerId;
    @JSONField(name = "provider_name")
    private final String providerName;
    @JSONField(name = "provider_type")
    private final String providerType;
    @JSONField(name = "tool_name")
    private final String toolName;
    @JSONField(name = "tool_label")
    private final String toolLabel;
    @JSONField(name = "tool_input_parameters")
    private final Map<String, Object> toolInputParameters;
    @JSONField(name = "tool_parameters")
    private final Map<String, Object> toolParameters;
    @JSONField(name = "tool_configurations")
    private final Map<String, Object> toolConfigurations;
    @JSONField(name = "is_team_authorization")
    private final Boolean isTeamAuthorization;

    // Agent node fields
    @JSONField(name = "agent")
    private final DifyAgentConfig agent;
    @JSONField(name = "agent_strategy_label")
    private final String agentStrategyLabel;
    @JSONField(name = "agent_strategy_name")
    private final String agentStrategyName;
    @JSONField(name = "agent_parameters")
    private final Map<String, Object> agentParameters;

    // Assigner node fields
    @JSONField(name = "items")
    private final List<Map<String, Object>> assignerItems;
    @JSONField(name = "version")
    private final String version;

    // Loop break conditions
    @JSONField(name = "break_conditions")
    private final List<Map<String, Object>> breakConditions;

    // Generic fields
    // 注意：非 final 以便 DifyYamlParser 解析后回填未知字段（FastJSON2 默认不收集未匹配字段到 Map）
    private Map<String, Object> additionalProperties;

    public DifyNodeData(String type, String title, String desc, Boolean selected,
                        List<DifyVariable> variables, DifyModel model, List<DifyPromptMessage> promptTemplate,
                        DifyVisionConfig vision, DifyMemoryConfig memory, DifyContextConfig context,
                        DifyStructuredOutputConfig structuredOutput, DifyRetryConfig retryConfig,
                        String code, String language, List<DifyVariable> inputVariables, List<DifyVariable> outputVariables,
                        String method, String url, Map<String, String> headers, Map<String, String> params,
                        DifyHttpBody body, DifyAuthorization authorization,
                        List<DifyCase> cases, String outputType, Object outputs, String answer,
                        Integer parallelNums, Integer maxConcurrency,
                        List<String> iteratorSelector, List<String> outputSelector, String startNodeId,
                        String parentId, String iterationId, String loopId,
                        Boolean isInIteration, Boolean isInLoop, Integer loopCount,
                        String template, Boolean enable, Map<String, Object> advancedSettings,
                        List<DifyParameter> parameters, String resultType,
                        List<DifyClass> classes, String instruction,
                        Object providerId, String providerName, String providerType,
                        String toolName, String toolLabel,
                        Map<String, Object> toolInputParameters, Map<String, Object> toolParameters,
                        Map<String, Object> toolConfigurations, Boolean isTeamAuthorization,
                        DifyAgentConfig agent, String agentStrategyLabel, String agentStrategyName,
                        Map<String, Object> agentParameters,
                        List<Map<String, Object>> assignerItems, String version,
                        List<Map<String, Object>> breakConditions,
                        Map<String, Object> additionalProperties) {
        this.type = type;
        this.title = title;
        this.desc = desc != null ? desc : "";
        this.selected = selected != null ? selected : false;
        this.variables = variables;
        this.model = model;
        this.promptTemplate = promptTemplate;
        this.vision = vision;
        this.memory = memory;
        this.context = context;
        this.structuredOutput = structuredOutput;
        this.retryConfig = retryConfig;
        this.code = code;
        this.language = language;
        this.inputVariables = inputVariables;
        this.outputVariables = outputVariables;
        this.method = method;
        this.url = url;
        this.headers = headers;
        this.params = params;
        this.body = body;
        this.authorization = authorization;
        this.cases = cases;
        this.outputType = outputType;
        this.outputs = outputs;
        this.answer = answer;
        this.parallelNums = parallelNums;
        this.maxConcurrency = maxConcurrency;
        this.iteratorSelector = iteratorSelector;
        this.outputSelector = outputSelector;
        this.startNodeId = startNodeId;
        this.parentId = parentId;
        this.iterationId = iterationId;
        this.loopId = loopId;
        this.isInIteration = isInIteration;
        this.isInLoop = isInLoop;
        this.loopCount = loopCount;
        this.template = template;
        this.enable = enable;
        this.advancedSettings = advancedSettings;
        this.parameters = parameters;
        this.resultType = resultType;
        this.classes = classes;
        this.instruction = instruction;
        this.providerId = providerId;
        this.providerName = providerName;
        this.providerType = providerType;
        this.toolName = toolName;
        this.toolLabel = toolLabel;
        this.toolInputParameters = toolInputParameters;
        this.toolParameters = toolParameters;
        this.toolConfigurations = toolConfigurations;
        this.isTeamAuthorization = isTeamAuthorization;
        this.agent = agent;
        this.agentStrategyLabel = agentStrategyLabel;
        this.agentStrategyName = agentStrategyName;
        this.agentParameters = agentParameters;
        this.assignerItems = assignerItems;
        this.version = version;
        this.breakConditions = breakConditions;
        this.additionalProperties = additionalProperties;
    }

    public String type() { return type; }
    public String title() { return title; }
    public String desc() { return desc; }
    public Boolean selected() { return selected; }
    public List<DifyVariable> variables() { return variables; }
    // 关键修复:code 节点的 inputVariables 和 template-transform 的 templateVariables
    //   实际都对应 Dify DSL 里的 "variables" 字段(同 start 节点)。
    //   这两个方法指向同一份列表,保留命名方便调用方阅读。
    public List<DifyVariable> inputVariables() { return variables; }
    public List<DifyVariable> templateVariables() { return variables; }
    public DifyModel model() { return model; }
    public List<DifyPromptMessage> promptTemplate() { return promptTemplate; }
    public DifyVisionConfig vision() { return vision; }
    public DifyMemoryConfig memory() { return memory; }
    public DifyContextConfig context() { return context; }
    public DifyStructuredOutputConfig structuredOutput() { return structuredOutput; }
    public DifyRetryConfig retryConfig() { return retryConfig; }
    public String code() { return code; }
    public String language() { return language; }
    public List<DifyVariable> outputVariables() { return outputVariables; }
    public String method() { return method; }
    public String url() { return url; }
    public Map<String, String> headers() { return headers; }
    public Map<String, String> params() { return params; }
    public DifyHttpBody body() { return body; }
    public DifyAuthorization authorization() { return authorization; }
    public List<DifyCase> cases() { return cases; }
    public String outputType() { return outputType; }
    public Object outputs() { return outputs; }
    public String answer() { return answer; }
    public Integer parallelNums() { return parallelNums; }
    public Integer maxConcurrency() { return maxConcurrency; }
    public List<String> iteratorSelector() { return iteratorSelector; }
    public List<String> outputSelector() { return outputSelector; }
    public String startNodeId() { return startNodeId; }
    public String parentId() { return parentId; }
    public String iterationId() { return iterationId; }
    public String loopId() { return loopId; }
    public Boolean isInIteration() { return isInIteration; }
    public Boolean isInLoop() { return isInLoop; }
    public Integer loopCount() { return loopCount; }
    public String template() { return template; }
    public Boolean enable() { return enable; }
    public Map<String, Object> advancedSettings() { return advancedSettings; }
    public List<DifyParameter> parameters() { return parameters; }
    public String resultType() { return resultType; }
    public List<DifyClass> classes() { return classes; }
    public String instruction() { return instruction; }
    public Object providerId() { return providerId; }
    public String providerName() { return providerName; }
    public String providerType() { return providerType; }
    public String toolName() { return toolName; }
    public String toolLabel() { return toolLabel; }
    public Map<String, Object> toolInputParameters() { return toolInputParameters; }
    public Map<String, Object> toolParameters() { return toolParameters; }
    public Map<String, Object> toolConfigurations() { return toolConfigurations; }
    public Boolean isTeamAuthorization() { return isTeamAuthorization; }
    public DifyAgentConfig agent() { return agent; }
    public String agentStrategyLabel() { return agentStrategyLabel; }
    public String agentStrategyName() { return agentStrategyName; }
    public Map<String, Object> agentParameters() { return agentParameters; }
    public List<Map<String, Object>> assignerItems() { return assignerItems; }
    public String version() { return version; }
    public List<Map<String, Object>> breakConditions() { return breakConditions; }
    public Map<String, Object> additionalProperties() { return additionalProperties; }

    /**
     * 把指定 JSON 对象中所有未匹配到类型化字段的 key 合并到 additionalProperties。
     * 用于 DifyYamlParser 在 FastJSON2 解析后回填未知字段（FastJSON2 默认丢弃未匹配字段）。
     */
    public void mergeAdditionalProperties(com.alibaba.fastjson2.JSONObject rawJson) {
        if (rawJson == null) return;
        if (this.additionalProperties == null) {
            this.additionalProperties = new java.util.HashMap<>();
        }
        for (java.util.Map.Entry<String, Object> e : rawJson.entrySet()) {
            if (KNOWN_FIELD_NAMES.contains(e.getKey())) continue;
            this.additionalProperties.put(e.getKey(), e.getValue());
        }
    }

    /** DifyNodeData 中已被 @JSONField 标注的字段名集合（用于过滤 additionalProperties） */
    private static final java.util.Set<String> KNOWN_FIELD_NAMES = new java.util.HashSet<>(java.util.Arrays.asList(
        "type", "title", "desc", "selected",
        "variables", "model", "prompt_template", "vision", "memory", "context",
        "structured_output", "retry_config", "code", "language",
        "input_variables", "output_variables", "method", "url", "headers", "params",
        "body", "authorization", "cases", "output_type", "outputs", "answer",
        "parallel_nums", "max_concurrency", "iterator_selector", "output_selector",
        "start_node_id", "parent_id", "iteration_id", "loop_id",
        "is_in_iteration", "is_in_loop", "loop_count",
        "template", "enable", "advanced_settings", "parameters", "result_type",
        "classes", "instruction", "agent_strategy_name", "agent_parameters",
        "items", "version", "break_conditions"
    ));

    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getDesc() { return desc; }
    public Boolean getSelected() { return selected; }
    public List<DifyVariable> getVariables() { return variables; }
    public DifyModel getModel() { return model; }
    public List<DifyPromptMessage> getPromptTemplate() { return promptTemplate; }
    public DifyVisionConfig getVision() { return vision; }
    public DifyMemoryConfig getMemory() { return memory; }
    public DifyContextConfig getContext() { return context; }
    public DifyStructuredOutputConfig getStructuredOutput() { return structuredOutput; }
    public DifyRetryConfig getRetryConfig() { return retryConfig; }
    public String getCode() { return code; }
    public String getLanguage() { return language; }
    public List<DifyVariable> getInputVariables() { return variables; }
    public List<DifyVariable> getOutputVariables() { return outputVariables; }
    public String getMethod() { return method; }
    public String getUrl() { return url; }
    public Map<String, String> getHeaders() { return headers; }
    public Map<String, String> getParams() { return params; }
    public DifyHttpBody getBody() { return body; }
    public DifyAuthorization getAuthorization() { return authorization; }
    public List<DifyCase> getCases() { return cases; }
    public String getOutputType() { return outputType; }
    public Object getOutputs() { return outputs; }
    public String getAnswer() { return answer; }
    public Integer getParallelNums() { return parallelNums; }
    public Integer getMaxConcurrency() { return maxConcurrency; }
    public List<String> getIteratorSelector() { return iteratorSelector; }
    public List<String> getOutputSelector() { return outputSelector; }
    public String getStartNodeId() { return startNodeId; }
    public String getParentId() { return parentId; }
    public String getIterationId() { return iterationId; }
    public String getLoopId() { return loopId; }
    public Boolean getIsInIteration() { return isInIteration; }
    public Boolean getIsInLoop() { return isInLoop; }
    public Integer getLoopCount() { return loopCount; }
    public String getTemplate() { return template; }
    public List<DifyVariable> getTemplateVariables() { return variables; }
    public Boolean getEnable() { return enable; }
    public Map<String, Object> getAdvancedSettings() { return advancedSettings; }
    public List<DifyParameter> getParameters() { return parameters; }
    public String getResultType() { return resultType; }
    public List<DifyClass> getClasses() { return classes; }
    public String getInstruccion() { return instruction; }
    public Object getProviderId() { return providerId; }
    public String getProviderName() { return providerName; }
    public String getProviderType() { return providerType; }
    public String getToolName() { return toolName; }
    public String getToolLabel() { return toolLabel; }
    public Map<String, Object> getToolInputParameters() { return toolInputParameters; }
    public Map<String, Object> getToolParameters() { return toolParameters; }
    public Map<String, Object> getToolConfigurations() { return toolConfigurations; }
    public Boolean getIsTeamAuthorization() { return isTeamAuthorization; }
    public DifyAgentConfig getAgent() { return agent; }
    public String getAgentStrategyLabel() { return agentStrategyLabel; }
    public String getAgentStrategyName() { return agentStrategyName; }
    public Map<String, Object> getAgentParameters() { return agentParameters; }
    public List<Map<String, Object>> getAssignerItems() { return assignerItems; }
    public String getVersion() { return version; }
    public List<Map<String, Object>> getBreakConditions() { return breakConditions; }
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyNodeData)) return false;
        DifyNodeData that = (DifyNodeData) o;
        return Objects.equals(type, that.type)
                && Objects.equals(title, that.title)
                && Objects.equals(desc, that.desc)
                && Objects.equals(selected, that.selected)
                && Objects.equals(variables, that.variables)
                && Objects.equals(model, that.model)
                && Objects.equals(promptTemplate, that.promptTemplate)
                && Objects.equals(vision, that.vision)
                && Objects.equals(memory, that.memory)
                && Objects.equals(context, that.context)
                && Objects.equals(structuredOutput, that.structuredOutput)
                && Objects.equals(retryConfig, that.retryConfig)
                && Objects.equals(code, that.code)
                && Objects.equals(language, that.language)
                && Objects.equals(inputVariables, that.inputVariables)
                && Objects.equals(outputVariables, that.outputVariables)
                && Objects.equals(method, that.method)
                && Objects.equals(url, that.url)
                && Objects.equals(headers, that.headers)
                && Objects.equals(params, that.params)
                && Objects.equals(body, that.body)
                && Objects.equals(authorization, that.authorization)
                && Objects.equals(cases, that.cases)
                && Objects.equals(outputType, that.outputType)
                && Objects.equals(outputs, that.outputs)
                && Objects.equals(answer, that.answer)
                && Objects.equals(parallelNums, that.parallelNums)
                && Objects.equals(maxConcurrency, that.maxConcurrency)
                && Objects.equals(iteratorSelector, that.iteratorSelector)
                && Objects.equals(outputSelector, that.outputSelector)
                && Objects.equals(startNodeId, that.startNodeId)
                && Objects.equals(parentId, that.parentId)
                && Objects.equals(iterationId, that.iterationId)
                && Objects.equals(loopId, that.loopId)
                && Objects.equals(isInIteration, that.isInIteration)
                && Objects.equals(isInLoop, that.isInLoop)
                && Objects.equals(loopCount, that.loopCount)
                && Objects.equals(template, that.template)
                && Objects.equals(enable, that.enable)
                && Objects.equals(advancedSettings, that.advancedSettings)
                && Objects.equals(parameters, that.parameters)
                && Objects.equals(resultType, that.resultType)
                && Objects.equals(classes, that.classes)
                && Objects.equals(instruction, that.instruction)
                && Objects.equals(providerId, that.providerId)
                && Objects.equals(providerName, that.providerName)
                && Objects.equals(providerType, that.providerType)
                && Objects.equals(toolName, that.toolName)
                && Objects.equals(toolLabel, that.toolLabel)
                && Objects.equals(toolInputParameters, that.toolInputParameters)
                && Objects.equals(toolParameters, that.toolParameters)
                && Objects.equals(toolConfigurations, that.toolConfigurations)
                && Objects.equals(isTeamAuthorization, that.isTeamAuthorization)
                && Objects.equals(agent, that.agent)
                && Objects.equals(agentStrategyLabel, that.agentStrategyLabel)
                && Objects.equals(agentStrategyName, that.agentStrategyName)
                && Objects.equals(agentParameters, that.agentParameters)
                && Objects.equals(assignerItems, that.assignerItems)
                && Objects.equals(version, that.version)
                && Objects.equals(breakConditions, that.breakConditions)
                && Objects.equals(additionalProperties, that.additionalProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, title, desc, selected, variables, model, promptTemplate, vision, memory,
                context, structuredOutput, retryConfig, code, language, inputVariables, outputVariables,
                method, url, headers, params, body, authorization, cases, outputType, outputs, answer,
                parallelNums, maxConcurrency, iteratorSelector, outputSelector, startNodeId, parentId,
                iterationId, loopId, isInIteration, isInLoop, loopCount, template, enable, advancedSettings,
                parameters, resultType, classes, instruction, providerId, providerName, providerType,
                toolName, toolLabel, toolInputParameters, toolParameters, toolConfigurations, isTeamAuthorization,
                agent, agentStrategyLabel, agentStrategyName, agentParameters, assignerItems, version,
                breakConditions, additionalProperties);
    }

    /**
     * 创建 {@link DifyNodeData} 的链式 Builder。
     *
     * <p>使用示例:</p>
     * <pre>{@code
     * DifyNodeData data = DifyNodeData.builder()
     *     .type("code")
     *     .title("代码执行")
     *     .code("function main() { return 1; }")
     *     .language("javascript")
     *     .build();
     * }</pre>
     *
     * @return 新的 Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link DifyNodeData} 链式 Builder,所有字段默认为 null。
     * 通过对应的 setter 方法设置字段,最后调用 {@link #build()} 完成构造。
     * Builder 实例非线程安全,不要跨线程共享。
     */
    public static final class Builder {
        private String type;
        private String title;
        private String desc;
        private Boolean selected;
        private List<DifyVariable> variables;
        private DifyModel model;
        private List<DifyPromptMessage> promptTemplate;
        private DifyVisionConfig vision;
        private DifyMemoryConfig memory;
        private DifyContextConfig context;
        private DifyStructuredOutputConfig structuredOutput;
        private DifyRetryConfig retryConfig;
        private String code;
        private String language;
        private List<DifyVariable> inputVariables;
        private List<DifyVariable> outputVariables;
        private String method;
        private String url;
        private Map<String, String> headers;
        private Map<String, String> params;
        private DifyHttpBody body;
        private DifyAuthorization authorization;
        private List<DifyCase> cases;
        private String outputType;
        private Object outputs;
        private String answer;
        private Integer parallelNums;
        private Integer maxConcurrency;
        private List<String> iteratorSelector;
        private List<String> outputSelector;
        private String startNodeId;
        private String parentId;
        private String iterationId;
        private String loopId;
        private Boolean isInIteration;
        private Boolean isInLoop;
        private Integer loopCount;
        private String template;
        private Boolean enable;
        private Map<String, Object> advancedSettings;
        private List<DifyParameter> parameters;
        private String resultType;
        private List<DifyClass> classes;
        private String instruction;
        private Object providerId;
        private String providerName;
        private String providerType;
        private String toolName;
        private String toolLabel;
        private Map<String, Object> toolInputParameters;
        private Map<String, Object> toolParameters;
        private Map<String, Object> toolConfigurations;
        private Boolean isTeamAuthorization;
        private DifyAgentConfig agent;
        private String agentStrategyLabel;
        private String agentStrategyName;
        private Map<String, Object> agentParameters;
        private List<Map<String, Object>> assignerItems;
        private String version;
        private List<Map<String, Object>> breakConditions;
        private Map<String, Object> additionalProperties;

        public Builder type(String type) { this.type = type; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder desc(String desc) { this.desc = desc; return this; }
        public Builder selected(Boolean selected) { this.selected = selected; return this; }
        public Builder variables(List<DifyVariable> variables) { this.variables = variables; return this; }
        public Builder model(DifyModel model) { this.model = model; return this; }
        public Builder promptTemplate(List<DifyPromptMessage> promptTemplate) { this.promptTemplate = promptTemplate; return this; }
        public Builder vision(DifyVisionConfig vision) { this.vision = vision; return this; }
        public Builder memory(DifyMemoryConfig memory) { this.memory = memory; return this; }
        public Builder context(DifyContextConfig context) { this.context = context; return this; }
        public Builder structuredOutput(DifyStructuredOutputConfig structuredOutput) { this.structuredOutput = structuredOutput; return this; }
        public Builder retryConfig(DifyRetryConfig retryConfig) { this.retryConfig = retryConfig; return this; }
        public Builder code(String code) { this.code = code; return this; }
        public Builder language(String language) { this.language = language; return this; }
        public Builder inputVariables(List<DifyVariable> inputVariables) { this.inputVariables = inputVariables; return this; }
        public Builder outputVariables(List<DifyVariable> outputVariables) { this.outputVariables = outputVariables; return this; }
        public Builder method(String method) { this.method = method; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder headers(Map<String, String> headers) { this.headers = headers; return this; }
        public Builder params(Map<String, String> params) { this.params = params; return this; }
        public Builder body(DifyHttpBody body) { this.body = body; return this; }
        public Builder authorization(DifyAuthorization authorization) { this.authorization = authorization; return this; }
        public Builder cases(List<DifyCase> cases) { this.cases = cases; return this; }
        public Builder outputType(String outputType) { this.outputType = outputType; return this; }
        public Builder outputs(Object outputs) { this.outputs = outputs; return this; }
        public Builder answer(String answer) { this.answer = answer; return this; }
        public Builder parallelNums(Integer parallelNums) { this.parallelNums = parallelNums; return this; }
        public Builder maxConcurrency(Integer maxConcurrency) { this.maxConcurrency = maxConcurrency; return this; }
        public Builder iteratorSelector(List<String> iteratorSelector) { this.iteratorSelector = iteratorSelector; return this; }
        public Builder outputSelector(List<String> outputSelector) { this.outputSelector = outputSelector; return this; }
        public Builder startNodeId(String startNodeId) { this.startNodeId = startNodeId; return this; }
        public Builder parentId(String parentId) { this.parentId = parentId; return this; }
        public Builder iterationId(String iterationId) { this.iterationId = iterationId; return this; }
        public Builder loopId(String loopId) { this.loopId = loopId; return this; }
        public Builder isInIteration(Boolean isInIteration) { this.isInIteration = isInIteration; return this; }
        public Builder isInLoop(Boolean isInLoop) { this.isInLoop = isInLoop; return this; }
        public Builder loopCount(Integer loopCount) { this.loopCount = loopCount; return this; }
        public Builder template(String template) { this.template = template; return this; }
        public Builder enable(Boolean enable) { this.enable = enable; return this; }
        public Builder advancedSettings(Map<String, Object> advancedSettings) { this.advancedSettings = advancedSettings; return this; }
        public Builder parameters(List<DifyParameter> parameters) { this.parameters = parameters; return this; }
        public Builder resultType(String resultType) { this.resultType = resultType; return this; }
        public Builder classes(List<DifyClass> classes) { this.classes = classes; return this; }
        public Builder instruction(String instruction) { this.instruction = instruction; return this; }
        public Builder providerId(Object providerId) { this.providerId = providerId; return this; }
        public Builder providerName(String providerName) { this.providerName = providerName; return this; }
        public Builder providerType(String providerType) { this.providerType = providerType; return this; }
        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder toolLabel(String toolLabel) { this.toolLabel = toolLabel; return this; }
        public Builder toolInputParameters(Map<String, Object> toolInputParameters) { this.toolInputParameters = toolInputParameters; return this; }
        public Builder toolParameters(Map<String, Object> toolParameters) { this.toolParameters = toolParameters; return this; }
        public Builder toolConfigurations(Map<String, Object> toolConfigurations) { this.toolConfigurations = toolConfigurations; return this; }
        public Builder isTeamAuthorization(Boolean isTeamAuthorization) { this.isTeamAuthorization = isTeamAuthorization; return this; }
        public Builder agent(DifyAgentConfig agent) { this.agent = agent; return this; }
        public Builder agentStrategyLabel(String agentStrategyLabel) { this.agentStrategyLabel = agentStrategyLabel; return this; }
        public Builder agentStrategyName(String agentStrategyName) { this.agentStrategyName = agentStrategyName; return this; }
        public Builder agentParameters(Map<String, Object> agentParameters) { this.agentParameters = agentParameters; return this; }
        public Builder assignerItems(List<Map<String, Object>> assignerItems) { this.assignerItems = assignerItems; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder breakConditions(List<Map<String, Object>> breakConditions) { this.breakConditions = breakConditions; return this; }
        public Builder additionalProperties(Map<String, Object> additionalProperties) { this.additionalProperties = additionalProperties; return this; }

        /**
         * 使用当前 Builder 状态构造 {@link DifyNodeData}。
         * 调用后 Builder 实例仍可继续使用以创建多个对象(非线程安全)。
         *
         * @return 新的 DifyNodeData 实例
         */
        public DifyNodeData build() {
            return new DifyNodeData(
                type, title, desc, selected,
                variables, model, promptTemplate,
                vision, memory, context, structuredOutput, retryConfig,
                code, language, inputVariables, outputVariables,
                method, url, headers, params, body, authorization,
                cases, outputType, outputs, answer,
                parallelNums, maxConcurrency,
                iteratorSelector, outputSelector, startNodeId,
                parentId, iterationId, loopId,
                isInIteration, isInLoop, loopCount,
                template, enable, advancedSettings,
                parameters, resultType, classes, instruction,
                providerId, providerName, providerType, toolName, toolLabel,
                toolInputParameters, toolParameters, toolConfigurations, isTeamAuthorization,
                agent, agentStrategyLabel, agentStrategyName, agentParameters,
                assignerItems, version, breakConditions,
                additionalProperties);
        }
    }
}
