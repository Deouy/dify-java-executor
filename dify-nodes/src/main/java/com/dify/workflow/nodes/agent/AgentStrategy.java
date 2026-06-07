package com.dify.workflow.nodes.agent;

/**
 * Agent 策略接口。
 * 定义 Agent 节点的不同执行策略：FunctionCall / ReAct / MCP。
 */
public interface AgentStrategy {

    /**
     * 执行 Agent 策略。
     *
     * @param context       执行上下文
     * @param query        用户查询
     * @param systemPrompt 系统提示词
     * @return Agent 输出结果
     */
    String execute(AgentExecutionContext context, String query, String systemPrompt);
}
