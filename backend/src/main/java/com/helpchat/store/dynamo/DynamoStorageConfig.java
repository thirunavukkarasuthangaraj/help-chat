package com.helpchat.store.dynamo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * DynamoDB client for helpchat.storage=dynamodb.
 *
 * Configure per deployment:
 *   HELPCHAT_STORAGE=dynamodb
 *   AWS_REGION=ap-south-1                (plus standard AWS credentials —
 *                                         env vars, profile, or IAM role)
 *
 * Tables: run scripts/db/dynamodb-create-tables.sh once.
 */
@Configuration
@ConditionalOnProperty(name = "helpchat.storage", havingValue = "dynamodb")
public class DynamoStorageConfig {

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.create(); // region + credentials from the AWS default chain
    }
}
