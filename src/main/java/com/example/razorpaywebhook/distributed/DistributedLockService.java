package com.example.razorpaywebhook.distributed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                    "  return redis.call('DEL', KEYS[1]) " +
                    "else " +
                    "  return 0 " +
                    "end";

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT;

    static {
        RELEASE_SCRIPT = new DefaultRedisScript<>();
        RELEASE_SCRIPT.setScriptText(RELEASE_LOCK_SCRIPT);
        RELEASE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    public boolean tryLock(String key, String instanceId, long ttlMs) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, instanceId,
                            java.time.Duration.ofMillis(ttlMs));
            return Boolean.TRUE.equals(acquired);
        } catch (Exception ex) {
            log.warn("DistributedLockService: Redis error on tryLock key={}", key, ex);
            return false;
        }
    }

    public boolean releaseLock(String key, String instanceId) {
        try {
            Long result = redisTemplate.execute(
                    RELEASE_SCRIPT,
                    List.of(key),
                    instanceId);
            return Long.valueOf(1L).equals(result);
        } catch (Exception ex) {
            log.warn("DistributedLockService: Redis error on releaseLock key={}", key, ex);
            return false;
        }
    }
}