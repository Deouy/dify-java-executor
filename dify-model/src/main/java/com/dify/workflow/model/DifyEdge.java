package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Objects;

/**
 * Dify workflow edge definition connecting two nodes.
 */
public final class DifyEdge {
    @JSONField(name = "id")
    private final String id;
    @JSONField(name = "source")
    private final String source;
    @JSONField(name = "target")
    private final String target;
    @JSONField(name = "sourceHandle")
    private final String sourceHandle;
    @JSONField(name = "targetHandle")
    private final String targetHandle;
    @JSONField(name = "type")
    private final String type;
    @JSONField(name = "data")
    private final DifyEdgeData data;
    @JSONField(name = "zIndex")
    private final Integer zIndex;

    public DifyEdge(String id, String source, String target, String sourceHandle, String targetHandle,
                     String type, DifyEdgeData data, Integer zIndex) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.sourceHandle = sourceHandle;
        this.targetHandle = targetHandle;
        this.type = type;
        this.data = data;
        this.zIndex = zIndex;
    }

    public String id() { return id; }
    public String source() { return source; }
    public String target() { return target; }
    public String sourceHandle() { return sourceHandle; }
    public String targetHandle() { return targetHandle; }
    public String type() { return type; }
    public DifyEdgeData data() { return data; }
    public Integer zIndex() { return zIndex; }

    public String getId() { return id; }
    public String getSource() { return source; }
    public String getTarget() { return target; }
    public String getSourceHandle() { return sourceHandle; }
    public String getTargetHandle() { return targetHandle; }
    public String getType() { return type; }
    public DifyEdgeData getData() { return data; }
    public Integer getZIndex() { return zIndex; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyEdge)) return false;
        DifyEdge that = (DifyEdge) o;
        return Objects.equals(id, that.id)
                && Objects.equals(source, that.source)
                && Objects.equals(target, that.target)
                && Objects.equals(sourceHandle, that.sourceHandle)
                && Objects.equals(targetHandle, that.targetHandle)
                && Objects.equals(type, that.type)
                && Objects.equals(data, that.data)
                && Objects.equals(zIndex, that.zIndex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, source, target, sourceHandle, targetHandle, type, data, zIndex);
    }

    @Override
    public String toString() {
        return String.format("DifyEdge[id=%s, source=%s, target=%s, sourceHandle=%s, targetHandle=%s, type=%s, data=%s, zIndex=%s]",
                id, source, target, sourceHandle, targetHandle, type, data, zIndex);
    }
}
