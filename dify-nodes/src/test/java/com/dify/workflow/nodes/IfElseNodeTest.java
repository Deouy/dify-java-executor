package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyCase;
import com.dify.workflow.model.DifyCondition;
import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.Java8Compat;
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
 * IfElseNode 单元测试。
 *
 * 测试 If-Else 节点的条件判断逻辑。
 * IfElseNode 根据 conditions 评估条件，选择对应的分支。
 */
@ExtendWith(MockitoExtension.class)
class IfElseNodeTest {

    @Mock
    private NodeExecutionContext context;

    private DifyNodeData createNodeData(List<DifyCase> cases) {
        return DifyNodeData.builder()
            .type("if-else")
            .title("条件分支")
            .cases(cases)
            .build();
    }

    @Nested
    @DisplayName("contains 操作符测试")
    class ContainsOperatorTest {

        @Test
        @DisplayName("当变量包含指定值时返回 true")
        void testContains_whenVariableContainsValue_returnsTrue() throws Exception {
            // 准备测试数据
            List<DifyCondition> conditions = new ArrayList<>();
            conditions.add(new DifyCondition(
                "cond1",
                "contains",
                "hello",
                "string",
                Java8Compat.listOf("node1", "text")
            ));

            List<DifyCase> cases = new ArrayList<>();
            cases.add(new DifyCase("true", conditions, "and", null));

            DifyNodeData data = createNodeData(cases);
            IfElseNode node = new IfElseNode("if_else_node", data);

            // 模拟上下文返回包含 "hello" 的字符串
            when(context.getVariable("node1", "text")).thenReturn("hello world");

            // 执行
            node.execute(context);

            // 验证
            verify(context).setVariable("if_else_node", "case_true_result", true);
            verify(context).setVariable("if_else_node", "_selected_case", "true");
        }

        @Test
        @DisplayName("当变量不包含指定值时返回 false")
        void testContains_whenVariableDoesNotContainValue_returnsFalse() throws Exception {
            // 准备测试数据
            List<DifyCondition> conditions = new ArrayList<>();
            conditions.add(new DifyCondition(
                "cond1",
                "contains",
                "hello",
                "string",
                Java8Compat.listOf("node1", "text")
            ));

            List<DifyCase> cases = new ArrayList<>();
            cases.add(new DifyCase("true", conditions, "and", null));

            DifyNodeData data = createNodeData(cases);
            IfElseNode node = new IfElseNode("if_else_node", data);

            // 模拟上下文返回不包含 "hello" 的字符串
            when(context.getVariable("node1", "text")).thenReturn("goodbye world");

            // 执行
            node.execute(context);

            // 验证 - 注意如果没有 case 返回 true，不会设置 _selected_case
            verify(context, never()).setVariable(eq("if_else_node"), eq("_selected_case"), any());
        }
    }

    @Nested
    @DisplayName("equals 操作符测试")
    class EqualsOperatorTest {

        @Test
        @DisplayName("当变量等于指定值时返回 true")
        void testEquals_whenVariableEqualsValue_returnsTrue() throws Exception {
            List<DifyCondition> conditions = new ArrayList<>();
            conditions.add(new DifyCondition(
                "cond1",
                "equals",
                "yes",
                "string",
                Java8Compat.listOf("node1", "answer")
            ));

            List<DifyCase> cases = new ArrayList<>();
            cases.add(new DifyCase("true", conditions, "and", null));

            DifyNodeData data = createNodeData(cases);
            IfElseNode node = new IfElseNode("if_else_node", data);

            when(context.getVariable("node1", "answer")).thenReturn("yes");

            node.execute(context);

            verify(context).setVariable("if_else_node", "case_true_result", true);
            verify(context).setVariable("if_else_node", "_selected_case", "true");
        }

        @Test
        @DisplayName("当变量不等于指定值时返回 false")
        void testEquals_whenVariableNotEqualsValue_returnsFalse() throws Exception {
            List<DifyCondition> conditions = new ArrayList<>();
            conditions.add(new DifyCondition(
                "cond1",
                "equals",
                "yes",
                "string",
                Java8Compat.listOf("node1", "answer")
            ));

            List<DifyCase> cases = new ArrayList<>();
            cases.add(new DifyCase("true", conditions, "and", null));

            DifyNodeData data = createNodeData(cases);
            IfElseNode node = new IfElseNode("if_else_node", data);

            when(context.getVariable("node1", "answer")).thenReturn("no");

            node.execute(context);

            verify(context, never()).setVariable(eq("if_else_node"), eq("_selected_case"), eq("true"));
        }
    }

    @Nested
    @DisplayName("is empty 操作符测试")
    class IsEmptyOperatorTest {

        @Test
        @DisplayName("当变量为空字符串时返回 true")
        void testIsEmpty_whenVariableIsEmptyString_returnsTrue() throws Exception {
            List<DifyCondition> conditions = new ArrayList<>();
            conditions.add(new DifyCondition(
                "cond1",
                "is empty",
                "",
                "string",
                Java8Compat.listOf("node1", "text")
            ));

            List<DifyCase> cases = new ArrayList<>();
            cases.add(new DifyCase("true", conditions, "and", null));

            DifyNodeData data = createNodeData(cases);
            IfElseNode node = new IfElseNode("if_else_node", data);

            when(context.getVariable("node1", "text")).thenReturn("");

            node.execute(context);

            verify(context).setVariable("if_else_node", "case_true_result", true);
            verify(context).setVariable("if_else_node", "_selected_case", "true");
        }
    }

    @Nested
    @DisplayName("greater than 操作符测试")
    class GreaterThanOperatorTest {

        @Test
        @DisplayName("当数值大于指定值时返回 true")
        void testGreaterThan_whenValueIsGreater_returnsTrue() throws Exception {
            List<DifyCondition> conditions = new ArrayList<>();
            conditions.add(new DifyCondition(
                "cond1",
                "greater than",
                10,
                "number",
                Java8Compat.listOf("node1", "count")
            ));

            List<DifyCase> cases = new ArrayList<>();
            cases.add(new DifyCase("true", conditions, "and", null));

            DifyNodeData data = createNodeData(cases);
            IfElseNode node = new IfElseNode("if_else_node", data);

            when(context.getVariable("node1", "count")).thenReturn(15);

            node.execute(context);

            verify(context).setVariable("if_else_node", "case_true_result", true);
            verify(context).setVariable("if_else_node", "_selected_case", "true");
        }

        @Test
        @DisplayName("当数值小于指定值时返回 false")
        void testGreaterThan_whenValueIsLess_returnsFalse() throws Exception {
            List<DifyCondition> conditions = new ArrayList<>();
            conditions.add(new DifyCondition(
                "cond1",
                "greater than",
                10,
                "number",
                Java8Compat.listOf("node1", "count")
            ));

            List<DifyCase> cases = new ArrayList<>();
            cases.add(new DifyCase("true", conditions, "and", null));

            DifyNodeData data = createNodeData(cases);
            IfElseNode node = new IfElseNode("if_else_node", data);

            when(context.getVariable("node1", "count")).thenReturn(5);

            node.execute(context);

            verify(context, never()).setVariable(eq("if_else_node"), eq("_selected_case"), eq("true"));
        }
    }

    @Nested
    @DisplayName("AND/OR 逻辑操作符测试")
    class LogicalOperatorTest {

        @Test
        @DisplayName("当 AND 条件都满足时返回 true")
        void testAnd_whenAllConditionsMet_returnsTrue() throws Exception {
            List<DifyCondition> conditions = new ArrayList<>();
            conditions.add(new DifyCondition(
                "cond1",
                "equals",
                "yes",
                "string",
                Java8Compat.listOf("node1", "a")
            ));
            conditions.add(new DifyCondition(
                "cond2",
                "greater than",
                10,
                "number",
                Java8Compat.listOf("node1", "b")
            ));

            List<DifyCase> cases = new ArrayList<>();
            cases.add(new DifyCase("true", conditions, "and", null));

            DifyNodeData data = createNodeData(cases);
            IfElseNode node = new IfElseNode("if_else_node", data);

            when(context.getVariable("node1", "a")).thenReturn("yes");
            when(context.getVariable("node1", "b")).thenReturn(15);

            node.execute(context);

            verify(context).setVariable("if_else_node", "case_true_result", true);
        }

        @Test
        @DisplayName("当 AND 条件有一个不满足时返回 false")
        void testAnd_whenOneConditionFails_returnsFalse() throws Exception {
            List<DifyCondition> conditions = new ArrayList<>();
            conditions.add(new DifyCondition(
                "cond1",
                "equals",
                "yes",
                "string",
                Java8Compat.listOf("node1", "a")
            ));
            conditions.add(new DifyCondition(
                "cond2",
                "greater than",
                10,
                "number",
                Java8Compat.listOf("node1", "b")
            ));

            List<DifyCase> cases = new ArrayList<>();
            cases.add(new DifyCase("true", conditions, "and", null));

            DifyNodeData data = createNodeData(cases);
            IfElseNode node = new IfElseNode("if_else_node", data);

            when(context.getVariable("node1", "a")).thenReturn("yes");
            when(context.getVariable("node1", "b")).thenReturn(5);  // 不满足 > 10

            node.execute(context);

            verify(context, never()).setVariable(eq("if_else_node"), eq("_selected_case"), eq("true"));
        }
    }

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTest {

        @Test
        @DisplayName("当 cases 为空时抛出异常")
        void testEmptyCases_throwsException() {
            DifyNodeData data = createNodeData(new ArrayList<>());
            IfElseNode node = new IfElseNode("if_else_node", data);

            assertThrows(Exception.class, () -> node.execute(context));
        }

        @Test
        @DisplayName("当变量为 null 时视为空字符串处理")
        void testNullVariable_handledAsEmpty() throws Exception {
            List<DifyCondition> conditions = new ArrayList<>();
            conditions.add(new DifyCondition(
                "cond1",
                "is empty",
                "",
                "string",
                Java8Compat.listOf("node1", "text")
            ));

            List<DifyCase> cases = new ArrayList<>();
            cases.add(new DifyCase("true", conditions, "and", null));

            DifyNodeData data = createNodeData(cases);
            IfElseNode node = new IfElseNode("if_else_node", data);

            when(context.getVariable("node1", "text")).thenReturn(null);

            node.execute(context);

            // null 会被转换为空字符串，所以 is empty 应该返回 true
            verify(context).setVariable("if_else_node", "case_true_result", true);
        }

        @Test
        @DisplayName("当 conditions 为空时默认为 true")
        void testEmptyConditions_defaultsToTrue() throws Exception {
            List<DifyCase> cases = new ArrayList<>();
            cases.add(new DifyCase("true", new ArrayList<>(), "and", null));

            DifyNodeData data = createNodeData(cases);
            IfElseNode node = new IfElseNode("if_else_node", data);

            node.execute(context);

            // 空 conditions 意味着默认分支
            verify(context).setVariable("if_else_node", "case_true_result", true);
            verify(context).setVariable("if_else_node", "_selected_case", "true");
        }
    }
}
