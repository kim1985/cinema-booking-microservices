package com.cinema.booking.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Distributed Locking per prevenire race conditions
 * Mission-critical: garantisce che solo una richiesta alla volta prenoti posti
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedLockManager {

    private final RedisTemplate<String, String> redisTemplate;
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);
    private static final String LOCK_PREFIX = "booking:lock:screening:";

    /**
     * Esegue operazione con distributed lock usando Java 21 features
     */
    public <T> T executeWithLock(Long screeningId, Supplier<T> operation) {
        String lockKey = LOCK_PREFIX + screeningId;
        String lockToken = UUID.randomUUID().toString();

        log.debug("Attempting to acquire lock for screening {}", screeningId);

        // Pattern Matching per result handling - Java 21
        var lockResult = acquireLock(lockKey, lockToken);
        return switch (lockResult) {
            case Boolean acquired when acquired -> {
                log.debug("Lock acquired for screening {}", screeningId);
                try {
                    yield operation.get();
                } finally {
                    releaseLock(lockKey, lockToken);
                    log.debug("Lock released for screening {}", screeningId);
                }
            }
            case Boolean ignored -> {
                log.warn("Failed to acquire lock for screening {}", screeningId);
                throw new RuntimeException("Sistema occupato, riprova tra poco");
            }
            case null -> throw new RuntimeException("Redis lock system unavailable");
        };
    }

    private Boolean acquireLock(String lockKey, String lockToken) {
        try {
            return redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockToken, LOCK_TIMEOUT);
        } catch (Exception e) {
            log.error("Redis lock error: {}", e.getMessage());
            return null;
        }
    }

    private void releaseLock(String lockKey, String lockToken) {
        try {
            // Lua script per rilascio atomico
            String luaScript = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
                """;

            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptText(luaScript);
            redisScript.setResultType(Long.class);

            redisTemplate.execute(redisScript, List.of(lockKey), lockToken);
        } catch (Exception e) {
            log.warn("Error releasing lock: {}", e.getMessage());
        }
    }
}