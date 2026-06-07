package com.dify.workflow.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 锁定 DifyVariablePool 的桶隔离契约,防止"set(\"env\", ...) 应该写到 environmentVariables"
 * 这类隐式约定被无意打破。
 *
 * <p>核心不变量:
 * <ul>
 *   <li>{@code setEnvironment} 写 environmentVariables 桶,读用 {@code getEnvironment}</li>
 *   <li>{@code set("env", ...)} 写 nodeOutputs["env"] 桶,与 environmentVariables 永不相交</li>
 *   <li>{@code copy()} 隔离两份独立的 environmentVariables 状态</li>
 * </ul>
 */
class DifyVariablePoolTest {

    @Test
    @DisplayName("setEnvironment 写入 environmentVariables 桶,可被 getEnvironment 读到")
    void setEnvironment_writesToEnvironmentMap_andIsReadableViaGetEnvironment() {
        DifyVariablePool pool = new DifyVariablePool();
        pool.setEnvironment("host", "https://example.com");
        // 关键路径:env 变量必须能从 environmentVariables 桶读到
        assertEquals("https://example.com", pool.getEnvironment("host"));
        // 锁定:setEnvironment 不能意外写入 nodeOutputs["env"]
        assertNull(pool.get("env", "host"),
                "setEnvironment 不应污染 nodeOutputs 桶,只走 environmentVariables 专用通道");
    }

    @Test
    @DisplayName("getEnvironment 未配置时返回 null")
    void getEnvironment_returnsNullForMissingKey() {
        DifyVariablePool pool = new DifyVariablePool();
        assertNull(pool.getEnvironment("missing"));
    }

    @Test
    @DisplayName("setEnvironment 重复写同 key 会覆盖")
    void setEnvironment_overwritesExistingValue() {
        DifyVariablePool pool = new DifyVariablePool();
        pool.setEnvironment("k", "v1");
        pool.setEnvironment("k", "v2");
        assertEquals("v2", pool.getEnvironment("k"));
    }

    @Test
    @DisplayName("旧 API setEnvironmentVariable 仍然可用(向后兼容)")
    void setEnvironment_legacyMethodStillWorks() {
        DifyVariablePool pool = new DifyVariablePool();
        pool.setEnvironmentVariable("k", "v");
        // 旧 API 写的是同一桶(environmentVariables),仍能被 getEnvironment 读出
        assertEquals("v", pool.getEnvironment("k"));
    }

    @Test
    @DisplayName("锁定:set(\"env\", ...) 写 nodeOutputs,不泄漏到 environmentVariables,防回归")
    void set_writesToNodeOutputsAndIsNotVisibleViaGetEnvironment() {
        DifyVariablePool pool = new DifyVariablePool();
        pool.set("env", "host", "from-set");
        // 通用 set 走的是 nodeOutputs 桶
        assertEquals("from-set", pool.get("env", "host"));
        // 但 getEnvironment 读的是 environmentVariables 桶,不应回退到 nodeOutputs
        assertNull(pool.getEnvironment("host"),
                "set(\"env\", ...) 写入 nodeOutputs 后,getEnvironment 不能回退到该桶 — " +
                        "本次修复前 DifyWorkflowExecutor 就栽在这上面");
    }

    @Test
    @DisplayName("copy() 隔离 environmentVariables,修改副本不影响原池")
    void copy_isolatesEnvironmentVariablesAcrossInstances() {
        DifyVariablePool pool = new DifyVariablePool();
        pool.setEnvironment("host", "v");
        DifyVariablePool clone = pool.copy();
        clone.setEnvironment("host", "modified");
        assertEquals("v", pool.getEnvironment("host"),
                "原池的 env 变量应保持不变(迭代隔离)");
        assertEquals("modified", clone.getEnvironment("host"),
                "副本的 env 变量应独立可改");
    }
}
