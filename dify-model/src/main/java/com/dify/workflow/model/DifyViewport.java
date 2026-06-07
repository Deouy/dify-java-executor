package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Objects;

/**
 * Dify viewport configuration for UI.
 */
public final class DifyViewport {
    @JSONField(name = "x")
    private final Double x;
    @JSONField(name = "y")
    private final Double y;
    @JSONField(name = "zoom")
    private final Double zoom;

    public DifyViewport(Double x, Double y, Double zoom) {
        this.x = x;
        this.y = y;
        this.zoom = zoom;
    }

    public Double x() { return x; }
    public Double y() { return y; }
    public Double zoom() { return zoom; }

    public Double getX() { return x; }
    public Double getY() { return y; }
    public Double getZoom() { return zoom; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyViewport)) return false;
        DifyViewport that = (DifyViewport) o;
        return Objects.equals(x, that.x)
                && Objects.equals(y, that.y)
                && Objects.equals(zoom, that.zoom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, zoom);
    }

    @Override
    public String toString() {
        return String.format("DifyViewport[x=%s, y=%s, zoom=%s]", x, y, zoom);
    }
}
