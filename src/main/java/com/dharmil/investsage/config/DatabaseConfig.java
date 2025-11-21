package com.dharmil.investsage.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class DatabaseConfig {

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Bean
    @Primary
    public DataSource dataSource() {
        // If DATABASE_URL is provided in postgresql:// format (Render, Railway, etc.), parse it
        if (databaseUrl != null && !databaseUrl.isEmpty() && databaseUrl.startsWith("postgresql://")) {
            try {
                String decodedUrl = URLDecoder.decode(databaseUrl, StandardCharsets.UTF_8.name());
                URI dbUri = new URI(decodedUrl);
                
                String username = dbUri.getUserInfo().split(":")[0];
                String password = dbUri.getUserInfo().split(":")[1];
                String host = dbUri.getHost();
                int port = dbUri.getPort() == -1 ? 5432 : dbUri.getPort();
                String database = dbUri.getPath().replaceFirst("/", "");
                
                // Build JDBC URL
                String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
                
                // Add SSL parameter (Render and most cloud databases require SSL)
                if (dbUri.getQuery() != null && dbUri.getQuery().contains("sslmode")) {
                    jdbcUrl += "?" + dbUri.getQuery();
                } else {
                    jdbcUrl += "?sslmode=require";
                }
                
                return DataSourceBuilder.create()
                        .url(jdbcUrl)
                        .username(username)
                        .password(password)
                        .driverClassName("org.postgresql.Driver")
                        .build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse DATABASE_URL: " + databaseUrl, e);
            }
        }
        
        // If DATABASE_URL is already in JDBC format, or if using individual properties
        // Fall back to Spring Boot's default DataSource configuration
        // This will use spring.datasource.* properties from application.properties
        return DataSourceBuilder.create().build();
    }
}