package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answer node - outputs a fixed answer.
 * Useful for returning constant responses.
 */
public class AnswerNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(AnswerNode.class);

    public AnswerNode(String id, DifyNodeData data) {
        super(id, "answer", data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing answer node: {}", id);

        String answer = data.answer();
        if (answer != null && !answer.isEmpty()) {
            // 解析模板中的变量引用
            String resolvedAnswer = resolveVariables(context, answer);
            // 节点级输出：按节点 ID 隔离，互不覆盖
            context.setVariable(id, "answer", resolvedAnswer);
            // 工作流级输出：累积所有 answer 节点的内容（与 Dify 流式拼接语义一致）
            // Dify 高级 chat pipeline 通过 self._task_state.answer += delta_text 累加
            // 这里用 \n 分隔多个 answer 节点的内容
            Object existing = context.getOutputs().get("answer");
            if (existing == null) {
                context.setOutput("answer", resolvedAnswer);
            } else {
                context.setOutput("answer", existing + "\n" + resolvedAnswer);
            }
            log.debug("Answer node output: {}", resolvedAnswer);
        }
    }
}
