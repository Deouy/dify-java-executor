package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Objects;

/**
 * Dify workflow node definition.
 */
public final class DifyNode {
    @JSONField(name = "id")
    private final String id;
    @JSONField(name = "type")
    private final String type;
    @JSONField(name = "data")
    private final DifyNodeData data;
    @JSONField(name = "width")
    private final Integer width;
    @JSONField(name = "height")
    private final Integer height;
    @JSONField(name = "position")
    private final DifyPosition position;
    @JSONField(name = "positionAbsolute")
    private final DifyPosition positionAbsolute;
    @JSONField(name = "selected")
    private final Boolean selected;
    @JSONField(name = "sourcePosition")
    private final String sourcePosition;
    @JSONField(name = "targetPosition")
    private final String targetPosition;
    @JSONField(name = "zIndex")
    private final Integer zIndex;

    public DifyNode(String id, String type, DifyNodeData data, Integer width, Integer height,
                    DifyPosition position, DifyPosition positionAbsolute, Boolean selected,
                    String sourcePosition, String targetPosition, Integer zIndex) {
        this.id = id;
        this.type = type;
        this.data = data;
        this.width = width != null ? width : 244;
        this.height = height != null ? height : 90;
        this.position = position;
        this.positionAbsolute = positionAbsolute;
        this.selected = selected != null ? selected : false;
        this.sourcePosition = sourcePosition;
        this.targetPosition = targetPosition;
        this.zIndex = zIndex;
    }

    public String id() { return id; }
    public String type() { return type; }
    public DifyNodeData data() { return data; }
    public Integer width() { return width; }
    public Integer height() { return height; }
    public DifyPosition position() { return position; }
    public DifyPosition positionAbsolute() { return positionAbsolute; }
    public Boolean selected() { return selected; }
    public String sourcePosition() { return sourcePosition; }
    public String targetPosition() { return targetPosition; }
    public Integer zIndex() { return zIndex; }

    public String getId() { return id; }
    public String getType() { return type; }
    public DifyNodeData getData() { return data; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public DifyPosition getPosition() { return position; }
    public DifyPosition getPositionAbsolute() { return positionAbsolute; }
    public Boolean getSelected() { return selected; }
    public String getSourcePosition() { return sourcePosition; }
    public String getTargetPosition() { return targetPosition; }
    public Integer getZIndex() { return zIndex; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyNode)) return false;
        DifyNode that = (DifyNode) o;
        return Objects.equals(id, that.id)
                && Objects.equals(type, that.type)
                && Objects.equals(data, that.data)
                && Objects.equals(width, that.width)
                && Objects.equals(height, that.height)
                && Objects.equals(position, that.position)
                && Objects.equals(positionAbsolute, that.positionAbsolute)
                && Objects.equals(selected, that.selected)
                && Objects.equals(sourcePosition, that.sourcePosition)
                && Objects.equals(targetPosition, that.targetPosition)
                && Objects.equals(zIndex, that.zIndex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, data, width, height, position, positionAbsolute,
                selected, sourcePosition, targetPosition, zIndex);
    }

    @Override
    public String toString() {
        return String.format("DifyNode[id=%s, type=%s, data=%s, width=%s, height=%s, position=%s, positionAbsolute=%s, selected=%s, sourcePosition=%s, targetPosition=%s, zIndex=%s]",
                id, type, data, width, height, position, positionAbsolute, selected, sourcePosition, targetPosition, zIndex);
    }
}
