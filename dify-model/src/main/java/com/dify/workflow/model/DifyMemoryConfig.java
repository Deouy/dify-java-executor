package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Map;
import java.util.Objects;

/**
 * LLM 节点 memory 配置。
 * 参考 Dify 的 MemoryConfig (graphon.prompt_entities)。
 */
public final class DifyMemoryConfig {
    @JSONField(name = "window")
    private final Map<String, Object> window;
    @JSONField(name = "role_prefix")
    private final RolePrefix rolePrefix;
    @JSONField(name = "query_prompt_template")
    private final String queryPromptTemplate;

    public DifyMemoryConfig(Map<String, Object> window, RolePrefix rolePrefix, String queryPromptTemplate) {
        this.window = window;
        this.rolePrefix = rolePrefix;
        this.queryPromptTemplate = queryPromptTemplate;
    }

    public Map<String, Object> window() { return window; }
    public RolePrefix rolePrefix() { return rolePrefix; }
    public String queryPromptTemplate() { return queryPromptTemplate; }

    public Map<String, Object> getWindow() { return window; }
    public RolePrefix getRolePrefix() { return rolePrefix; }
    public String getQueryPromptTemplate() { return queryPromptTemplate; }

    /** 判断是否启用记忆窗口 */
    public boolean isWindowEnabled() {
        return window != null && Boolean.TRUE.equals(window.get("enabled"));
    }

    /** 获取记忆窗口大小 */
    public int windowSize() {
        if (window == null) return 0;
        Object size = window.get("size");
        return size instanceof Number ? ((Number) size).intValue() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyMemoryConfig)) return false;
        DifyMemoryConfig that = (DifyMemoryConfig) o;
        return Objects.equals(window, that.window)
                && Objects.equals(rolePrefix, that.rolePrefix)
                && Objects.equals(queryPromptTemplate, that.queryPromptTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(window, rolePrefix, queryPromptTemplate);
    }

    @Override
    public String toString() {
        return String.format("DifyMemoryConfig[window=%s, rolePrefix=%s, queryPromptTemplate=%s]",
                window, rolePrefix, queryPromptTemplate);
    }

    /**
     * Role prefix for memory messages.
     */
    public static final class RolePrefix {
        @JSONField(name = "user")
        private final String user;
        @JSONField(name = "assistant")
        private final String assistant;

        public RolePrefix(String user, String assistant) {
            this.user = user;
            this.assistant = assistant;
        }

        public String user() { return user; }
        public String assistant() { return assistant; }

        public String getUser() { return user; }
        public String getAssistant() { return assistant; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RolePrefix)) return false;
            RolePrefix that = (RolePrefix) o;
            return Objects.equals(user, that.user) && Objects.equals(assistant, that.assistant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, assistant);
        }

        @Override
        public String toString() {
            return String.format("RolePrefix[user=%s, assistant=%s]", user, assistant);
        }
    }
}
