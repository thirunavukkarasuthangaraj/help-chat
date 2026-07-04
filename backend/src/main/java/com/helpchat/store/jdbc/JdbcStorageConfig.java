package com.helpchat.store.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * SQL connection for helpchat.storage=jdbc. Works with any JDBC database —
 * MySQL, PostgreSQL, MariaDB, H2 ... the driver is picked from the URL.
 *
 * Configure per deployment (each client can point at their own DB):
 *   HELPCHAT_STORAGE=jdbc
 *   HELPCHAT_DB_URL=jdbc:mysql://host:3306/helpchat        (or jdbc:postgresql://...)
 *   HELPCHAT_DB_USER=...
 *   HELPCHAT_DB_PASSWORD=...
 *
 * Tables: run scripts/db/schema.sql once (MySQL) or the equivalent DDL.
 */
@Configuration
@ConditionalOnProperty(name = "helpchat.storage", havingValue = "jdbc")
public class JdbcStorageConfig {

    @Value("${helpchat.db.url}")
    private String url;

    @Value("${helpchat.db.username:}")
    private String username;

    @Value("${helpchat.db.password:}")
    private String password;

    @Bean
    public DataSource helpchatDataSource() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        if (!username.isBlank()) cfg.setUsername(username);
        if (!password.isBlank()) cfg.setPassword(password);
        cfg.setMaximumPoolSize(10);
        cfg.setPoolName("helpchat");
        return new HikariDataSource(cfg);
    }

    @Bean
    public JdbcTemplate helpchatJdbcTemplate(DataSource helpchatDataSource) {
        return new JdbcTemplate(helpchatDataSource);
    }
}
