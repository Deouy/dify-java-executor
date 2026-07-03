package com.dify.workflow.model;

import java.util.List;
import java.util.Objects;

/**
 * 模板段(answer 节点 answer 模板解析后的最小单位)。
 *
 * <p>对齐 Dify Python 端 {@code graph_engine.template.Template.from_answer_template}。
 * 模板被解析成 TextSegment 和 VariableSegment 的有序列表,ResponseStreamCoordinator
 * 据此决定每个 chunk 应归属到 answer 节点还是某个上游 LLM 节点的 selector。</p>
 */
public interface TemplateSegment {

    /**
     * 字面文本段 — 模板里 {@code {{#...#}}} 区间外的字符。
     * 在 LLM 流式期间原样输出,等 LLM 跑完或路径清零时一次性 emit。
     */
    final class TextSegment implements TemplateSegment {
        private final String text;

        public TextSegment(String text) {
            this.text = text;
        }

        public String text() { return text; }
        public String getText() { return text; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TextSegment)) return false;
            TextSegment that = (TextSegment) o;
            return Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() { return Objects.hash(text); }

        @Override
        public String toString() { return "TextSegment[" + text + "]"; }
    }

    /**
     * 变量引用段 — 模板里 {@code {{#nodeId.field#}}} 区间。
     * selector 是 [nodeId, field] 形式,指明 chunk 来源。
     * 在 LLM 流式期间从对应 selector 的 chunk buffer 逐 token pop 后 emit。
     */
    final class VariableSegment implements TemplateSegment {
        private final List<String> selector;

        public VariableSegment(List<String> selector) {
            this.selector = selector;
        }

        public List<String> selector() { return selector; }
        public List<String> getSelector() { return selector; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VariableSegment)) return false;
            VariableSegment that = (VariableSegment) o;
            return Objects.equals(selector, that.selector);
        }

        @Override
        public int hashCode() { return Objects.hash(selector); }

        @Override
        public String toString() { return "VariableSegment[" + selector + "]"; }
    }
}
