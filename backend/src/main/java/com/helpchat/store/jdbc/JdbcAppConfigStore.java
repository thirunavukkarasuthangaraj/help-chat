package com.helpchat.store.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpchat.model.Models.AppConfig;
import com.helpchat.store.AppConfigStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * App registry backed by the chat_apps table (see scripts/db/schema.sql).
 * Onboard a new application by inserting one row — no code change, no restart.
 */
@Component
@ConditionalOnProperty(name = "helpchat.storage", havingValue = "jdbc")
public class JdbcAppConfigStore implements AppConfigStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcAppConfigStore(JdbcTemplate helpchatJdbcTemplate) {
        this.jdbc = helpchatJdbcTemplate;
    }

    @Override
    public AppConfig get(String appKey) {
        List<AppConfig> rows = jdbc.query("""
                SELECT app_key, app_name, theme_color, welcome_message,
                       suggested_questions, system_prompt, docs_file
                  FROM chat_apps
                 WHERE app_key = ? AND is_active = 1
                """,
                (rs, i) -> new AppConfig(
                        rs.getString("app_key"),
                        rs.getString("app_name"),
                        rs.getString("theme_color"),
                        rs.getString("welcome_message"),
                        parseQuestions(rs.getString("suggested_questions")),
                        rs.getString("system_prompt"),
                        rs.getString("docs_file")),
                appKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<String> parseQuestions(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
