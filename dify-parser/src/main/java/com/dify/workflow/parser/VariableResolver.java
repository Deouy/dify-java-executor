package com.dify.workflow.parser;

import com.dify.workflow.model.TemplateSegment;
import com.dify.workflow.model.node.SystemVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolver for Dify variable references.
 *
 * Variable format: {{#node_id.variable#}}
 *
 * Examples:
 * - {{#start_node.query#}} - Reference query variable from start_node
 * - {{#llm_node.text#}} - Reference text output from llm_node
 * - {{#sys.query#}} - Reference system query variable
 * - {{#env.API_KEY#}} - Reference environment variable
 */
public class VariableResolver {

    private static final Logger log = LoggerFactory.getLogger(VariableResolver.class);

    /**
     * Pattern to match variable references: {{#...#}}
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "\\{\\{#([^#}]+)#\\}\\}"
    );

    /**
     * System variable pattern: {{#sys.var#}}
     */
    private static final Pattern SYS_VAR_PATTERN = Pattern.compile(
            "\\{\\{#sys\\.([^}]+)#\\}\\}"
    );

    /**
     * Environment variable pattern: {{#env.VAR_NAME#}}
     */
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile(
            "\\{\\{#env\\.([^}]+)#\\}\\}"
    );

    /**
     * Conversation variable pattern: {{#conversation.var_name#}}
     */
    private static final Pattern CONV_VAR_PATTERN = Pattern.compile(
            "\\{\\{#conversation\\.([^}]+)#\\}\\}"
    );

    /**
     * RAG variable pattern: {{#rag.node_id.variable#}}
     */
    private static final Pattern RAG_VAR_PATTERN = Pattern.compile(
            "\\{\\{#rag\\.([^#}]+)#\\}\\}"
    );

    /**
     * Check if a string contains any variable references.
     *
     * @param value The string to check
     * @return true if the string contains variable references
     */
    public boolean containsVariable(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return VARIABLE_PATTERN.matcher(value).find();
    }

    /**
     * Extract all variable references from a string.
     *
     * @param value The string to extract from
     * @return List of variable references
     */
    public List<VariableRef> extractReferences(String value) {
        List<VariableRef> refs = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return refs;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(value);
        while (matcher.find()) {
            String fullMatch = matcher.group(1);
            refs.add(parseVariableRef(fullMatch));
        }
        return refs;
    }

    /**
     * 把模板字符串拆成 [TextSegment, VariableSegment, TextSegment, ...] 的有序列表。
     *
     * <p>对齐 Dify Python 端 {@code graph_engine.template.Template.from_answer_template}。
     * 用于 ResponseStreamCoordinator 把 answer 节点的 answer 字段解析成可逐 chunk 重写的段序列。</p>
     *
     * <p>解析规则:
     * <ul>
     *   <li>{{#nodeId.field#}} → VariableSegment(selector=[nodeId, field])</li>
     *   <li>其他字面字符 → TextSegment(原样)</li>
     *   <li>空字符串或 null → 返回空 list</li>
     *   <li>无 {{#...#}} 的纯文本 → 返回单 TextSegment</li>
     * </ul>
     *
     * @param template 模板字符串(如 "{{#llm.text#}}\n\n{{#llm.sources#}}")
     * @return 按出现顺序的段列表
     */
    public List<TemplateSegment> parseTemplate(String template) {
        List<TemplateSegment> segments = new ArrayList<>();
        if (template == null || template.isEmpty()) {
            return segments;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        int cursor = 0;
        while (matcher.find()) {
            int refStart = matcher.start();
            int refEnd = matcher.end();

            // refStart 之前是字面文本
            if (refStart > cursor) {
                segments.add(new TemplateSegment.TextSegment(template.substring(cursor, refStart)));
            }

            // ref 区间是变量段,selector = [nodeId, field]
            String fullMatch = matcher.group(1);
            String[] parts = fullMatch.split("\\.", 2);
            if (parts.length == 2) {
                segments.add(new TemplateSegment.VariableSegment(
                        Arrays.asList(parts[0], parts[1])));
            } else {
                // 退化处理:不是 nodeId.field 形式,按字面文本处理
                segments.add(new TemplateSegment.TextSegment(matcher.group()));
            }

            cursor = refEnd;
        }

        // 尾部剩余字面文本
        if (cursor < template.length()) {
            segments.add(new TemplateSegment.TextSegment(template.substring(cursor)));
        }

        return segments;
    }

    /**
     * Parse a variable reference string into a VariableRef object.
     *
     * @param ref String like "node_id.variable" or "sys.query"
     * @return Parsed VariableRef
     */
    private VariableRef parseVariableRef(String ref) {
        if (ref.startsWith(SystemVariable.SYS + ".")) {
            return new VariableRef(VariableRef.Type.SYSTEM, SystemVariable.SYS,
                    ref.substring(SystemVariable.SYS.length() + 1), null);
        } else if (ref.startsWith(SystemVariable.ENV + ".")) {
            return new VariableRef(VariableRef.Type.ENVIRONMENT, SystemVariable.ENV,
                    ref.substring(SystemVariable.ENV.length() + 1), null);
        } else if (ref.startsWith(SystemVariable.CONVERSATION + ".")) {
            String varName = ref.substring(SystemVariable.CONVERSATION.length() + 1);
            return new VariableRef(VariableRef.Type.CONVERSATION, SystemVariable.CONVERSATION, varName, null);
        } else if (ref.startsWith(SystemVariable.RAG + ".")) {
            String remaining = ref.substring(SystemVariable.RAG.length() + 1);
            int dotIndex = remaining.indexOf('.');
            if (dotIndex > 0) {
                String nodeId = remaining.substring(0, dotIndex);
                String field = remaining.substring(dotIndex + 1);
                return new VariableRef(VariableRef.Type.RAG, nodeId, field, null);
            }
            return new VariableRef(VariableRef.Type.RAG, null, remaining, null);
        } else {
            // node_id.variable format
            int dotIndex = ref.indexOf('.');
            if (dotIndex > 0) {
                String nodeId = ref.substring(0, dotIndex);
                String field = ref.substring(dotIndex + 1);
                return new VariableRef(VariableRef.Type.NODE, nodeId, field, null);
            }
            // Just node_id without field
            return new VariableRef(VariableRef.Type.NODE, ref, null, null);
        }
    }

    /**
     * Resolve all variable references in a string using the provided resolver function.
     *
     * @param template The string containing variable references
     * @param resolver Function to resolve a VariableRef to its value
     * @return The resolved string
     */
    public String resolve(String template, java.util.function.Function<VariableRef, String> resolver) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        String result = template;
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String fullMatch = matcher.group(0);
            String refStr = matcher.group(1);
            VariableRef ref = parseVariableRef(refStr);
            String resolved = resolver.apply(ref);
            if (resolved != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
            } else {
                // Keep original if not resolved
                matcher.appendReplacement(sb, fullMatch);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Variable reference data class.
     */
    public static final class VariableRef {
        public enum Type {
            SYSTEM,
            ENVIRONMENT,
            CONVERSATION,
            RAG,
            NODE
        }

        private final Type type;
        private final String nodeId;
        private final String field;
        private final String original;

        public VariableRef(Type type, String nodeId, String field, String original) {
            this.type = type;
            this.nodeId = nodeId;
            this.field = field;
            this.original = original;
        }

        // Record 风格访问器
        public Type type() { return type; }
        public String nodeId() { return nodeId; }
        public String field() { return field; }
        public String original() { return original; }

        // Getter 风格
        public Type getType() { return type; }
        public String getNodeId() { return nodeId; }
        public String getField() { return field; }
        public String getOriginal() { return original; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VariableRef)) return false;
            VariableRef that = (VariableRef) o;
            return type == that.type
                    && Objects.equals(nodeId, that.nodeId)
                    && Objects.equals(field, that.field)
                    && Objects.equals(original, that.original);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, nodeId, field, original);
        }

        @Override
        public String toString() {
            return String.format("VariableRef[type=%s, nodeId=%s, field=%s, original=%s]",
                    type, nodeId, field, original);
        }
    }
}
