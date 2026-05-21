package com.example.razorpaywebhook.service;

import com.example.razorpaywebhook.domain.entity.LedgerAccount;
import com.example.razorpaywebhook.domain.entity.LedgerEntry;
import com.example.razorpaywebhook.domain.entity.LedgerRetryQueue;
import com.example.razorpaywebhook.dto.AccountBalanceResponse;
import com.example.razorpaywebhook.dto.LedgerEntryDTO;
import com.example.razorpaywebhook.dto.LedgerResponse;
import com.example.razorpaywebhook.enums.EntryType;
import com.example.razorpaywebhook.enums.LedgerAccountType;
import com.example.razorpaywebhook.enums.RetryStatus;
import com.example.razorpaywebhook.enums.TransactionType;
import com.example.razorpaywebhook.event.PaymentCapturedEvent;
import com.example.razorpaywebhook.event.PaymentFailedEvent;
import com.example.razorpaywebhook.event.PaymentRefundedEvent;
import com.example.razorpaywebhook.exception.DataInconsistencyException;
import com.example.razorpaywebhook.exception.LedgerCurrencyMismatchException;
import com.example.razorpaywebhook.exception.LedgerInvariantException;
import com.example.razorpaywebhook.repository.LedgerAccountRepository;
import com.example.razorpaywebhook.repository.LedgerEntryRepository;
import com.example.razorpaywebhook.repository.LedgerRetryQueueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository      ledgerEntryRepository;
    private final LedgerAccountRepository    ledgerAccountRepository;
    private final LedgerRetryQueueRepository ledgerRetryQueueRepository;
    private final ObjectMapper               objectMapper;
    private final MeterRegistry              meterRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        if (event.correlationId() != null) {
            MDC.put("correlationId", event.correlationId().toString());
        }
        try {
            String transactionRef = event.eventId().toString();

            if (ledgerEntryRepository.existsByTransactionRefAndEntryType(
                    transactionRef, EntryType.DEBIT)) {
                log.info("Ledger already posted for eventId={} - skipping", transactionRef);
                return;
            }

            validateAmount(event.amount(), event.currency());

            LedgerAccount customerAccount = requireAccount(LedgerAccountType.CUSTOMER);
            LedgerAccount gatewayAccount  = requireAccount(LedgerAccountType.GATEWAY);
            UUID transactionId = UUID.randomUUID();

            LedgerEntry debit = new LedgerEntry(
                    transactionRef, TransactionType.PAYMENT, EntryType.DEBIT,
                    customerAccount.getId(), event.amount(), event.currency(),
                    "Payment captured - debit customer",
                    event.paymentId(), event.internalOrderId(), transactionId, event.correlationId());

            LedgerEntry credit = new LedgerEntry(
                    transactionRef, TransactionType.PAYMENT, EntryType.CREDIT,
                    gatewayAccount.getId(), event.amount(), event.currency(),
                    "Payment captured - credit gateway",
                    event.paymentId(), event.internalOrderId(), transactionId, event.correlationId());

            ledgerEntryRepository.save(debit);
            ledgerEntryRepository.save(credit);
            log.info("Ledger posted PAYMENT: transactionRef={} amount={} currency={}",
                    transactionRef, event.amount(), event.currency());

        } catch (Exception ex) {
            log.error("Ledger posting failed for PaymentCapturedEvent eventId={}",
                    event.eventId(), ex);
            enqueueRetry("PAYMENT_CAPTURED", event, ex.getMessage());
        } finally {
            MDC.remove("correlationId");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("No ledger entry for failed payment: paymentId={}", event.paymentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        if (event.correlationId() != null) {
            MDC.put("correlationId", event.correlationId().toString());
        }
        try {
            String transactionRef = event.eventId().toString();

            if (ledgerEntryRepository.existsByTransactionRefAndEntryType(
                    transactionRef, EntryType.DEBIT)) {
                log.info("Ledger already posted for refund eventId={} - skipping", transactionRef);
                return;
            }

            validateAmount(event.amount(), event.currency());

            LedgerAccount gatewayAccount  = requireAccount(LedgerAccountType.GATEWAY);
            LedgerAccount customerAccount = requireAccount(LedgerAccountType.CUSTOMER);
            UUID transactionId = UUID.randomUUID();

            LedgerEntry debit = new LedgerEntry(
                    transactionRef, TransactionType.REFUND, EntryType.DEBIT,
                    gatewayAccount.getId(), event.amount(), event.currency(),
                    "Refund - debit gateway",
                    event.paymentId(), event.internalOrderId(), transactionId, event.correlationId());

            LedgerEntry credit = new LedgerEntry(
                    transactionRef, TransactionType.REFUND, EntryType.CREDIT,
                    customerAccount.getId(), event.amount(), event.currency(),
                    "Refund - credit customer",
                    event.paymentId(), event.internalOrderId(), transactionId, event.correlationId());

            ledgerEntryRepository.save(debit);
            ledgerEntryRepository.save(credit);
            log.info("Ledger posted REFUND: transactionRef={} amount={} currency={}",
                    transactionRef, event.amount(), event.currency());

        } catch (Exception ex) {
            log.error("Ledger posting failed for PaymentRefundedEvent eventId={}",
                    event.eventId(), ex);
            enqueueRetry("PAYMENT_REFUNDED", event, ex.getMessage());
        } finally {
            MDC.remove("correlationId");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postSettlement(String transactionRef, long amount, String currency,
                               String paymentId, UUID orderId, UUID correlationId) {
        if (ledgerEntryRepository.existsByTransactionRefAndEntryType(
                transactionRef, EntryType.DEBIT)) {
            log.info("Settlement already posted for ref={} - skipping", transactionRef);
            return;
        }

        validateAmount(amount, currency);

        LedgerAccount gatewayAccount  = requireAccount(LedgerAccountType.GATEWAY);
        LedgerAccount merchantAccount = requireAccount(LedgerAccountType.MERCHANT);
        UUID transactionId = UUID.randomUUID();

        LedgerEntry debit = new LedgerEntry(
                transactionRef, TransactionType.SETTLEMENT, EntryType.DEBIT,
                gatewayAccount.getId(), amount, currency,
                "Settlement - debit gateway", paymentId, orderId, transactionId, correlationId);

        LedgerEntry credit = new LedgerEntry(
                transactionRef, TransactionType.SETTLEMENT, EntryType.CREDIT,
                merchantAccount.getId(), amount, currency,
                "Settlement - credit merchant", paymentId, orderId, transactionId, correlationId);

        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);
        log.info("Ledger posted SETTLEMENT: transactionRef={}", transactionRef);
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse getAccountBalance(LedgerAccountType type, Instant asOf) {
        LedgerAccount account = requireAccount(type);
        List<LedgerEntry> entries = ledgerEntryRepository
                .findAllByAccountIdAndCreatedAtBefore(account.getId(), asOf);
        long credits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .mapToLong(LedgerEntry::getAmount).sum();
        long debits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .mapToLong(LedgerEntry::getAmount).sum();
        return AccountBalanceResponse.builder()
                .accountType(type.name())
                .balance(credits - debits)
                .currency("INR")
                .asOf(asOf)
                .build();
    }

    @Transactional(readOnly = true)
    public LedgerResponse getLedgerByPaymentId(String paymentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by("createdAt").ascending());
        return buildLedgerResponse(ledgerEntryRepository.findByPaymentId(paymentId, pageable));
    }

    @Transactional(readOnly = true)
    public LedgerResponse getLedgerByOrderId(UUID orderId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by("createdAt").ascending());
        return buildLedgerResponse(ledgerEntryRepository.findByOrderId(orderId, pageable));
    }

    @Transactional(readOnly = true)
    public LedgerResponse getLedgerByTransactionId(UUID transactionId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by("createdAt").ascending());
        return buildLedgerResponse(
                ledgerEntryRepository.findByTransactionId(transactionId, pageable));
    }

    @Transactional(readOnly = true)
    public LedgerResponse getLedgerByAccountType(LedgerAccountType type, Instant from,
                                                 Instant to, int page, int size) {
        LedgerAccount account = requireAccount(type);
        Pageable pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by("createdAt").ascending());
        return buildLedgerResponse(
                ledgerEntryRepository.findByAccountIdAndCreatedAtBetween(
                        account.getId(), from, to, pageable));
    }

    private LedgerResponse buildLedgerResponse(Page<LedgerEntry> page) {
        Map<UUID, String> accountTypeMap = ledgerAccountRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        LedgerAccount::getId,
                        a -> a.getAccountType().name()));

        List<LedgerEntry> entries = page.getContent();
        long totalDebit  = entries.stream().filter(e -> e.getEntryType() == EntryType.DEBIT)
                .mapToLong(LedgerEntry::getAmount).sum();
        long totalCredit = entries.stream().filter(e -> e.getEntryType() == EntryType.CREDIT)
                .mapToLong(LedgerEntry::getAmount).sum();

        return LedgerResponse.builder()
                .entries(entries.stream().map(e -> toDto(e, accountTypeMap)).toList())
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .build();
    }

    private LedgerEntryDTO toDto(LedgerEntry e, Map<UUID, String> accountTypeMap) {
        return LedgerEntryDTO.builder()
                .id(e.getId())
                .transactionRef(e.getTransactionRef())
                .transactionType(e.getTransactionType().name())
                .entryType(e.getEntryType().name())
                .accountType(accountTypeMap.getOrDefault(e.getAccountId(), "UNKNOWN"))
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .description(e.getDescription())
                .transactionId(e.getTransactionId())
                .createdAt(e.getCreatedAt())
                .correlationId(e.getCorrelationId())
                .build();
    }

    private LedgerAccount requireAccount(LedgerAccountType type) {
        return ledgerAccountRepository.findByAccountType(type)
                .orElseThrow(() -> new DataInconsistencyException(
                        "Ledger account not found: " + type));
    }

    private void validateAmount(long amount, String currency) {
        if (amount <= 0) {
            throw new LedgerInvariantException("Ledger amount must be positive, got: " + amount);
        }
        if (currency == null || currency.isBlank()) {
            throw new LedgerCurrencyMismatchException("Currency must not be blank");
        }
    }

    private void enqueueRetry(String eventType, Object event, String failureReason) {
        meterRegistry.counter("ledger.write.failures", "event_type", eventType).increment();
        try {
            String payload = objectMapper.writeValueAsString(event);
            LedgerRetryQueue retryItem = LedgerRetryQueue.builder()
                    .eventType(eventType)
                    .eventPayload(payload)
                    .retryCount(0)
                    .status(RetryStatus.PENDING)
                    .lastFailureReason(failureReason)
                    .nextRetryAt(Instant.now().plusSeconds(60))
                    .build();
            ledgerRetryQueueRepository.save(retryItem);
            log.info("Enqueued ledger retry for eventType={}", eventType);
        } catch (Exception ex) {
            log.error("Failed to enqueue ledger retry for eventType={}", eventType, ex);
        }
    }
}