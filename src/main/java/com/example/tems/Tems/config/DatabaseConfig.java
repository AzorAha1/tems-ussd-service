package com.example.tems.Tems.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class DatabaseConfig {
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSource dataSource() {
        String dbUrl = System.getenv("DATABASE_URL");
        
        if (dbUrl != null && !dbUrl.isEmpty()) {
            try {
                URI uri = new URI(dbUrl);
                String host = uri.getHost();
                int port = uri.getPort();
                String dbName = uri.getPath().substring(1);
                String[] userPass = uri.getUserInfo().split(":");
                String username = userPass[0];
                String password = userPass.length > 1 ? userPass[1] : "";
                
                com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
                
                // Build JDBC URL with SSL parameters that don't verify hostname
                String jdbcUrl = String.format(
                    "jdbc:postgresql://%s:%d/%s?sslmode=require&ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory",
                    host, port, dbName
                );
                
                ds.setJdbcUrl(jdbcUrl);
                ds.setUsername(username);
                ds.setPassword(password);
                ds.setDriverClassName("org.postgresql.Driver");
                
                return ds;
            } catch (URISyntaxException e) {
                throw new RuntimeException("Invalid DATABASE_URL: " + dbUrl, e);
            }
        }
        
        // Fallback for local development
        com.zaxxer.hikari.HikariDataSource fallback = new com.zaxxer.hikari.HikariDataSource();
        fallback.setJdbcUrl("jdbc:postgresql://localhost:5432/tems");
        fallback.setUsername("faisal");
        fallback.setPassword("");
        fallback.setDriverClassName("org.postgresql.Driver");
        return fallback;
    }
}