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
        String databaseUrl = System.getenv("DATABASE_URL");
        
        // If DATABASE_URL is not set or is empty, fall back to individual env vars or local defaults
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            String host = System.getenv("PGHOST");
            if (host == null || host.trim().isEmpty()) {
                host = "localhost";
            }
            String port = System.getenv("PGPORT");
            if (port == null || port.trim().isEmpty()) {
                port = "5432";
            }
            String database = System.getenv("PGDATABASE");
            if (database == null || database.trim().isEmpty()) {
                database = "roost";
            }
            String username = System.getenv("PGUSER");
            if (username == null || username.trim().isEmpty()) {
                username = "postgres";
            }
            String password = System.getenv("PGPASSWORD");
            if (password == null || password.trim().isEmpty()) {
                password = "postgres";
            }
            
            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            System.out.println("No DATABASE_URL found. Configuring datasource dynamically using PG* variables/defaults: " + jdbcUrl);
            
            return DataSourceBuilder.create()
                    .url(jdbcUrl)
                    .username(username)
                    .password(password)
                    .driverClassName("org.postgresql.Driver")
                    .build();
        }

        try {
            System.out.println("DATABASE_URL found. Parsing URL: " + databaseUrl.replaceAll(":([^@]+)@", ":****@"));
            
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
            
            // Append SSL mode if query parameters exist and are relevant, otherwise keep it simple
            if (dbUri.getQuery() != null && !dbUri.getQuery().isEmpty()) {
                jdbcUrl += "?" + dbUri.getQuery();
            }

            System.out.println("Configured datasource successfully from DATABASE_URL: " + jdbcUrl);

            return DataSourceBuilder.create()
                    .url(jdbcUrl)
                    .username(username)
                    .password(password)
                    .driverClassName("org.postgresql.Driver")
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid DATABASE_URL syntax: " + databaseUrl, e);
        }
    }
}
