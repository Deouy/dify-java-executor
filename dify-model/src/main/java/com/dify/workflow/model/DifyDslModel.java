package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Objects;

/**
 * Dify DSL root model.
 */
public final class DifyDslModel {
    @JSONField(name = "kind")
    private final String kind;
    @JSONField(name = "version")
    private final String version;
    @JSONField(name = "app")
    private final DifyApp app;
    @JSONField(name = "workflow")
    private final DifyWorkflow workflow;

    public DifyDslModel(String kind, String version, DifyApp app, DifyWorkflow workflow) {
        this.kind = kind;
        this.version = version;
        this.app = app;
        this.workflow = workflow;
    }

    public String kind() { return kind; }
    public String version() { return version; }
    public DifyApp app() { return app; }
    public DifyWorkflow workflow() { return workflow; }

    public String getKind() { return kind; }
    public String getVersion() { return version; }
    public DifyApp getApp() { return app; }
    public DifyWorkflow getWorkflow() { return workflow; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyDslModel)) return false;
        DifyDslModel that = (DifyDslModel) o;
        return Objects.equals(kind, that.kind)
                && Objects.equals(version, that.version)
                && Objects.equals(app, that.app)
                && Objects.equals(workflow, that.workflow);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, version, app, workflow);
    }

    @Override
    public String toString() {
        return String.format("DifyDslModel[kind=%s, version=%s, app=%s, workflow=%s]", kind, version, app, workflow);
    }
}
