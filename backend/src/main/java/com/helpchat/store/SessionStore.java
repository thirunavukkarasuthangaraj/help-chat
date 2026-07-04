package com.helpchat.store;

import com.helpchat.model.Models.ChatMessage;

import java.util.List;

/**
 * Conversation history per widget session (24h retention).
 *
 * Pluggable — pick the backend with `helpchat.storage`
 * (env var HELPCHAT_STORAGE): memory (default) | jdbc | dynamodb.
 */
public interface SessionStore {

    /** @return the last {@code limit} messages of the session, oldest first. */
    List<ChatMessage> getHistory(String sessionId, int limit);

    /** Append one message to the session's history. */
    void append(String appKey, String sessionId, ChatMessage message);
}
