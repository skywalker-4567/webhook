package com.example.razorpaywebhook.scheduler;

import com.example.razorpaywebhook.distributed.LeaderElectionService;
import com.example.razorpaywebhook.service.LedgerRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerRetryScheduler {

    private static final String  LOCK_KEY = "retry:batch";
    private static final Duration LOCK_TTL = Duration.ofSeconds(25);

    private final LedgerRetryService ledgerRetryService;
    private final LeaderElectionService leaderElectionService;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 30_000)
    public void run() {
        if (!leaderElectionService.isLeader("ledger-retry")) {
            return;
        }

        boolean lockAcquired = acquireLock();
        if (!lockAcquired) {
            log.debug("LedgerRetryScheduler: could not acquire lock, skipping");
            return;
        }

        try {
            log.debug("LedgerRetryScheduler: running ledger retry batch");
            ledgerRetryService.retryPending();
        } catch (Exception ex) {
            log.error("LedgerRetryScheduler: error during retry batch", ex);
        } finally {
            releaseLock();
        }
    }

    private boolean acquireLock() {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception ex) {
            log.warn("LedgerRetryScheduler: Redis unavailable for lock — skipping run", ex);
            return false;
        }
    }

    private void releaseLock() {
        try {
            redisTemplate.delete(LOCK_KEY);
        } catch (Exception ex) {
            log.warn("LedgerRetryScheduler: failed to release Redis lock", ex);
        }
    }
}