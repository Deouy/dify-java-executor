package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.DifyVariable;
import com.dify.workflow.model.NodeExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CodeNode 单元测试。
 * 测试代码执行节点 (仅支持 JavaScript)。
 */
@ExtendWith(MockitoExtension.class)
class CodeNodeTest {

    @Mock
    private NodeExecutionContext context;

    private DifyNodeData createCodeNodeData(String code, String language, List<DifyVariable> inputVars) {
        return DifyNodeData.builder()
            .type("code")
            .title("代码执行")
            .variables(inputVars) // used as fallback for input variables
            .code(code)
            .language(language)
            .build();
    }

    @Nested
    @DisplayName("JavaScript 代码执行测试")
    class JavaScriptExecutionTest {

        @Test
        @DisplayName("执行简单加法运算")
        void testSimpleAddition() throws Exception {
            String code = "function main({a, b}) { return a + b; }";
            List<DifyVariable> inputVars = new ArrayList<>();
            inputVars.add(new DifyVariable("a", null, "number", false, null, null, null, null, null));
            inputVars.add(new DifyVariable("b", null, "number", false, null, null, null, null, null));

            DifyNodeData data = createCodeNodeData(code, "javascript", inputVars);
            CodeNode node = new CodeNode("code_node", data);

            when(context.getVariable("code_node", "a")).thenReturn(5);
            when(context.getVariable("code_node", "b")).thenReturn(3);

            node.execute(context);

            verify(context).setVariable("code_node", "result", 8);
        }

        @Test
        @DisplayName("执行字符串拼接")
        void testStringConcat() throws Exception {
            String code = "function main({greeting, name}) { return greeting + ' ' + name; }";
            List<DifyVariable> inputVars = new ArrayList<>();
            inputVars.add(new DifyVariable("greeting", null, "string", false, null, null, null, null, null));
            inputVars.add(new DifyVariable("name", null, "string", false, null, null, null, null, null));

            DifyNodeData data = createCodeNodeData(code, "javascript", inputVars);
            CodeNode node = new CodeNode("code_node", data);

            when(context.getVariable("code_node", "greeting")).thenReturn("Hello");
            when(context.getVariable("code_node", "name")).thenReturn("World");

            node.execute(context);

            verify(context).setVariable("code_node", "result", "Hello World");
        }

        @Test
        @DisplayName("当代码为空时抛出异常")
        void testEmptyCode_throwsException() {
            DifyNodeData data = createCodeNodeData("", "javascript", new ArrayList<>());
            CodeNode node = new CodeNode("code_node", data);

            assertThrows(Exception.class, () -> node.execute(context));
        }
    }

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTest {

        @Test
        @DisplayName("当输入变量为 null 时")
        void testNullInputVariables() throws Exception {
            String code = "function main() { return 'no inputs'; }";

            DifyNodeData data = createCodeNodeData(code, "javascript", null);
            CodeNode node = new CodeNode("code_node", data);

            node.execute(context);

            verify(context).setVariable("code_node", "result", "no inputs");
        }
    }
}
