package com.dify.workflow.model.node;
import com.alibaba.fastjson2.annotation.JSONField;

/**
 * System variable prefixes used in Dify workflows.
 */
public final class SystemVariable {

    private SystemVariable() {}

    // System variable prefix
    public static final String SYS = "sys";

    // Environment variable prefix
    public static final String ENV = "env";

    // Conversation variable prefix
    public static final String CONVERSATION = "conversation";

    // RAG pipeline variable prefix
    public static final String RAG = "rag";

    // System variable names
    public static final String SYS_QUERY = "query";
    public static final String SYS_FILES = "files";
    public static final String SYS_CONVERSATION_ID = "conversation_id";
    public static final String SYS_USER_ID = "user_id";
    public static final String SYS_DIALOGUE_COUNT = "dialogue_count";
    public static final String SYS_APP_ID = "app_id";
    public static final String SYS_WORKFLOW_ID = "workflow_id";
    public static final String SYS_WORKFLOW_EXECUTION_ID = "workflow_run_id";
    public static final String SYS_TIMESTAMP = "timestamp";
}
