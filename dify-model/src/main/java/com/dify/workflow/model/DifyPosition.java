package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Objects;

/**
 * Node position in the workflow graph UI.
 */
public final class DifyPosition {
    @JSONField(name = "x")
    private final Double x;
    @JSONField(name = "y")
    private final Double y;

    public DifyPosition(Double x, Double y) {
        this.x = x;
        this.y = y;
    }

    public Double x() { return x; }
    public Double y() { return y; }

    public Double getX() { return x; }
    public Double getY() { return y; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyPosition)) return false;
        DifyPosition that = (DifyPosition) o;
        return Objects.equals(x, that.x) && Objects.equals(y, that.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return String.format("DifyPosition[x=%s, y=%s]", x, y);
    }
}
