package com.example.razorpaywebhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookIntegrationTest extends BaseIntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Test 1: Duplicate event_id — second call is a no-op
    // -------------------------------------------------------------------------
    @Test
    void duplicateEventId_secondCallIsNoOp() throws Exception {
        String eventId   = "evt_idem_" + UUID.randomUUID();
        String paymentId = "pay_idem_" + UUID.randomUUID();
        String payload   = capturedPayload(eventId, paymentId, 50000);

        // First call — accepted
        ResponseEntity<String> first = sendWebhook(payload);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(first.getBody()).get("status").asText())
                .isEqualTo("received");

        // Second call — same payload, same signature — also 200
        ResponseEntity<String> second = sendWebhook(payload);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(second.getBody()).get("status").asText())
                .isEqualTo("received");

        // Exactly ONE row in DB — idempotency held
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_events WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(count).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Test 2: Invalid HMAC — rejected, zero DB writes
    // -------------------------------------------------------------------------
    @Test
    void invalidSignature_rejectedWithNoDatabaseWrite() throws Exception {
        String eventId   = "evt_badsig_" + UUID.randomUUID();
        String paymentId = "pay_badsig_" + UUID.randomUUID();
        String payload   = capturedPayload(eventId, paymentId, 50000);

        ResponseEntity<String> response = sendWebhookBadSig(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("error").asText()).isEqualTo("SIGNATURE_INVALID");

        // No row written
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_events WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(count).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Test 3: Valid webhook → ledger has 1 DEBIT + 1 CREDIT, shared transaction_id
    // -------------------------------------------------------------------------
    @Test
    void validWebhook_ledgerHasBalancedDebitCreditPair() throws Exception {
        String eventId   = "evt_ledger_" + UUID.randomUUID();
        String paymentId = "pay_ledger_" + UUID.randomUUID();
        String payload   = capturedPayload(eventId, paymentId, 150000);

        ResponseEntity<String> response = sendWebhook(payload);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Wait for async ledger write
        waitFor(() -> {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ledger_entries WHERE payment_id = ?",
                    Integer.class, paymentId);
            return n != null && n == 2;
        }, 10);

        // Exactly 1 DEBIT
        Integer debit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE payment_id = ? AND entry_type = 'DEBIT'",
                Integer.class, paymentId);
        assertThat(debit).isEqualTo(1);

        // Exactly 1 CREDIT
        Integer credit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE payment_id = ? AND entry_type = 'CREDIT'",
                Integer.class, paymentId);
        assertThat(credit).isEqualTo(1);

        // Both share the same transaction_id
        List<Map<String, Object>> txIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT transaction_id FROM ledger_entries WHERE payment_id = ?",
                paymentId);
        assertThat(txIds).hasSize(1);

        // Balanced amounts
        Long totalDebit = jdbcTemplate.queryForObject(
                "SELECT SUM(amount) FROM ledger_entries WHERE payment_id = ? AND entry_type='DEBIT'",
                Long.class, paymentId);
        Long totalCredit = jdbcTemplate.queryForObject(
                "SELECT SUM(amount) FROM ledger_entries WHERE payment_id = ? AND entry_type='CREDIT'",
                Long.class, paymentId);
        assertThat(totalDebit).isEqualTo(totalCredit);

    }

    // -------------------------------------------------------------------------
    // Test 4: Audit hash chain valid after 3 sequential webhooks
    // -------------------------------------------------------------------------
    @Test
    void auditHashChain_remainsValidAfterThreeEvents() throws Exception {
        java.util.List<String> paymentIds = new java.util.ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            String eventId   = "evt_chain_" + i + "_" + UUID.randomUUID();
            String paymentId = "pay_chain_" + i + "_" + UUID.randomUUID();
            paymentIds.add(paymentId);
            sendWebhook(capturedPayload(eventId, paymentId, 50000L * i));
            Thread.sleep(100);
        }

        // Wait for all 3 audit entries for our paymentIds
        waitFor(() -> {
            int total = 0;
            for (String pid : paymentIds) {
                Integer n = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE entity_id = ? AND current_hash != 'PENDING'",
                        Integer.class, pid);
                if (n != null) total += n;
            }
            return total >= 3;
        }, 15);

        // Each payment has its own audit chain starting with GENESIS
        for (String paymentId : paymentIds) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT sequence_num, previous_hash, current_hash FROM audit_log " +
                            "WHERE entity_id = ? ORDER BY sequence_num ASC",
                    paymentId);

            assertThat(rows).isNotEmpty();

            // First row of each entity chain must start with GENESIS
            assertThat(rows.get(0).get("previous_hash"))
                    .as("first audit row for %s must have previousHash=GENESIS", paymentId)
                    .isEqualTo("GENESIS");

            // currentHash must not be PENDING
            for (Map<String, Object> row : rows) {
                Long seq = (Long) row.get("sequence_num");
                assertThat(row.get("current_hash"))
                        .as("currentHash must not be PENDING at seq %d", seq)
                        .isNotEqualTo("PENDING");
                assertThat(row.get("current_hash"))
                        .as("currentHash must not be null at seq %d", seq)
                        .isNotNull();
            }

            // If entity has multiple audit rows, verify chain continuity within the entity
            for (int i = 1; i < rows.size(); i++) {
                Long seq = (Long) rows.get(i).get("sequence_num");
                String prevCurrent  = (String) rows.get(i - 1).get("current_hash");
                String thisPrevious = (String) rows.get(i).get("previous_hash");
                assertThat(thisPrevious)
                        .as("chain broken at seq %d for entity %s", seq, paymentId)
                        .isEqualTo(prevCurrent);
            }
        }
    }


    // Mirrors AuditService.computeHash exactly — no createdAt, matches the fix we applied

}