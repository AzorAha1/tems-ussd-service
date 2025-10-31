package com.example.tems.Tems.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

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
                ds.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + dbName);
                ds.setUsername(username);
                ds.setPassword(password);
                ds.setDriverClassName("org.postgresql.Driver");

                // Required for Railway's self-signed SSL cert
                Properties props = new Properties();
                props.setProperty("ssl", "true");
                props.setProperty("sslfactory", "org.postgresql.ssl.NonValidatingFactory");
                ds.setDataSourceProperties(props);

                return ds;
            } catch (URISyntaxException e) {
                throw new RuntimeException("Invalid DATABASE_URL: " + dbUrl, e);
            }
        }

        // Fallback for local development
        return new com.zaxxer.hikari.HikariDataSource();
    }
}