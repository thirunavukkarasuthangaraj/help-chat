#!/usr/bin/env bash
# =====================================================================
# help-chat DynamoDB tables (AWS alternative to schema.sql)
#   chat_apps      one item per application (key: app_key)
#   chat_sessions  one item per message (key: session_id + sort ts);
#                  expires_at enables the 24h TTL automatically
#
# Usage:  ./scripts/db/dynamodb-create-tables.sh [region]
# Requires: AWS CLI configured (aws configure)
# =====================================================================
set -e
REGION="${1:-ap-south-1}"

echo "Creating table chat_apps in $REGION ..."
aws dynamodb create-table \
  --region "$REGION" \
  --table-name chat_apps \
  --attribute-definitions AttributeName=app_key,AttributeType=S \
  --key-schema AttributeName=app_key,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

echo "Creating table chat_sessions in $REGION ..."
aws dynamodb create-table \
  --region "$REGION" \
  --table-name chat_sessions \
  --attribute-definitions \
      AttributeName=session_id,AttributeType=S \
      AttributeName=ts,AttributeType=N \
  --key-schema \
      AttributeName=session_id,KeyType=HASH \
      AttributeName=ts,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST

echo "Waiting for tables to become ACTIVE ..."
aws dynamodb wait table-exists --region "$REGION" --table-name chat_apps
aws dynamodb wait table-exists --region "$REGION" --table-name chat_sessions

echo "Enabling 24h TTL on chat_sessions (attribute: expires_at) ..."
aws dynamodb update-time-to-live \
  --region "$REGION" \
  --table-name chat_sessions \
  --time-to-live-specification "Enabled=true, AttributeName=expires_at"

echo "Seeding the demo app into chat_apps ..."
aws dynamodb put-item \
  --region "$REGION" \
  --table-name chat_apps \
  --item '{
    "app_key":            {"S": "demo"},
    "app_name":           {"S": "Demo App"},
    "theme_color":        {"S": "#0d7377"},
    "welcome_message":    {"S": "Hi! I'\''m your help assistant. Ask me anything about this app."},
    "suggested_questions":{"L": [
        {"S": "How do I get started?"},
        {"S": "How do I reset my password?"},
        {"S": "What are the pricing plans?"}]},
    "system_prompt":      {"S": "You are a friendly, concise help assistant for Demo App. Answer ONLY using the provided help documentation."},
    "docs_file":          {"S": "docs/demo.md"}
  }'

echo "Done. Tables: chat_apps, chat_sessions (TTL on expires_at)."
