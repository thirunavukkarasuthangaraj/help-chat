package com.helpchat.store.dynamo;

import com.helpchat.model.Models.AppConfig;
import com.helpchat.store.AppConfigStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.util.List;
import java.util.Map;

/**
 * App registry backed by the DynamoDB chat_apps table
 * (see scripts/db/dynamodb-create-tables.sh). Onboard a new application
 * with one put-item — no code change, no restart.
 */
@Component
@ConditionalOnProperty(name = "helpchat.storage", havingValue = "dynamodb")
public class DynamoAppConfigStore implements AppConfigStore {

    private static final String TABLE = "chat_apps";
    private final DynamoDbClient dynamo;

    public DynamoAppConfigStore(DynamoDbClient dynamo) {
        this.dynamo = dynamo;
    }

    @Override
    public AppConfig get(String appKey) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("app_key", AttributeValue.fromS(appKey)))
                .build()).item();
        if (item == null || item.isEmpty()) return null;

        List<String> questions = item.containsKey("suggested_questions")
                ? item.get("suggested_questions").l().stream().map(AttributeValue::s).toList()
                : List.of();

        return new AppConfig(
                s(item, "app_key"),
                s(item, "app_name"),
                s(item, "theme_color"),
                s(item, "welcome_message"),
                questions,
                s(item, "system_prompt"),
                s(item, "docs_file"));
    }

    private String s(Map<String, AttributeValue> item, String name) {
        AttributeValue v = item.get(name);
        return v == null ? "" : v.s();
    }
}
