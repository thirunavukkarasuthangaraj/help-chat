package com.helpchat.store.memory;

import com.helpchat.model.Models.ChatMessage;
import com.helpchat.store.SessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default history store — in this JVM's memory, 24h TTL, gone on restart.
 * Active when helpchat.storage=memory (the default).
 */
@Component
@ConditionalOnProperty(name = "helpchat.storage", havingValue = "memory", matchIfMissing = true)
public class InMemorySessionStore implements SessionStore {

    private record Session(List<ChatMessage> messages, Instant lastActive) {}

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final long TTL_SECONDS = 24 * 3600;

    @Override
    public List<ChatMessage> getHistory(String sessionId, int limit) {
        Session s = sessions.get(sessionId);
        if (s == null) return List.of();
        List<ChatMessage> msgs = s.messages();
        int from = Math.max(0, msgs.size() - limit);
        return new ArrayList<>(msgs.subList(from, msgs.size()));
    }

    @Override
    public void append(String appKey, String sessionId, ChatMessage message) {
        sessions.compute(sessionId, (k, s) -> {
            List<ChatMessage> msgs = (s == null) ? new ArrayList<>() : s.messages();
            msgs.add(message);
            return new Session(msgs, Instant.now());
        });
    }

    /** Evict idle sessions every 10 minutes. */
    @Scheduled(fixedDelay = 600_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds(TTL_SECONDS);
        sessions.entrySet().removeIf(e -> e.getValue().lastActive().isBefore(cutoff));
    }
}
