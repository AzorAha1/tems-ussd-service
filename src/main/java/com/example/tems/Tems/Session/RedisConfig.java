package com.example.tems.Tems.Session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@Configuration
@EnableRedisHttpSession
public class RedisConfig {
    
    @Value("${REDIS_URL}")
    private String redisUrl;
    
    @Bean
    public RedisConnectionFactory connectionFactory() {
        try {
            String url = redisUrl.trim();
            if (url.startsWith("redis://")) {
                url = url.substring(8);
            }
            
            String password = "";
            String host = "localhost";
            int port = 6379;
            
            if (url.contains("@")) {
                String[] parts = url.split("@");
                String authPart = parts[0];
                String hostPart = parts[1];
                
                // Extract password (format is :password)
                if (authPart.startsWith(":")) {
                    password = authPart.substring(1);
                } else if (authPart.contains(":")) {
                    password = authPart.split(":", 2)[1];
                }
                
                // Extract host and port
                if (hostPart.contains(":")) {
                    String[] hostPortParts = hostPart.split(":");
                    host = hostPortParts[0];
                    port = Integer.parseInt(hostPortParts[1]);
                } else {
                    host = hostPart;
                }
            } else if (url.contains(":")) {
                host = url.split(":")[0];
                port = Integer.parseInt(url.split(":")[1]);
            } else {
                host = url;
            }
            
            // ✅ THIS IS THE KEY FIX - Use RedisStandaloneConfiguration to set password
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
            if (!password.isEmpty()) {
                config.setPassword(password);
                System.out.println("✅ Redis password configured");
            }
            
            System.out.println("✅ Connecting to Redis at " + host + ":" + port);
            return new LettuceConnectionFactory(config);
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing REDIS_URL: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to configure Redis", e);
        }
    }
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}