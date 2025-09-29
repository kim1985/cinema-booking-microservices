package com.cinema.booking.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration per distributed locking mission-critical
 * Previene race conditions nelle prenotazioni simultanee
 * Gestisce fallimenti Redis gracefully
 */
@Configuration
@Slf4j
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // String serializer per performance ottimali sui lock
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        // Configurazione per gestire fallimenti
        template.setEnableTransactionSupport(false); // Disable per performance
        template.afterPropertiesSet();

        // Test connessione Redis
        try {
            template.opsForValue().set("test:connection", "ok");
            template.delete("test:connection");
            log.info("Redis connection established successfully");
        } catch (Exception e) {
            log.warn("Redis connection failed - distributed locking will use fallback: {}", e.getMessage());
        }

        return template;
    }

    /**
     * Health check Bean per monitorare Redis
     */
    @Bean
    public RedisHealthIndicator redisHealthIndicator(RedisTemplate<String, String> redisTemplate) {
        return new RedisHealthIndicator(redisTemplate);
    }

    /**
     * Custom health indicator per Redis
     */
    @Slf4j
    public static class RedisHealthIndicator {
        private final RedisTemplate<String, String> redisTemplate;
        private volatile boolean lastKnownState = true;

        public RedisHealthIndicator(RedisTemplate<String, String> redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        public boolean isRedisAvailable() {
            try {
                redisTemplate.opsForValue().set("health:check", "ping");
                String result = redisTemplate.opsForValue().get("health:check");
                redisTemplate.delete("health:check");

                boolean isAvailable = "ping".equals(result);
                if (isAvailable != lastKnownState) {
                    log.info("Redis state changed: {}", isAvailable ? "AVAILABLE" : "UNAVAILABLE");
                    lastKnownState = isAvailable;
                }
                return isAvailable;
            } catch (Exception e) {
                if (lastKnownState) {
                    log.warn("Redis health check failed: {}", e.getMessage());
                    lastKnownState = false;
                }
                return false;
            }
        }
    }
}
