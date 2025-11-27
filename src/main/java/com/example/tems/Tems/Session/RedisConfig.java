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
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 900) // 15 minutes
public class RedisConfig {
    
    @Value("${spring.data.redis.host}")
    private String redisHost;
    
    @Value("${spring.data.redis.port}")
    private int redisPort;
    
    @Value("${spring.data.redis.password}")
    private String redisPassword;
    
    @Value("${spring.data.redis.username}")
    private String redisUsername;
    
    @Bean
    public RedisConnectionFactory connectionFactory() {
        System.out.println("===============================================");
        System.out.println("🔌 REDIS CONNECTION CONFIGURATION");
        System.out.println("===============================================");
        System.out.println("Host: " + redisHost);
        System.out.println("Port: " + redisPort);
        System.out.println("Username: " + redisUsername);
        System.out.println("Has Password: " + (redisPassword != null && !redisPassword.isEmpty()));
        System.out.println("===============================================");
        
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        
        if (redisUsername != null && !redisUsername.isEmpty()) {
            config.setUsername(redisUsername);
        }
        
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        
        System.out.println("✅ Redis connection factory created successfully");
        return factory;
    }
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        
        // Use JSON serializer for values
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        
        System.out.println("✅ RedisTemplate configured successfully");
        return template;
    }
}

