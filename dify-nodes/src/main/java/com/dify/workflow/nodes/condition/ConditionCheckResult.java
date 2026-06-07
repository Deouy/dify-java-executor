package com.dify.workflow.nodes.condition;

import java.util.List;
import java.util.Map;

/**
 * If-Else 条件评估结果。
 *
 * <p>对齐 Dify 官方 {@code ConditionCheckResult} NamedTuple:
 * <pre>{@code
 *   ConditionCheckResult(input_conditions, group_results, final_result)
 * }</pre>
 * </p>
 *
 * <p>注:项目 pom 锁定 Java 1.8,不能用 {@code record} 语法,改为 final class + 显式 getter。
 * getter 命名延续 record 风格({@code inputs()} / {@code groupResults()} / {@code finalResult()})
 * 以免改 {@code ConditionProcessor} 调用方。</p>
 */
public final class ConditionCheckResult {

    private final List<Map<String, Object>> inputs;
    private final List<Boolean> groupResults;
    private final boolean finalResult;

    public ConditionCheckResult(List<Map<String, Object>> inputs,
                                List<Boolean> groupResults,
                                boolean finalResult) {
        this.inputs = inputs;
        this.groupResults = groupResults;
        this.finalResult = finalResult;
    }

    public List<Map<String, Object>> inputs() {
        return inputs;
    }

    public List<Boolean> groupResults() {
        return groupResults;
    }

    public boolean finalResult() {
        return finalResult;
    }
}
