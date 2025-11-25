package com.example.tems.Tems.Session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
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
            // Parse redis://:password@host:port format
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
                
                if (authPart.contains(":")) {
                    password = authPart.split(":")[1];
                }
                
                if (hostPart.contains(":")) {
                    host = hostPart.split(":")[0];
                    port = Integer.parseInt(hostPart.split(":")[1]);
                } else {
                    host = hostPart;
                }
            } else if (url.contains(":")) {
                host = url.split(":")[0];
                port = Integer.parseInt(url.split(":")[1]);
            } else {
                host = url;
            }
            
            return new LettuceConnectionFactory(host, port);
        } catch (Exception e) {
            System.err.println("Error parsing REDIS_URL: " + e.getMessage());
            System.err.println("Using fallback localhost connection");
            return new LettuceConnectionFactory("localhost", 6379);
        }
    }
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        if (redisUrl.contains("@")) {
            String password = redisUrl.split("@")[0].split(":")[2];
            // Set password on connection factory if available
        }
        
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