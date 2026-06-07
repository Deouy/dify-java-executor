package com.dify.workflow.nodes.condition;

import com.dify.workflow.model.DifyCondition;
import com.dify.workflow.model.Java8Compat;
import com.dify.workflow.model.NodeExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * If-Else 条件求值器。
 *
 * <p>完整对齐 Dify 官方 {@code api/core/workflow/utils/condition/processor.py}
 * (commit {@code c917838f9c^}) 的 {@code ConditionProcessor} 语义,实现全部
 * 21 个 {@code SupportedComparisonOperator} 字符串字面量。</p>
 *
 * <p>支持运算符分类:</p>
 * <ul>
 *   <li>字符串/数组: contains, not contains, start with, end with, is, is not, empty, not empty, in, not in, all of</li>
 *   <li>数字: =, ≠, &gt;, &lt;, ≥, ≤ (注意官方是数学符号,Java 字符串需逐字匹配)</li>
 *   <li>空值: null, not null</li>
 *   <li>文件: exists, not exists</li>
 * </ul>
 *
 * <p>与官方 Python 实现的关键差异:</p>
 * <ul>
 *   <li>用 {@link NodeExecutionContext#getVariable(String, String)} 代替
 *       {@code VariablePool.get(selector)}(语义等价)</li>
 *   <li>异常用 {@link IllegalArgumentException} 代替 {@code ValueError}</li>
 *   <li>布尔转换用 {@link #convertToBool(Object)} 代替 {@code _convert_to_bool}</li>
 *   <li>expected 模板解析 ({@code {{xxx.yyy}}}) 暂不实现,留作后续 issue</li>
 * </ul>
 *
 * @see <a href="https://github.com/langgenius/dify">Dify 官方源码参考</a>
 */
public class ConditionProcessor {

    private static final Logger log = LoggerFactory.getLogger(ConditionProcessor.class);

    /** 入口:评估一组条件,按 logicalOperator 聚合,并在短路时提前返回。 */
    public ConditionCheckResult processConditions(NodeExecutionContext context,
                                                  List<DifyCondition> conditions,
                                                  String operator) {
        java.util.List<java.util.Map<String, Object>> inputConditions = new java.util.ArrayList<>();
        java.util.List<Boolean> groupResults = new java.util.ArrayList<>();

        if (conditions == null || conditions.isEmpty()) {
            // 无条件视为 true(对齐官方"空条件默认"行为)
            return new ConditionCheckResult(inputConditions, groupResults, true);
        }

        String op = operator == null ? "and" : operator.toLowerCase();
        boolean isAnd = "and".equals(op);
        boolean isOr = "or".equals(op);

        for (DifyCondition condition : conditions) {
            boolean result = evaluateSingleCondition(context, condition, inputConditions);

            groupResults.add(result);
            // 短路:AND 遇 false / OR 遇 true → 立即返回
            if (isAnd && !result) {
                return new ConditionCheckResult(inputConditions, groupResults, false);
            }
            if (isOr && result) {
                return new ConditionCheckResult(inputConditions, groupResults, true);
            }
        }

        boolean finalResult = isAnd
            ? groupResults.stream().allMatch(Boolean::booleanValue)
            : groupResults.stream().anyMatch(Boolean::booleanValue);
        return new ConditionCheckResult(inputConditions, groupResults, finalResult);
    }

    // ========== 单条件求值 ==========

    private boolean evaluateSingleCondition(NodeExecutionContext context,
                                            DifyCondition condition,
                                            List<Map<String, Object>> inputConditions) {
        List<String> selector = condition.variableSelector();
        if (selector == null || selector.size() < 2) {
            throw new IllegalArgumentException(
                "Variable selector must have at least 2 elements [nodeId, varName]: " + selector);
        }

        // exists/not exists 直接判定"变量池是否存在"
        String op = condition.comparisonOperator();
        if ("exists".equals(op) || "not exists".equals(op)) {
            Object value = context.getVariable(selector.get(0), selector.get(1));
            return evaluateCondition(value, op, null);
        }

        Object actualValue = context.getVariable(selector.get(0), selector.get(1));
        Object expectedValue = condition.value();

        // NEW: 仅对 String expected 做模板解析(对齐 Dify 官方 convert_template)
        //   - 无标记字面量("LLM")经 resolveVariables 后原样返回 → 向后兼容
        //   - 标记 {{#node1.x#}} 替换为变量池中的值
        //   - 非 String(Number/Boolean/Collection)直接透传
        if (expectedValue instanceof String) {
            String resolved = context.resolveVariables((String) expectedValue);
            if (resolved != null) {
                expectedValue = resolved;
            }
        }

        inputConditions.add(Java8Compat.mapOf(
            "actual_value", String.valueOf(actualValue),
            "expected_value", String.valueOf(expectedValue),
            "comparison_operator", op == null ? "" : op
        ));

        return evaluateCondition(actualValue, op, expectedValue);
    }

    // ========== 运算符分发(对齐官方 _evaluate_condition) ==========

    private boolean evaluateCondition(Object value, String operator, Object expected) {
        if (operator == null) {
            throw new IllegalArgumentException("comparison_operator is null");
        }
        switch (operator) {
            case "contains":         return _assertContains(value, expected);
            case "not contains":     return _assertNotContains(value, expected);
            case "start with":       return _assertStartWith(value, expected);
            case "end with":         return _assertEndWith(value, expected);
            case "is":               return _assertIs(value, expected);
            case "is not":           return _assertIsNot(value, expected);
            case "empty":            return _assertEmpty(value);
            case "not empty":        return _assertNotEmpty(value);
            case "=":                return _assertEqual(value, expected);
            case "≠":                return _assertNotEqual(value, expected);
            case ">":                return _assertGreaterThan(value, expected);
            case "<":                return _assertLessThan(value, expected);
            case "≥":                return _assertGreaterThanOrEqual(value, expected);
            case "≤":                return _assertLessThanOrEqual(value, expected);
            case "null":             return _assertNull(value);
            case "not null":         return _assertNotNull(value);
            case "in":               return _assertIn(value, expected);
            case "not in":           return _assertNotIn(value, expected);
            case "all of":           return _assertAllOf(value, expected);
            case "exists":           return _assertExists(value);
            case "not exists":       return _assertNotExists(value);
            default:
                log.warn("Unknown comparison operator: {}", operator);
                // 对齐官方"unsupported operator"语义:抛错而非静默返回 false
                throw new IllegalArgumentException("Unsupported operator: " + operator);
        }
    }

    // ========== 字符串/数组断言(6 个) ==========

    private boolean _assertContains(Object value, Object expected) {
        if (isFalsy(value)) {
            return false;
        }
        if (!(value instanceof String || value instanceof Collection)) {
            throw new IllegalArgumentException("Invalid actual value type: string or array");
        }
        String exp = expected == null ? "" : expected.toString();
        if (value instanceof String) {
            return ((String) value).contains(exp);
        }
        // Collection
        Collection<?> coll = (Collection<?>) value;
        for (Object item : coll) {
            if (item != null && exp.equals(item.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean _assertNotContains(Object value, Object expected) {
        if (isFalsy(value)) {
            return true;
        }
        if (!(value instanceof String || value instanceof Collection)) {
            throw new IllegalArgumentException("Invalid actual value type: string or array");
        }
        return !_assertContains(value, expected);
    }

    private boolean _assertStartWith(Object value, Object expected) {
        if (isFalsy(value)) {
            return false;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Invalid actual value type: string");
        }
        if (!(expected instanceof String)) {
            throw new IllegalArgumentException("Expected value must be a string for startswith");
        }
        return ((String) value).startsWith((String) expected);
    }

    private boolean _assertEndWith(Object value, Object expected) {
        if (isFalsy(value)) {
            return false;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Invalid actual value type: string");
        }
        if (!(expected instanceof String)) {
            throw new IllegalArgumentException("Expected value must be a string for endswith");
        }
        return ((String) value).endsWith((String) expected);
    }

    private boolean _assertIs(Object value, Object expected) {
        if (value == null) {
            return false;
        }
        if (!(value instanceof String || value instanceof Boolean)) {
            throw new IllegalArgumentException("Invalid actual value type: string or boolean");
        }
        return Objects.equals(value, expected);
    }

    private boolean _assertIsNot(Object value, Object expected) {
        if (value == null) {
            return false;
        }
        if (!(value instanceof String || value instanceof Boolean)) {
            throw new IllegalArgumentException("Invalid actual value type: string or boolean");
        }
        return !Objects.equals(value, expected);
    }

    // ========== empty 断言(2 个,基于 Python falsy 语义) ==========

    private boolean _assertEmpty(Object value) {
        return isFalsy(value);
    }

    private boolean _assertNotEmpty(Object value) {
        return !isFalsy(value);
    }

    // ========== 数字断言(6 个,带 _normalizeNumericValues) ==========

    private boolean _assertEqual(Object value, Object expected) {
        if (value == null) {
            return false;
        }
        if (!(value instanceof Number || value instanceof Boolean)) {
            throw new IllegalArgumentException("Invalid actual value type: number or boolean");
        }
        Object exp = coerceNumeric(value, expected);
        if (exp == null) {
            throw new IllegalArgumentException("Cannot convert expected to compatible numeric type");
        }
        return Objects.equals(value, exp);
    }

    private boolean _assertNotEqual(Object value, Object expected) {
        return !_assertEqual(value, expected);
    }

    private boolean _assertGreaterThan(Object value, Object expected) {
        if (value == null) {
            return false;
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Invalid actual value type: number");
        }
        Number[] pair = normalizeNumericValues((Number) value, expected);
        if (pair == null) {
            throw new IllegalArgumentException("Cannot convert expected to number: " + expected);
        }
        return compareNumbers(pair[0], pair[1]) > 0;
    }

    private boolean _assertLessThan(Object value, Object expected) {
        if (value == null) {
            return false;
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Invalid actual value type: number");
        }
        Number[] pair = normalizeNumericValues((Number) value, expected);
        if (pair == null) {
            throw new IllegalArgumentException("Cannot convert expected to number: " + expected);
        }
        return compareNumbers(pair[0], pair[1]) < 0;
    }

    private boolean _assertGreaterThanOrEqual(Object value, Object expected) {
        if (value == null) {
            return false;
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Invalid actual value type: number");
        }
        Number[] pair = normalizeNumericValues((Number) value, expected);
        if (pair == null) {
            throw new IllegalArgumentException("Cannot convert expected to number: " + expected);
        }
        return compareNumbers(pair[0], pair[1]) >= 0;
    }

    private boolean _assertLessThanOrEqual(Object value, Object expected) {
        if (value == null) {
            return false;
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Invalid actual value type: number");
        }
        Number[] pair = normalizeNumericValues((Number) value, expected);
        if (pair == null) {
            throw new IllegalArgumentException("Cannot convert expected to number: " + expected);
        }
        return compareNumbers(pair[0], pair[1]) <= 0;
    }

    // ========== null 断言(2 个) ==========

    private boolean _assertNull(Object value) {
        return value == null;
    }

    private boolean _assertNotNull(Object value) {
        return value != null;
    }

    // ========== 列表断言(4 个) ==========

    private boolean _assertIn(Object value, Object expected) {
        if (isFalsy(value)) {
            return false;
        }
        if (!(expected instanceof Collection)) {
            throw new IllegalArgumentException("Invalid expected value type: array");
        }
        Collection<?> expectedList = (Collection<?>) expected;
        for (Object item : expectedList) {
            if (Objects.equals(value, item)) {
                return true;
            }
        }
        return false;
    }

    private boolean _assertNotIn(Object value, Object expected) {
        if (isFalsy(value)) {
            return true;
        }
        if (!(expected instanceof Collection)) {
            throw new IllegalArgumentException("Invalid expected value type: array");
        }
        return !_assertIn(value, expected);
    }

    @SuppressWarnings("unchecked")
    private boolean _assertAllOf(Object value, Object expected) {
        if (isFalsy(value)) {
            return false;
        }
        if (!(expected instanceof Collection)) {
            throw new IllegalArgumentException("Invalid expected value type: array");
        }
        Collection<Object> expectedList = (Collection<Object>) expected;

        // value 必须是容器(string / list / set)
        if (!(value instanceof Collection) && !(value instanceof String)) {
            return false;
        }

        for (Object item : expectedList) {
            boolean found = false;
            if (value instanceof String) {
                String s = (String) value;
                if (item != null && s.contains(item.toString())) {
                    found = true;
                }
            } else {
                Collection<Object> coll = (Collection<Object>) value;
                for (Object v : coll) {
                    if (Objects.equals(v, item)) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    // ========== 文件存在性断言(2 个) ==========

    private boolean _assertExists(Object value) {
        return value != null;
    }

    private boolean _assertNotExists(Object value) {
        return value == null;
    }

    // ========== 辅助方法(对齐官方 _convert_to_bool / _normalize_numeric_values) ==========

    /**
     * Python 风格 falsy 判定:
     * <ul>
     *   <li>{@code null} → true</li>
     *   <li>{@code ""} → true</li>
     *   <li>空 {@link Collection} / 空 {@link Map} → true</li>
     *   <li>{@link Number} 0 → true</li>
     *   <li>{@link Boolean} false → true</li>
     *   <li>其他 → false</li>
     * </ul>
     */
    public static boolean isFalsy(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean) {
            return !(Boolean) value;
        }
        if (value instanceof Number) {
            // 注意:官方不把 0 单独处理(因为 Python 的 0 走 generic falsy)
            return ((Number) value).doubleValue() == 0.0;
        }
        if (value instanceof CharSequence) {
            return ((CharSequence) value).length() == 0;
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
        }
        if (value instanceof Object[]) {
            return ((Object[]) value).length == 0;
        }
        return false;
    }

    /**
     * 仿 Python {@code _convert_to_bool}:把 int / 字符串(JSON 数字) 转 bool。
     * 非 int/str 或解析失败 → 抛 {@link IllegalArgumentException}。
     */
    public static Boolean convertToBool(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value) != 0;
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            // 简化处理:支持 "true"/"false" 字面量
            if ("true".equalsIgnoreCase(s)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(s)) {
                return Boolean.FALSE;
            }
            // 数字字符串:非零即 true
            try {
                int n = Integer.parseInt(s);
                return n != 0;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Cannot convert string to bool: " + value);
            }
        }
        throw new IllegalArgumentException("unexpected value: type=" +
            (value == null ? "null" : value.getClass().getName()) + ", value=" + value);
    }

    /**
     * 把 expected 转成与 value 类型兼容的数字。
     * - Boolean value → bool(expected)
     * - Integer value → int(expected)(str 时截断)
     * - 其他 Number value → double(expected)
     */
    private Object coerceNumeric(Object value, Object expected) {
        try {
            if (value instanceof Boolean) {
                if (expected instanceof Boolean || expected instanceof Number) {
                    return ((Number) ((Number) expected).intValue()).intValue() != 0;
                }
                if (expected instanceof String) {
                    return convertToBool(expected);
                }
                throw new IllegalArgumentException("Cannot convert to bool: " + expected);
            }
            if (value instanceof Integer) {
                if (expected instanceof Integer) return expected;
                if (expected instanceof Number) return ((Number) expected).intValue();
                if (expected instanceof String) return Integer.parseInt((String) expected);
                throw new IllegalArgumentException("Cannot convert to int: " + expected);
            }
            if (value instanceof Number) {
                if (expected instanceof Number) return ((Number) expected).doubleValue();
                if (expected instanceof String) return Double.parseDouble((String) expected);
                throw new IllegalArgumentException("Cannot convert to double: " + expected);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot parse numeric expected: " + expected, e);
        }
        return null;
    }

    /**
     * 仿官方 {@code _normalize_numeric_values}:返回 (normalizedValue, normalizedExpected) 二元组。
     * - 若 expected 是整数字符串且 value 是 Integer → 保持 int 比较(避免双精度精度)
     * - 否则 → 双方转 double
     * - 无法转换 → 返回 null
     */
    private Number[] normalizeNumericValues(Number value, Object expected) {
        try {
            if (expected instanceof Number) {
                return new Number[] { value, ((Number) expected).doubleValue() };
            }
            if (expected instanceof String) {
                String s = (String) expected;
                double expectedDouble = Double.parseDouble(s);
                if (value instanceof Integer && s.matches("-?\\d+")) {
                    return new Number[] { value, Integer.parseInt(s) };
                }
                return new Number[] { value.doubleValue(), expectedDouble };
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return null;
    }

    /** 比较两个数字(可能类型不同)。 */
    private int compareNumbers(Number a, Number b) {
        if (a instanceof Integer && b instanceof Integer) {
            return Integer.compare(a.intValue(), b.intValue());
        }
        return Double.compare(a.doubleValue(), b.doubleValue());
    }
}
