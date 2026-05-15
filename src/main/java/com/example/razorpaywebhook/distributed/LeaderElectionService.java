package com.example.razorpaywebhook.distributed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderElectionService {

    private static final long LEADER_TTL_MS   = 10_000L;
    private static final String LEADER_PREFIX = "leader:";

    private static final String EXTEND_TTL_SCRIPT =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                    "  return redis.call('PEXPIRE', KEYS[1], ARGV[2]) " +
                    "else " +
                    "  return 0 " +
                    "end";

    private static final DefaultRedisScript<Long> EXTEND_SCRIPT;

    static {
        EXTEND_SCRIPT = new DefaultRedisScript<>();
        EXTEND_SCRIPT.setScriptText(EXTEND_TTL_SCRIPT);
        EXTEND_SCRIPT.setResultType(Long.class);
    }

    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    private final StringRedisTemplate redisTemplate;
    private final DistributedLockService distributedLockService;

    // Track keys this instance currently holds for renewal
    private final Set<String> heldKeys = ConcurrentHashMap.newKeySet();

    public boolean isLeader(String leaderKey) {
        String redisKey = LEADER_PREFIX + leaderKey;
        boolean acquired = distributedLockService.tryLock(redisKey, INSTANCE_ID, LEADER_TTL_MS);
        if (acquired) {
            heldKeys.add(redisKey);
            log.debug("LeaderElectionService: acquired leadership for key={}", redisKey);
        } else {
            // Check if we already hold it
            try {
                String current = redisTemplate.opsForValue().get(redisKey);
                if (INSTANCE_ID.equals(current)) {
                    heldKeys.add(redisKey);
                    return true;
                }
            } catch (Exception ex) {
                log.warn("LeaderElectionService: Redis error checking leadership key={}", redisKey, ex);
                // Fail-open
                return true;
            }
        }
        return acquired;
    }

    public void resignLeadership(String leaderKey) {
        String redisKey = LEADER_PREFIX + leaderKey;
        boolean released = distributedLockService.releaseLock(redisKey, INSTANCE_ID);
        heldKeys.remove(redisKey);
        if (released) {
            log.info("LeaderElectionService: resigned leadership for key={}", redisKey);
        }
    }

    @Scheduled(fixedDelay = 4_000)
    public void renewLeadership() {
        if (heldKeys.isEmpty()) return;

        for (String key : heldKeys) {
            try {
                Long result = redisTemplate.execute(
                        EXTEND_SCRIPT,
                        List.of(key),
                        INSTANCE_ID,
                        String.valueOf(LEADER_TTL_MS));
                if (!Long.valueOf(1L).equals(result)) {
                    heldKeys.remove(key);
                    log.info("LeaderElectionService: lost leadership for key={} during renewal", key);
                } else {
                    log.debug("LeaderElectionService: renewed leadership for key={}", key);
                }
            } catch (Exception ex) {
                log.warn("LeaderElectionService: Redis error during renewal for key={}", key, ex);
            }
        }
    }
}