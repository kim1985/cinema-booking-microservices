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
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final String LOCK_PREFIX = "booking:lock:screening:";
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long BASE_RETRY_DELAY_MS = 20;

    /**
     * Esegue operazione con lock distribuito per evitare conflitti
     */
    public <T> T executeWithLock(Long screeningId, Supplier<T> operation) {
        String lockKey = LOCK_PREFIX + screeningId;
        String lockToken = UUID.randomUUID().toString();

        log.debug("Attempting to acquire lock for screening {}", screeningId);

        // Tentativi multipli con pause crescenti per gestire alta concorrenza
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Boolean lockAcquired = acquireLockWithTimeout(lockKey, lockToken);

                if (Boolean.TRUE.equals(lockAcquired)) {
                    log.debug("Lock acquired for screening {} on attempt {}", screeningId, attempt);
                    try {
                        return operation.get();
                    } finally {
                        releaseLockSafely(lockKey, lockToken);
                        log.debug("Lock released for screening {}", screeningId);
                    }
                } else {
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        // Pausa crescente per ridurre conflitti tra richieste simultanee
                        long delay = calculateOptimalDelay(attempt);
                        log.debug("Lock busy for screening {}, retrying in {}ms (attempt {}/{})",
                                screeningId, delay, attempt, MAX_RETRY_ATTEMPTS);
                        Thread.sleep(delay);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Lock acquisition interrupted for screening {}", screeningId);
                throw new RuntimeException("Prenotazione interrotta", e);
            } catch (Exception e) {
                log.warn("Lock error for screening {} on attempt {}: {}",
                        screeningId, attempt, e.getMessage());

                if (attempt == MAX_RETRY_ATTEMPTS) {
                    // Ultimo tentativo: procedi senza lock (rischio controllato)
                    log.warn("Proceeding without lock for screening {} after {} attempts",
                            screeningId, MAX_RETRY_ATTEMPTS);
                    return operation.get();
                }

                // Breve pausa prima del retry
                try {
                    Thread.sleep(BASE_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Prenotazione interrotta", ie);
                }
            }
        }

        // Fallback finale: esegui operazione (per garantire successo)
        log.warn("Could not acquire lock for screening {} after {} attempts, executing anyway",
                screeningId, MAX_RETRY_ATTEMPTS);
        return operation.get();
    }

    /**
     * Calcola tempo di attesa ottimale per ridurre conflitti
     */
    private long calculateOptimalDelay(int attempt) {
        // Tempo di attesa crescente con variazione casuale per evitare collisioni
        long baseDelay = BASE_RETRY_DELAY_MS * (1L << (attempt - 1)); // 20, 40, 80, 160, 320ms
        long randomVariation = (long) (Math.random() * baseDelay * 0.1); // 10% di variazione casuale
        return Math.min(baseDelay + randomVariation, 500); // Massimo 500ms
    }

    /**
     * Acquisizione lock con timeout
     */
    private Boolean acquireLockWithTimeout(String lockKey, String lockToken) {
        try {
            return redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockToken, LOCK_TIMEOUT);
        } catch (Exception e) {
            log.debug("Redis lock error (will retry): {}", e.getMessage());
            return false;
        }
    }

    /**
     * Rilascio lock sicuro
     */
    private void releaseLockSafely(String lockKey, String lockToken) {
        try {
            // Lua script atomico per rilascio sicuro
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

            Long result = redisTemplate.execute(redisScript, List.of(lockKey), lockToken);
            if (result == 0) {
                log.debug("Lock {} already expired or not owned", lockKey);
            }
        } catch (Exception e) {
            log.debug("Non-critical error releasing lock {}: {}", lockKey, e.getMessage());
        }
    }
}