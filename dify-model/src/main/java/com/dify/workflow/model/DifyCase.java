package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Objects;

/**
 * Dify if-else case definition.
 */
public final class DifyCase {
    @JSONField(name = "case_id")
    private final String caseId;
    @JSONField(name = "conditions")
    private final List<DifyCondition> conditions;
    @JSONField(name = "logical_operator")
    private final String logicalOperator;
    @JSONField(name = "id")
    private final String id;

    public DifyCase(String caseId, List<DifyCondition> conditions, String logicalOperator, String id) {
        this.caseId = caseId;
        this.conditions = conditions;
        this.logicalOperator = logicalOperator;
        this.id = id;
    }

    public String caseId() { return caseId; }
    public List<DifyCondition> conditions() { return conditions; }
    public String logicalOperator() { return logicalOperator; }
    public String id() { return id; }

    public String getCaseId() { return caseId; }
    public List<DifyCondition> getConditions() { return conditions; }
    public String getLogicalOperator() { return logicalOperator; }
    public String getId() { return id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyCase)) return false;
        DifyCase that = (DifyCase) o;
        return Objects.equals(caseId, that.caseId)
                && Objects.equals(conditions, that.conditions)
                && Objects.equals(logicalOperator, that.logicalOperator)
                && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseId, conditions, logicalOperator, id);
    }

    @Override
    public String toString() {
        return String.format("DifyCase[caseId=%s, conditions=%s, logicalOperator=%s, id=%s]",
                caseId, conditions, logicalOperator, id);
    }
}
