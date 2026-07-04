-- =====================================================================
-- help-chat database schema (MySQL 8+ / MariaDB 10.5+)
-- Use this when you outgrow the in-memory stores.
--   chat_apps      replaces AppConfigStore  (one row per application)
--   chat_messages  replaces SessionStore    (conversation history)
--   chat_feedback  optional 👍/👎 logging
--
-- Run:  mysql -u root -p < scripts/db/schema.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS helpchat
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE helpchat;

-- One row per application that embeds the widget
CREATE TABLE IF NOT EXISTS chat_apps (
  app_key             VARCHAR(64)   NOT NULL,
  app_name            VARCHAR(128)  NOT NULL,
  theme_color         VARCHAR(16)   NOT NULL DEFAULT '#0d7377',
  welcome_message     VARCHAR(512)  NOT NULL,
  suggested_questions JSON          NOT NULL,          -- e.g. ["How do I get started?", ...]
  system_prompt       TEXT          NOT NULL,          -- used only by the claude engine
  docs_file           VARCHAR(256)  NOT NULL,          -- e.g. "docs/myapp.md"
  is_active           TINYINT(1)    NOT NULL DEFAULT 1,
  created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (app_key)
) ENGINE=InnoDB;

-- Conversation history (one row per message; session = widget sessionId)
CREATE TABLE IF NOT EXISTS chat_messages (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  app_key     VARCHAR(64)     NOT NULL,
  session_id  VARCHAR(64)     NOT NULL,
  role        ENUM('user','assistant') NOT NULL,
  content     TEXT            NOT NULL,
  created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_session (session_id, id),
  KEY idx_app_created (app_key, created_at),
  CONSTRAINT fk_msg_app FOREIGN KEY (app_key) REFERENCES chat_apps (app_key)
) ENGINE=InnoDB;

-- Optional: thumbs up/down feedback + unanswered-question logging
CREATE TABLE IF NOT EXISTS chat_feedback (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  app_key     VARCHAR(64)     NOT NULL,
  session_id  VARCHAR(64)     NOT NULL,
  message_id  BIGINT UNSIGNED NULL,
  rating      ENUM('up','down') NOT NULL,
  comment     VARCHAR(512)    NULL,
  created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_app_rating (app_key, rating, created_at)
) ENGINE=InnoDB;

-- 24h history cleanup (equivalent of the in-memory TTL). Requires
-- SET GLOBAL event_scheduler = ON;  (or event_scheduler=ON in my.cnf)
CREATE EVENT IF NOT EXISTS purge_old_chat_messages
  ON SCHEDULE EVERY 1 HOUR
  DO DELETE FROM chat_messages WHERE created_at < NOW() - INTERVAL 24 HOUR;

-- Seed: the demo application
INSERT INTO chat_apps
  (app_key, app_name, theme_color, welcome_message, suggested_questions, system_prompt, docs_file)
VALUES (
  'demo',
  'Demo App',
  '#0d7377',
  'Hi! I''m your help assistant. Ask me anything about this app.',
  JSON_ARRAY('How do I get started?', 'How do I reset my password?', 'What are the pricing plans?'),
  'You are a friendly, concise help assistant for "Demo App". Answer ONLY using the provided help documentation. If the answer is not in the docs, say you don''t have that information and suggest contacting support.',
  'docs/demo.md'
)
ON DUPLICATE KEY UPDATE app_name = VALUES(app_name);
