package com.helpchat.store.jdbc;

import com.helpchat.model.Models.ChatMessage;
import com.helpchat.store.SessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Conversation history backed by the chat_messages table
 * (see scripts/db/schema.sql). History survives restarts; the schema's
 * hourly purge event enforces the 24h retention — remove that event in
 * the database if you want to keep chats forever.
 */
@Component
@ConditionalOnProperty(name = "helpchat.storage", havingValue = "jdbc")
public class JdbcSessionStore implements SessionStore {

    private final JdbcTemplate jdbc;

    public JdbcSessionStore(JdbcTemplate helpchatJdbcTemplate) {
        this.jdbc = helpchatJdbcTemplate;
    }

    @Override
    public List<ChatMessage> getHistory(String sessionId, int limit) {
        List<ChatMessage> newestFirst = jdbc.query("""
                SELECT role, content FROM chat_messages
                 WHERE session_id = ?
                 ORDER BY id DESC
                 LIMIT ?
                """,
                (rs, i) -> new ChatMessage(rs.getString("role"), rs.getString("content")),
                sessionId, limit);
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    @Override
    public void append(String appKey, String sessionId, ChatMessage message) {
        jdbc.update("""
                INSERT INTO chat_messages (app_key, session_id, role, content)
                VALUES (?, ?, ?, ?)
                """, appKey, sessionId, message.role(), message.content());
    }
}
