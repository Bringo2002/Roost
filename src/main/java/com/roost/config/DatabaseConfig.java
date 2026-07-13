package com.roost.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.jdbc.DataSourceBuilder;
import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        // 1. Try standard connection URLs (Railway public/private or general spring datasource overrides)
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            databaseUrl = System.getenv("DATABASE_PRIVATE_URL");
        }
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            databaseUrl = System.getenv("SPRING_DATASOURCE_URL");
        }
        
        // 2. If a connection URL is found, parse or use directly
        if (databaseUrl != null && !databaseUrl.trim().isEmpty()) {
            if (databaseUrl.startsWith("jdbc:")) {
                // If it's already a JDBC URL, use it directly with optionally provided username and password
                String username = System.getenv("SPRING_DATASOURCE_USERNAME");
                if (username == null || username.trim().isEmpty()) {
                    username = System.getenv("PGUSER");
                }
                String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
                if (password == null || password.trim().isEmpty()) {
                    password = System.getenv("PGPASSWORD");
                }

                System.out.println("JDBC URL detected. Configuring datasource directly: " + databaseUrl);
                return DataSourceBuilder.create()
                        .url(databaseUrl)
                        .username(username)
                        .password(password)
                        .driverClassName("org.postgresql.Driver")
                        .build();
            }

            try {
                System.out.println("Standard connection URL detected (postgresql:// format). Parsing: " + databaseUrl.replaceAll(":([^@]+)@", ":****@"));
                
                // Clean up the URL format if it contains "postgresql://" or "postgres://"
                URI dbUri = new URI(databaseUrl);
                
                String username = "";
                String password = "";
                if (dbUri.getUserInfo() != null && dbUri.getUserInfo().contains(":")) {
                    username = dbUri.getUserInfo().split(":")[0];
                    password = dbUri.getUserInfo().split(":")[1];
                } else if (dbUri.getUserInfo() != null) {
                    username = dbUri.getUserInfo();
                }

                // Construct JDBC URL: jdbc:postgresql://host:port/database
                String jdbcUrl = "jdbc:postgresql://" + dbUri.getHost() + ":" + dbUri.getPort() + dbUri.getPath();
                
                // Append query parameters (like sslmode) if they exist
                if (dbUri.getQuery() != null && !dbUri.getQuery().isEmpty()) {
                    jdbcUrl += "?" + dbUri.getQuery();
                }

                System.out.println("Programmatic datasource configured successfully from URL: " + jdbcUrl);

                return DataSourceBuilder.create()
                        .url(jdbcUrl)
                        .username(username)
                        .password(password)
                        .driverClassName("org.postgresql.Driver")
                        .build();
            } catch (URISyntaxException e) {
                throw new RuntimeException("Invalid database URL syntax: " + databaseUrl, e);
            }
        }

        // 3. Fall back to individual PG* environment variables or local defaults
        String host = System.getenv("PGHOST");
        String port = System.getenv("PGPORT");
        String database = System.getenv("PGDATABASE");
        String username = System.getenv("PGUSER");
        String password = System.getenv("PGPASSWORD");

        // Detect if we are in Railway/Cloud production but have no configuration
        boolean isProduction = System.getenv("PORT") != null || System.getenv("RAILWAY_ENVIRONMENT") != null;
        if (isProduction && (host == null || host.trim().isEmpty())) {
            System.err.println("=========================================================================");
            System.err.println("CRITICAL ERROR: Running in Production/Railway but database is not configured!");
            System.err.println("No DATABASE_URL, DATABASE_PRIVATE_URL, or PGHOST environment variables were found.");
            System.err.println("Please make sure to link your PostgreSQL database to this service in the Railway UI:");
            System.err.println("  1. Go to your Spring Boot service in Railway.");
            System.err.println("  2. Click on 'Variables'.");
            System.err.println("  3. Click 'New Variable', select 'DATABASE_URL' and reference the Postgres service.");
            System.err.println("=========================================================================");
        }

        if (host == null || host.trim().isEmpty()) {
            host = "localhost";
        }
        if (port == null || port.trim().isEmpty()) {
            port = "5432";
        }
        if (database == null || database.trim().isEmpty()) {
            database = "roost";
        }
        if (username == null || username.trim().isEmpty()) {
            username = "postgres";
        }
        if (password == null || password.trim().isEmpty()) {
            password = "postgres";
        }
        
        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        System.out.println("Configuring datasource dynamically using PG* variables or local defaults: " + jdbcUrl);
        
        return DataSourceBuilder.create()
                .url(jdbcUrl)
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}
