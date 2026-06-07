package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Objects;

/**
 * Dify workflow graph structure containing nodes and edges.
 */
public final class DifyGraph {
    @JSONField(name = "nodes")
    private final List<DifyNode> nodes;
    @JSONField(name = "edges")
    private final List<DifyEdge> edges;
    @JSONField(name = "viewport")
    private final DifyViewport viewport;

    public DifyGraph(List<DifyNode> nodes, List<DifyEdge> edges, DifyViewport viewport) {
        this.nodes = nodes;
        this.edges = edges;
        this.viewport = viewport;
    }

    public List<DifyNode> nodes() { return nodes; }
    public List<DifyEdge> edges() { return edges; }
    public DifyViewport viewport() { return viewport; }

    public List<DifyNode> getNodes() { return nodes; }
    public List<DifyEdge> getEdges() { return edges; }
    public DifyViewport getViewport() { return viewport; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyGraph)) return false;
        DifyGraph that = (DifyGraph) o;
        return Objects.equals(nodes, that.nodes)
                && Objects.equals(edges, that.edges)
                && Objects.equals(viewport, that.viewport);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, edges, viewport);
    }

    @Override
    public String toString() {
        return String.format("DifyGraph[nodes=%s, edges=%s, viewport=%s]", nodes, edges, viewport);
    }
}
