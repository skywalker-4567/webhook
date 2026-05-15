package com.example.razorpaywebhook.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    public static final int  WEBHOOK_MAX_REQUESTS = 100;
    public static final long WEBHOOK_WINDOW_MS    = 60_000L;

    public static final int  ORDER_MAX_REQUESTS   = 20;
    public static final long ORDER_WINDOW_MS      = 60_000L;

    private static final String SLIDING_WINDOW_SCRIPT =
            "local key = KEYS[1] " +
                    "local now = tonumber(ARGV[1]) " +
                    "local window = tonumber(ARGV[2]) " +
                    "local max = tonumber(ARGV[3]) " +
                    "local cutoff = now - window " +
                    "redis.call('ZREMRANGEBYSCORE', key, 0, cutoff) " +
                    "local count = redis.call('ZCARD', key) " +
                    "if count >= max then " +
                    "  return 0 " +
                    "end " +
                    "redis.call('ZADD', key, now, now) " +
                    "redis.call('PEXPIRE', key, window) " +
                    "return 1";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(SLIDING_WINDOW_SCRIPT);
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    public boolean isAllowed(String key, int maxRequests, long windowMs) {
        try {
            long now = System.currentTimeMillis();
            Long result = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(key),
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    String.valueOf(maxRequests));
            return Long.valueOf(1L).equals(result);
        } catch (Exception ex) {
            log.warn("RateLimiterService: Redis error for key={} — allowing request (fail-open)", key, ex);
            return true;
        }
    }

    public boolean isWebhookAllowed(String clientIp) {
        return isAllowed("ratelimit:webhook:" + clientIp,
                WEBHOOK_MAX_REQUESTS, WEBHOOK_WINDOW_MS);
    }

    public boolean isOrderAllowed(String clientIp) {
        return isAllowed("ratelimit:order:" + clientIp,
                ORDER_MAX_REQUESTS, ORDER_WINDOW_MS);
    }
}