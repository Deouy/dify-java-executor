package com.dify.workflow.nodes;

import com.dify.workflow.model.DifyNodeData;
import com.dify.workflow.model.NodeExecutionContext;
import com.dify.workflow.model.node.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * List Filter node - 列表过滤器。
 * 对数组执行 Filter → Order By → Limit → Extract 四个操作。
 * 参考 Dify 的 ListOperatorNode (graphon.nodes.list_operator)。
 */
public class ListFilterNode extends AbstractDifyNode {

    private static final Logger log = LoggerFactory.getLogger(ListFilterNode.class);

    public ListFilterNode(String id, DifyNodeData data) {
        super(id, NodeType.LIST_FILTER, data);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doExecute(NodeExecutionContext context) throws Exception {
        log.debug("Executing list filter node: {}", id);

        // 1. 读取输入数组
        Object rawVar = data.additionalProperties() != null
                ? data.additionalProperties().get("variable") : null;
        List<?> items = resolveInputArray(rawVar, context);
        if (items == null || items.isEmpty()) {
            context.setVariable(id, "result", Collections.emptyList());
            context.setVariable(id, "first_record", null);
            context.setVariable(id, "last_record", null);
            return;
        }

        // 转换为可变列表
        List<Object> result = new ArrayList<>(items);

        // 2. Filter
        Map<String, Object> filterBy = data.additionalProperties() != null
                ? (Map<String, Object>) data.additionalProperties().get("filter_by") : null;
        if (filterBy != null && Boolean.TRUE.equals(filterBy.get("enabled"))) {
            List<Map<String, Object>> conditions = (List<Map<String, Object>>) filterBy.get("conditions");
            if (conditions != null && !conditions.isEmpty()) {
                result = applyFilter(result, conditions);
            }
        }

        // 3. Order By
        Map<String, Object> orderBy = data.additionalProperties() != null
                ? (Map<String, Object>) data.additionalProperties().get("order_by") : null;
        if (orderBy != null && Boolean.TRUE.equals(orderBy.get("enabled"))) {
            String key = (String) orderBy.get("key");
            String order = (String) orderBy.get("value");
            result = applyOrderBy(result, key, order);
        }

        // 4. Limit
        Map<String, Object> limitCfg = data.additionalProperties() != null
                ? (Map<String, Object>) data.additionalProperties().get("limit") : null;
        if (limitCfg != null && Boolean.TRUE.equals(limitCfg.get("enabled"))) {
            Object sizeObj = limitCfg.get("size");
            int size = sizeObj instanceof Number ? ((Number) sizeObj).intValue() : 10;
            result = applyLimit(result, size);
        }

        // 5. Extract
        Map<String, Object> extractBy = data.additionalProperties() != null
                ? (Map<String, Object>) data.additionalProperties().get("extract_by") : null;
        if (extractBy != null && Boolean.TRUE.equals(extractBy.get("enabled"))) {
            result = applyExtract(result, extractBy);
        }

        // 6. 输出
        context.setVariable(id, "result", result);
        if (!result.isEmpty()) {
            context.setVariable(id, "first_record", result.get(0));
            context.setVariable(id, "last_record", result.get(result.size() - 1));
        } else {
            context.setVariable(id, "first_record", null);
            context.setVariable(id, "last_record", null);
        }

        log.debug("List filter node result: {} items", result.size());
    }

    @SuppressWarnings("unchecked")
    private List<?> resolveInputArray(Object rawVar, NodeExecutionContext context) {
        if (rawVar instanceof List && ((List<?>) rawVar).size() >= 2) {
            List<String> selector = (List<String>) rawVar;
            Object val = context.getVariable(selector.get(0), selector.get(1));
            if (val instanceof List) {
                return (List<?>) val;
            }
        }
        // 兼容: variables 中的 value_selector
        if (data.variables() != null && !data.variables().isEmpty()) {
            com.dify.workflow.model.DifyVariable firstVar = data.variables().get(0);
            if (firstVar.valueSelector() != null && firstVar.valueSelector().size() >= 2) {
                Object val = context.getVariable(firstVar.valueSelector().get(0), firstVar.valueSelector().get(1));
                if (val instanceof List) return (List<?>) val;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> applyFilter(List<Object> items, List<Map<String, Object>> conditions) {
        return items.stream().filter(item -> matchesAll(item, conditions)).collect(Collectors.toList());
    }

    private boolean matchesAll(Object item, List<Map<String, Object>> conditions) {
        for (Map<String, Object> cond : conditions) {
            if (!matchesCondition(item, cond)) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean matchesCondition(Object item, Map<String, Object> condition) {
        String operator = String.valueOf(condition.getOrDefault("comparison_operator", "is"));
        String key = (String) condition.get("key");
        String expectedValue = String.valueOf(condition.getOrDefault("value", ""));

        Object actualValue = null;
        if (item instanceof Map) {
            actualValue = ((Map<String, Object>) item).get(key);
        } else {
            actualValue = item;
        }
        String actualStr = actualValue != null ? String.valueOf(actualValue) : "";

        if ("contains".equals(operator)) return actualStr.contains(expectedValue);
        if ("not contains".equals(operator)) return !actualStr.contains(expectedValue);
        if ("start with".equals(operator)) return actualStr.startsWith(expectedValue);
        if ("end with".equals(operator)) return actualStr.endsWith(expectedValue);
        if ("is".equals(operator) || "=".equals(operator)) return actualStr.equals(expectedValue);
        if ("is not".equals(operator) || "≠".equals(operator)) return !actualStr.equals(expectedValue);
        if ("empty".equals(operator)) return actualStr.isEmpty();
        if ("not empty".equals(operator)) return !actualStr.isEmpty();
        if (">".equals(operator)) return compareNumbers(actualStr, expectedValue) > 0;
        if ("<".equals(operator)) return compareNumbers(actualStr, expectedValue) < 0;
        if ("≥".equals(operator) || ">=".equals(operator)) return compareNumbers(actualStr, expectedValue) >= 0;
        if ("≤".equals(operator) || "<=".equals(operator)) return compareNumbers(actualStr, expectedValue) <= 0;
        return true;
    }

    private int compareNumbers(String a, String b) {
        try {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> applyOrderBy(List<Object> items, String key, String order) {
        if (key == null) return items;
        Comparator<Object> cmp = (a, b) -> {
            Object va = a instanceof Map ? ((Map<String, Object>) a).get(key) : a;
            Object vb = b instanceof Map ? ((Map<String, Object>) b).get(key) : b;
            String sa = va != null ? String.valueOf(va) : "";
            String sb = vb != null ? String.valueOf(vb) : "";
            int result = compareNumbers(sa, sb);
            return "desc".equals(order) ? -result : result;
        };
        return items.stream().sorted(cmp).collect(Collectors.toList());
    }

    private List<Object> applyLimit(List<Object> items, int size) {
        if (size <= 0 || size >= items.size()) return items;
        return new ArrayList<>(items.subList(0, size));
    }

    private List<Object> applyExtract(List<Object> items, Map<String, Object> extractBy) {
        String serialStr = (String) extractBy.get("serial");
        try {
            int index = Integer.parseInt(serialStr) - 1; // 1-based → 0-based
            if (index >= 0 && index < items.size()) {
                Object extracted = items.get(index);
                return extracted instanceof List ? (List<Object>) extracted
                        : new ArrayList<>(Collections.singletonList(extracted));
            }
        } catch (NumberFormatException e) {
            log.debug("Invalid extract serial: {}", serialStr);
        }
        return items;
    }
}
