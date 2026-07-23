package com.roost.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1) // Run BEFORE DataSeeder
public class DatabaseSchemaMigrator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaMigrator.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("Checking & migrating PostgreSQL database schema...");
        try {
            // User table columns
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS public_key TEXT;");
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS last_active_at TIMESTAMP;");

            // Property table columns
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS landlord_phone VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS landlord_name VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS landlord_id VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS video_url VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS house_type VARCHAR(255) DEFAULT 'BEDSITTER';");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS bathrooms INT DEFAULT 1;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS furnished BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS parking BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS water BOOLEAN DEFAULT TRUE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS wifi BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS security BOOLEAN DEFAULT TRUE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS pet_friendly BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS balcony BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS deposit VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS move_in_date VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS listed_at TIMESTAMP;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS last_confirmed_at TIMESTAMP;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS view_count INT DEFAULT 0;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS save_count INT DEFAULT 0;");

            log.info("Database schema migration completed successfully!");
        } catch (Exception e) {
            log.warn("Database schema migration notice: " + e.getMessage());
        }
    }
}
