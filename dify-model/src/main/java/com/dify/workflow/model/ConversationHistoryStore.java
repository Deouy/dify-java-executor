package com.dify.workflow.model;

import java.util.List;
import java.util.Objects;

/**
 * 聊天历史存储接口。
 * 用于 LLM 节点的 conversation memory 功能。
 */
public interface ConversationHistoryStore {

    /** 保存一对问答消息 */
    void saveMessage(String conversationId, String query, String answer);

    /** 获取最近的 N 条历史消息 */
    List<MessagePair> getHistory(String conversationId, int limit);

    /** 获取对话轮数 */
    long getDialogueCount(String conversationId);

    /** 一对问答消息 */
    final class MessagePair {
        private final String query;
        private final String answer;
        private final long timestamp;

        public MessagePair(String query, String answer, long timestamp) {
            this.query = query;
            this.answer = answer;
            this.timestamp = timestamp;
        }

        public String query() { return query; }
        public String answer() { return answer; }
        public long timestamp() { return timestamp; }

        public String getQuery() { return query; }
        public String getAnswer() { return answer; }
        public long getTimestamp() { return timestamp; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MessagePair)) return false;
            MessagePair that = (MessagePair) o;
            return timestamp == that.timestamp
                    && Objects.equals(query, that.query)
                    && Objects.equals(answer, that.answer);
        }

        @Override
        public int hashCode() { return Objects.hash(query, answer, timestamp); }

        @Override
        public String toString() {
            return String.format("MessagePair[query=%s, answer=%s, timestamp=%d]", query, answer, timestamp);
        }
    }
}
