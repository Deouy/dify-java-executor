package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyCase;
import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import com.dify.workflow.nodes.condition.ConditionCheckResult;
import com.dify.workflow.nodes.condition.ConditionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * If-Else 节点。
 *
 * <p>按 Dify 官方语义(case 顺序评估 + 短路)对条件求值,选中第一个通过的 case,
 * 写入 {@code _selected_case} 供下游边路由匹配 {@code sourceHandle}。</p>
 *
 * <p>实际条件求值委托给 {@link ConditionProcessor},本节点仅负责:
 * <ul>
 *   <li>遍历 cases(顺序求值)</li>
 *   <li>短路退出(首个 finalResult=true 即停)</li>
 *   <li>无任何 case 匹配时设 {@code _selected_case = "false"}</li>
 *   <li>兼容保留 {@code case_<id>_result} 变量(供历史测试断言)</li>
 * </ul>
 */
public class IfElseNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(IfElseNode.class);

    public IfElseNode(String id, DifyNodeData data) {
        super(id, NodeType.IF_ELSE, data);
    }

    @Override
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing if-else node: {}", id);

        List<DifyCase> cases = data.cases();
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("If-else node has no cases: " + id);
        }

        ConditionProcessor processor = new ConditionProcessor();
        String selectedCaseId = "false"; // 默认分支(Dify 官方约定)

        for (DifyCase caseItem : cases) {
            String caseId = caseId(caseItem);
            boolean matched = false;

            try {
                ConditionCheckResult result = processor.processConditions(
                    context, caseItem.conditions(), caseItem.logicalOperator());
                matched = result.finalResult();
            } catch (IllegalArgumentException e) {
                // 对齐官方"异常被 try/except 吞掉,节点继续"的语义
                log.warn("If-else condition evaluation failed for case '{}': {}",
                    caseId, e.getMessage());
            }

            // 兼容旧测试断言:每个 case 都写自己的 result 变量
            context.setVariable(id, "case_" + caseId + "_result", matched);

            if (matched) {
                log.debug("If-else node: case '{}' evaluated to true", caseId);
                selectedCaseId = caseId;
                break; // 短路:首个匹配 case 胜出
            }
        }

        if ("false".equals(selectedCaseId)) {
            log.debug("If-else node: no case matched, defaulting to 'false' branch");
        }
        context.setVariable(id, "_selected_case", selectedCaseId);
    }

    /**
     * 提取 case_id。优先用 {@code caseId()},回退到 {@code id()},最后兜底 {@code "unknown"}。
     */
    private String caseId(DifyCase caseItem) {
        if (caseItem.caseId() != null) {
            return caseItem.caseId();
        }
        return caseItem.id() != null ? caseItem.id() : "unknown";
    }
}
