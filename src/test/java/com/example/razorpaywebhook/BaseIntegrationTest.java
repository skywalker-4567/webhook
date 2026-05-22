package com.example.razorpaywebhook;

import com.example.razorpaywebhook.distributed.DistributedLockService;
import com.example.razorpaywebhook.distributed.LeaderElectionService;
import com.example.razorpaywebhook.fraud.MLClient;
import com.example.razorpaywebhook.fraud.MLScoreResponse;
import com.example.razorpaywebhook.ratelimit.RateLimiterService;
import com.example.razorpaywebhook.scheduler.LedgerRetryScheduler;
import com.example.razorpaywebhook.scheduler.ReconciliationScheduler;
import com.example.razorpaywebhook.scheduler.RetryScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("razorpay_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("razorpay.webhook-secret",    () -> WEBHOOK_SECRET);
        registry.add("razorpay.key-id",            () -> "test_key");
        registry.add("razorpay.key-secret",        () -> "test_key_secret");
        registry.add("jwt.secret",
                () -> "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtYXQtbGVhc3QtMjU2LWJpdHMtbG9uZw==");
        registry.add("ml.service.base-url",        () -> "http://localhost:9999");
    }

    // Mock all Redis-dependent beans
    @MockBean DistributedLockService  distributedLockService;
    @MockBean LeaderElectionService   leaderElectionService;
    @MockBean RateLimiterService      rateLimiterService;
    @MockBean MLClient                mlClient;
    @MockBean LedgerRetryScheduler    ledgerRetryScheduler;
    @MockBean ReconciliationScheduler reconciliationScheduler;
    @MockBean RetryScheduler          retryScheduler;

    @LocalServerPort  protected int             port;
    @Autowired        protected TestRestTemplate restTemplate;
    @Autowired        protected JdbcTemplate     jdbcTemplate;

    protected static final String WEBHOOK_SECRET = "test_secret";

    @BeforeEach
    void setUpMocksAndData() {
        when(distributedLockService.tryLock(anyString(), anyString(), anyLong()))
                .thenReturn(true);
        when(distributedLockService.releaseLock(anyString(), anyString()))
                .thenReturn(true);
        when(rateLimiterService.isAllowed(anyString(), anyInt(), anyLong()))
                .thenReturn(true);
        when(mlClient.score(any()))
                .thenReturn(Optional.of(new MLScoreResponse(0.1, false)));

        jdbcTemplate.execute("""
                TRUNCATE webhook_events, payment_records, ledger_entries,
                         audit_log, fraud_checks, orders, reconciliation_log,
                         settlement_reports, refund_records, ledger_retry_queue,
                         ledger_accounts, users, api_keys RESTART IDENTITY CASCADE
                """);

        jdbcTemplate.execute("""
                INSERT INTO ledger_accounts (account_type, account_code, description)
                VALUES
                  ('CUSTOMER', 'ACC_CUSTOMER', 'Customer account'),
                  ('MERCHANT', 'ACC_MERCHANT', 'Merchant account'),
                  ('GATEWAY',  'ACC_GATEWAY',  'Gateway account')
                ON CONFLICT DO NOTHING
                """);

        jdbcTemplate.execute("""
                INSERT INTO users (username, password, role)
                VALUES ('admin',
                        '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBpwTTyNyp2bKu',
                        'ADMIN')
                ON CONFLICT (username) DO NOTHING
                """);
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected ResponseEntity<String> sendWebhook(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Razorpay-Signature", sign(body));
        return restTemplate.exchange(
                baseUrl() + "/webhooks/razorpay",
                HttpMethod.POST,
                new HttpEntity<>(json, headers),
                String.class);
    }

    protected ResponseEntity<String> sendWebhookBadSig(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Razorpay-Signature", "deadbeef000000");
        return restTemplate.exchange(
                baseUrl() + "/webhooks/razorpay",
                HttpMethod.POST,
                new HttpEntity<>(json, headers),
                String.class);
    }

    protected String capturedPayload(String eventId, String paymentId, long amount) {
        return """
                {"id":"%s","event":"payment.captured","created_at":%d,
                 "payload":{"payment":{"entity":{
                   "id":"%s","amount":%d,"currency":"INR","status":"captured",
                   "method":"card","order_id":null,"email":"test@example.com",
                   "contact":"+919999999999","error_description":null}}}}
                """.formatted(eventId, System.currentTimeMillis() / 1000, paymentId, amount);
    }

    protected void waitFor(java.util.function.BooleanSupplier condition, int maxSeconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + (maxSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition.getAsBoolean()) return;
            } catch (Exception ignored) {}
            Thread.sleep(300);
        }
        throw new AssertionError("Condition not met within " + maxSeconds + "s");
    }
}