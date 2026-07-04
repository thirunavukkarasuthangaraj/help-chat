package com.helpchat.store.dynamo;

import com.helpchat.model.Models.ChatMessage;
import com.helpchat.store.SessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Conversation history backed by the DynamoDB chat_sessions table
 * (see scripts/db/dynamodb-create-tables.sh).
 * PK session_id, sort key ts; the expires_at attribute drives DynamoDB's
 * native TTL, so 24h cleanup is automatic and free.
 */
@Component
@ConditionalOnProperty(name = "helpchat.storage", havingValue = "dynamodb")
public class DynamoSessionStore implements SessionStore {

    private static final String TABLE = "chat_sessions";
    private static final long TTL_SECONDS = 24 * 3600;

    private final DynamoDbClient dynamo;
    private final AtomicLong lastTs = new AtomicLong();

    public DynamoSessionStore(DynamoDbClient dynamo) {
        this.dynamo = dynamo;
    }

    @Override
    public List<ChatMessage> getHistory(String sessionId, int limit) {
        var items = dynamo.query(QueryRequest.builder()
                .tableName(TABLE)
                .keyConditionExpression("session_id = :sid")
                .expressionAttributeValues(Map.of(":sid", AttributeValue.fromS(sessionId)))
                .scanIndexForward(false)   // newest first
                .limit(limit)
                .build()).items();

        List<ChatMessage> newestFirst = new ArrayList<>();
        for (var item : items) {
            newestFirst.add(new ChatMessage(item.get("role").s(), item.get("content").s()));
        }
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    @Override
    public void append(String appKey, String sessionId, ChatMessage message) {
        long now = System.currentTimeMillis();
        // strictly increasing ts so two messages in the same millisecond don't collide
        long ts = lastTs.updateAndGet(prev -> Math.max(prev + 1, now));

        dynamo.putItem(PutItemRequest.builder()
                .tableName(TABLE)
                .item(Map.of(
                        "session_id", AttributeValue.fromS(sessionId),
                        "ts", AttributeValue.fromN(Long.toString(ts)),
                        "app_key", AttributeValue.fromS(appKey),
                        "role", AttributeValue.fromS(message.role()),
                        "content", AttributeValue.fromS(message.content()),
                        "expires_at", AttributeValue.fromN(
                                Long.toString(Instant.now().getEpochSecond() + TTL_SECONDS))))
                .build());
    }
}
