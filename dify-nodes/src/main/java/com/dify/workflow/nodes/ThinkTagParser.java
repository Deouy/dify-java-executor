package com.dify.workflow.nodes;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式增量中 <think>...</think> 标签的解析器。
 *
 * <p>把 LLM 返回的流式 delta 文本分成两段:
 * <ul>
 *   <li>{@link #REASON_CONTENT} (reason_content):位于 {@code <think>...</think>} 块内的内容</li>
 *   <li>{@link #NORMAL_TEXT} (text):位于 {@code <think>} 之前或 {@code </think>} 之后的内容</li>
 * </ul>
 * </p>
 *
 * <p>支持跨 SSE chunk 边界的标签:
 * 例如 chunk1="<think>" chunk2="分析...</think>结论" 也能正确解析为
 * reason_content="分析..." 后接 normal_text="结论"。</p>
 *
 * <p>设计参考 Dify Python 端 graphon/nodes/llm/node.py 对 reasoning_content 的分离处理。</p>
 *
 * <p>使用示例:
 * <pre>{@code
 * ThinkTagParser parser = new ThinkTagParser();
 * List<Segment> segs = parser.consume("<think>逐步分析</think>最终结论");
 * // segs = [{type=REASON_CONTENT, text="逐步分析"}, {type=NORMAL_TEXT, text="最终结论"}]
 * }</pre>
 */
public final class ThinkTagParser {

    /** 段类型:思维链内容(在 <think>...</think> 块内) */
    public static final int REASON_CONTENT = 1;
    /** 段类型:普通文本(在 <think>...</think> 块外) */
    public static final int NORMAL_TEXT = 2;

    /** 解析出的单个段 */
    public static final class Segment {
        public final int type;       // REASON_CONTENT 或 NORMAL_TEXT
        public final String text;

        public Segment(int type, String text) {
            this.type = type;
            this.text = text;
        }

        public boolean isReasonContent() { return type == REASON_CONTENT; }
        public boolean isNormalText() { return type == NORMAL_TEXT; }

        @Override
        public String toString() {
            return (isReasonContent() ? "REASON" : "TEXT") + "[\"" + text + "\"]";
        }
    }

    /** 标记 <think> 开始 */
    private static final String THINK_OPEN = "<think>";
    /** 标记 </think> 结束 */
    private static final String THINK_CLOSE = "</think>";

    /** 当前状态:false=在块外, true=在 <think> 块内 */
    private boolean insideThink = false;
    /** 跨 chunk 边界的未完整 buffer(可能包含 think_open/close 的前缀部分) */
    private final StringBuilder pending = new StringBuilder();

    /**
     * 输入一段增量文本,返回所有可以立刻 emit 的段。
     * 残余未确定字符(可能跨越 chunk 边界的部分 tag)会留在内部 buffer 中,
     * 等下一个 chunk 进来后再处理。
     */
    public List<Segment> consume(String delta) {
        List<Segment> emits = new ArrayList<>();
        if (delta == null || delta.isEmpty()) {
            return emits;
        }
        pending.append(delta);

        // 主循环:反复查找完整标签并 emit
        while (true) {
            if (insideThink) {
                int closeIdx = pending.indexOf(THINK_CLOSE);
                if (closeIdx >= 0) {
                    // emit close 之前的全部作为 reason_content
                    if (closeIdx > 0) {
                        emits.add(new Segment(REASON_CONTENT, pending.substring(0, closeIdx)));
                    }
                    pending.delete(0, closeIdx + THINK_CLOSE.length());
                    insideThink = false;
                } else {
                    // 没有完整 close 标签。安全 emit 范围 = 长度 - (THINK_CLOSE.length - 1)
                    // 因为最后 (THINK_CLOSE.length - 1) 个字符可能是 THINK_CLOSE 的前缀。
                    int safeLen = pending.length() - (THINK_CLOSE.length() - 1);
                    if (safeLen > 0) {
                        emits.add(new Segment(REASON_CONTENT, pending.substring(0, safeLen)));
                        pending.delete(0, safeLen);
                    }
                    break;
                }
            } else {
                int openIdx = pending.indexOf(THINK_OPEN);
                if (openIdx >= 0) {
                    // emit open 之前的全部作为 normal_text
                    if (openIdx > 0) {
                        emits.add(new Segment(NORMAL_TEXT, pending.substring(0, openIdx)));
                    }
                    pending.delete(0, openIdx + THINK_OPEN.length());
                    insideThink = true;
                } else {
                    // 没有完整 open 标签。安全 emit 范围 = 长度 - (THINK_OPEN.length - 1)
                    int safeLen = pending.length() - (THINK_OPEN.length() - 1);
                    if (safeLen > 0) {
                        emits.add(new Segment(NORMAL_TEXT, pending.substring(0, safeLen)));
                        pending.delete(0, safeLen);
                    }
                    break;
                }
            }
        }
        return emits;
    }

    /**
     * 流结束时调用,把残留 buffer 强制 flush 出去(避免最后一段丢失)。
     *
     * @param closeInProgress 当前是否在 <think> 块内(用于决定把残留 emit 为 reason 还是 text)
     */
    public List<Segment> flush(boolean closeInProgress) {
        List<Segment> emits = new ArrayList<>();
        if (pending.length() == 0) {
            return emits;
        }
        // 流结束时如果还在 <think> 内,残留部分作为 reason_content emit(虽然缺闭合标签)
        // 否则作为 normal_text emit(可能是 <think> 前缀或孤立 < 字符)
        emits.add(new Segment(closeInProgress ? REASON_CONTENT : NORMAL_TEXT, pending.toString()));
        pending.setLength(0);
        return emits;
    }

    /** 是否当前在 <think> 块内(用于流结束时的语义推断) */
    public boolean isInsideThink() {
        return insideThink;
    }

    /** 重置解析器(供同一节点复用 / 异常恢复) */
    public void reset() {
        insideThink = false;
        pending.setLength(0);
    }
}