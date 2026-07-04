-- H2-compatible schema (used for local/JDBC testing without MySQL).
-- Same tables as schema.sql, portable SQL types.

CREATE TABLE IF NOT EXISTS chat_apps (
  app_key             VARCHAR(64)   NOT NULL PRIMARY KEY,
  app_name            VARCHAR(128)  NOT NULL,
  theme_color         VARCHAR(16)   NOT NULL DEFAULT '#0d7377',
  welcome_message     VARCHAR(512)  NOT NULL,
  suggested_questions TEXT          NOT NULL,
  system_prompt       TEXT          NOT NULL,
  docs_file           VARCHAR(256)  NOT NULL,
  is_active           TINYINT       NOT NULL DEFAULT 1,
  created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_messages (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_key     VARCHAR(64)  NOT NULL,
  session_id  VARCHAR(64)  NOT NULL,
  role        VARCHAR(16)  NOT NULL,
  content     TEXT         NOT NULL,
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_session ON chat_messages (session_id, id);

CREATE TABLE IF NOT EXISTS chat_feedback (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_key     VARCHAR(64)  NOT NULL,
  session_id  VARCHAR(64)  NOT NULL,
  rating      VARCHAR(8)   NOT NULL,
  comment     VARCHAR(512) NULL,
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

MERGE INTO chat_apps (app_key, app_name, theme_color, welcome_message,
                      suggested_questions, system_prompt, docs_file)
KEY (app_key)
VALUES (
  'demo',
  'Demo App (from SQL DB)',
  '#0d7377',
  'Hi! I''m your help assistant. Ask me anything about this app.',
  '["How do I get started?","How do I reset my password?","What are the pricing plans?"]',
  'You are a friendly, concise help assistant for Demo App. Answer ONLY using the provided help documentation.',
  'docs/demo.md'
);
