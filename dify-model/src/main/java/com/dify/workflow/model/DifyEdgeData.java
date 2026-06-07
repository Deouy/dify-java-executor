package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Objects;

/**
 * Dify edge data containing metadata about the connection.
 */
public final class DifyEdgeData {
    @JSONField(name = "isInIteration")
    private final Boolean isInIteration;
    @JSONField(name = "isInLoop")
    private final Boolean isInLoop;
    @JSONField(name = "sourceType")
    private final String sourceType;
    @JSONField(name = "targetType")
    private final String targetType;

    public DifyEdgeData(Boolean isInIteration, Boolean isInLoop, String sourceType, String targetType) {
        this.isInIteration = isInIteration != null ? isInIteration : false;
        this.isInLoop = isInLoop != null ? isInLoop : false;
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    public Boolean isInIteration() { return isInIteration; }
    public Boolean isInLoop() { return isInLoop; }
    public String sourceType() { return sourceType; }
    public String targetType() { return targetType; }

    public Boolean getIsInIteration() { return isInIteration; }
    public Boolean getIsInLoop() { return isInLoop; }
    public String getSourceType() { return sourceType; }
    public String getTargetType() { return targetType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyEdgeData)) return false;
        DifyEdgeData that = (DifyEdgeData) o;
        return Objects.equals(isInIteration, that.isInIteration)
                && Objects.equals(isInLoop, that.isInLoop)
                && Objects.equals(sourceType, that.sourceType)
                && Objects.equals(targetType, that.targetType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isInIteration, isInLoop, sourceType, targetType);
    }

    @Override
    public String toString() {
        return String.format("DifyEdgeData[isInIteration=%s, isInLoop=%s, sourceType=%s, targetType=%s]",
                isInIteration, isInLoop, sourceType, targetType);
    }
}
