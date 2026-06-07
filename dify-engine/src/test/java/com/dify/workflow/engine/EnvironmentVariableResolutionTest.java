package com.dify.workflow.engine;

import com.dify.workflow.engine.llm.LlmProviderFactory;
import com.dify.workflow.model.DifyDslModel;
import com.dify.workflow.parser.DifyYamlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端验证 {{#env.X#}} 解析链: YAML environment_variables →
 * DifyWorkflowExecutor.initializeContextVariables → VariablePool.environmentVariables →
 * AbstractDifyNode.resolveVariables 的 ENVIRONMENT 分支。
 *
 * <p>之前 fix 前:env 变量被错误地写入 nodeOutputs["env"],getEnvironment 读不到,
 * {{#env.host#}} 永远字面回显。本次 fix 后:env 变量进入 environmentVariables 桶,
 * getEnvironment 能读到。</p>
 *
 * <p>因为 DifyWorkflowExecutor.execute() 不暴露 context 给测试,本测试通过反射
 * 调用 private initializeContextVariables(DifyWorkflowContext) 验证初始化阶段的写入路径。
 * 该方法是单测白盒合理介入点,不影响生产代码。</p>
 */
class EnvironmentVariableResolutionTest {

    /** 最小工作流:start → end,只配 environment_variables 触发 init 路径 */
    private static final String MINIMAL_YAML = ""
        + "app:\n"
        + "  name: env-resolution-test\n"
        + "  mode: workflow\n"
        + "kind: app\n"
        + "version: 0.5.0\n"
        + "workflow:\n"
        + "  conversation_variables: []\n"
        + "  environment_variables:\n"
        + "  - id: id-host\n"
        + "    name: host\n"
        + "    value: https://resolved.example.com\n"
        + "    value_type: string\n"
        + "  graph:\n"
        + "    edges: []\n"
        + "    nodes:\n"
        + "    - id: start\n"
        + "      data:\n"
        + "        type: start\n"
        + "        title: start\n"
        + "        variables: []\n"
        + "    - id: end\n"
        + "      data:\n"
        + "        type: end\n"
        + "        title: end\n"
        + "        variables: []\n"
        + "        outputs: []\n";

    @Test
    @DisplayName("execute() 路径下,env 变量被写入 environmentVariables 桶(非 nodeOutputs),可被 getEnvironment 读到")
    void envVarFromYaml_isWrittenToEnvironmentMap() throws Exception {
        DifyYamlParser parser = new DifyYamlParser();
        DifyDslModel model = parser.parse(MINIMAL_YAML);
        DifyWorkflowExecutor executor = DifyWorkflowExecutor.builder()
            .dslModel(model)
            .llmProviderFactory(LlmProviderFactory.builder().withBuiltinProviders().build())
            .build();

        // 构造一个真实的 DifyWorkflowContext(reflection 调到 ctor)
        DifyWorkflowContext context = newContext(model);

        // 反射调用 private initializeContextVariables
        Method init = DifyWorkflowExecutor.class.getDeclaredMethod(
            "initializeContextVariables", DifyWorkflowContext.class);
        init.setAccessible(true);
        init.invoke(executor, context);

        // 关键断言:env 变量应进入 environmentVariables 桶(可被 getEnvironment 读出)
        assertEquals("https://resolved.example.com",
            context.getVariablePool().getEnvironment("host"),
            "fix 前 env 变量被错误写入 nodeOutputs 桶,getEnvironment 读不到;"
                + "fix 后应进入 environmentVariables 专用通道");

        // 反向锁定:set(\"env\", ...) 路径不应回退到 env 桶
        assertNull(context.getVariablePool().get("env", "host"),
            "env 变量不应出现在 nodeOutputs[\"env\"] 桶");

        // 反向锁定:环境变量不应被错误地写到 systemVariables
        assertNull(context.getVariablePool().getSystem("host"),
            "env 变量不应出现在 systemVariables 桶");
    }

    /**
     * 反射构造 DifyWorkflowContext(其构造器可能是 package-private 或多参)。
     * 通过遍历构造器找一个能接受 DifyDslModel + Map + LlmProviderFactory 的。
     */
    private static DifyWorkflowContext newContext(DifyDslModel model) throws Exception {
        // 优先尝试常见签名
        for (java.lang.reflect.Constructor<?> ctor : DifyWorkflowContext.class.getDeclaredConstructors()) {
            ctor.setAccessible(true);
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 3
                && params[0].isAssignableFrom(DifyDslModel.class)
                && params[1].isAssignableFrom(java.util.Map.class)
                && params[2].isAssignableFrom(LlmProviderFactory.class)) {
                return (DifyWorkflowContext) ctor.newInstance(
                    model, Collections.emptyMap(),
                    LlmProviderFactory.builder().withBuiltinProviders().build());
            }
        }
        throw new IllegalStateException(
            "未找到 DifyWorkflowContext(DifyDslModel, Map, LlmProviderFactory) 构造器,"
                + "请更新测试以匹配实际构造器签名");
    }
}
