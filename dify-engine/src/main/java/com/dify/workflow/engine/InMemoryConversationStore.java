package com.dify.workflow.engine;

import com.dify.workflow.model.ConversationHistoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的聊天历史存储实现。
 * 使用 ConcurrentHashMap + ArrayList，线程安全。
 * 支持通过 maxConversations 限制会话数量（LRU 淘汰）。
 */
public class InMemoryConversationStore implements ConversationHistoryStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryConversationStore.class);

    private final int maxConversations;
    private final Map<String, List<MessagePair>> store = new ConcurrentHashMap<>();
    private final Deque<String> accessOrder = new ArrayDeque<>();

    public InMemoryConversationStore() {
        this(1000);
    }

    public InMemoryConversationStore(int maxConversations) {
        this.maxConversations = maxConversations;
    }

    @Override
    public void saveMessage(String conversationId, String query, String answer) {
        List<MessagePair> messages = store.computeIfAbsent(conversationId,
                k -> {
                    evictIfNeeded();
                    synchronized (accessOrder) {
                        accessOrder.addLast(k);
                    }
                    return Collections.synchronizedList(new ArrayList<>());
                });
        messages.add(new MessagePair(query, answer, System.currentTimeMillis()));
        log.debug("Saved message to conversation {}: {} messages total",
                conversationId, messages.size());
    }

    @Override
    public List<MessagePair> getHistory(String conversationId, int limit) {
        List<MessagePair> messages = store.get(conversationId);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        synchronized (messages) {
            int size = messages.size();
            int from = Math.max(0, size - limit);
            return new ArrayList<>(messages.subList(from, size));
        }
    }

    @Override
    public long getDialogueCount(String conversationId) {
        List<MessagePair> messages = store.get(conversationId);
        return messages != null ? messages.size() : 0;
    }

    private void evictIfNeeded() {
        while (store.size() >= maxConversations) {
            String oldest;
            synchronized (accessOrder) {
                oldest = accessOrder.pollFirst();
            }
            if (oldest != null) {
                store.remove(oldest);
                log.debug("Evicted conversation: {}", oldest);
            }
        }
    }
}
