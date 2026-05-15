package com.example.razorpaywebhook.service;

import com.example.razorpaywebhook.client.RazorpaySettlementClient;
import com.example.razorpaywebhook.client.RazorpaySettlementItem;
import com.example.razorpaywebhook.domain.entity.LedgerAccount;
import com.example.razorpaywebhook.domain.entity.LedgerEntry;
import com.example.razorpaywebhook.domain.entity.SettlementReport;
import com.example.razorpaywebhook.dto.SettlementSummaryResponse;
import com.example.razorpaywebhook.enums.EntryType;
import com.example.razorpaywebhook.enums.LedgerAccountType;
import com.example.razorpaywebhook.enums.SettlementStatus;
import com.example.razorpaywebhook.enums.TransactionType;
import com.example.razorpaywebhook.exception.InvalidDateRangeException;
import com.example.razorpaywebhook.repository.LedgerAccountRepository;
import com.example.razorpaywebhook.repository.LedgerEntryRepository;
import com.example.razorpaywebhook.repository.SettlementReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SettlementService {

    private static final long MAX_RANGE_DAYS = 31;

    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final SettlementReportRepository settlementReportRepository;
    private final RazorpaySettlementClient razorpaySettlementClient;

    // -------------------------------------------------------------------------
    // Generate report + CSV
    // -------------------------------------------------------------------------

    public SettlementReport generateReport(Instant from, Instant to) {
        validateDateRange(from, to);

        List<LedgerEntry> entries = fetchEntries(from, to);
        Map<UUID, String> accountCodeMap = buildAccountCodeMap();

        long totalPayments = sumByType(entries, TransactionType.PAYMENT, EntryType.CREDIT);
        long totalRefunds  = sumByType(entries, TransactionType.REFUND, EntryType.DEBIT);
        long netAmount     = totalPayments - totalRefunds;
        int  entryCount    = entries.size();

        SettlementReport report = SettlementReport.builder()
                .periodStart(from)
                .periodEnd(to)
                .totalPayments(totalPayments)
                .totalRefunds(totalRefunds)
                .netAmount(netAmount)
                .entryCount(entryCount)
                .status(SettlementStatus.GENERATED)
                .build();

        SettlementReport saved = settlementReportRepository.save(report);
        log.info("Settlement report generated: id={} entries={} net={}",
                saved.getId(), entryCount, netAmount);
        return saved;
    }

    public byte[] generateCsvBytes(Instant from, Instant to) {
        validateDateRange(from, to);

        List<LedgerEntry> entries = fetchEntries(from, to);
        Map<UUID, String> accountCodeMap = buildAccountCodeMap();

        String csv = buildCsv(entries, accountCodeMap);
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public SettlementSummaryResponse getSummary(Instant from, Instant to) {
        validateDateRange(from, to);

        List<LedgerEntry> entries = fetchEntries(from, to);

        long totalPayments = sumByType(entries, TransactionType.PAYMENT, EntryType.CREDIT);
        long totalRefunds  = sumByType(entries, TransactionType.REFUND, EntryType.DEBIT);
        long netAmount     = totalPayments - totalRefunds;
        int  entryCount    = entries.size();

        return SettlementSummaryResponse.builder()
                .periodStart(from)
                .periodEnd(to)
                .totalPayments(totalPayments)
                .totalRefunds(totalRefunds)
                .netAmount(netAmount)
                .currency("INR")
                .entryCount(entryCount)
                .build();
    }

    // -------------------------------------------------------------------------
    // Reconciliation
    // -------------------------------------------------------------------------

    public void reconcileWithGateway(UUID reportId) {
        SettlementReport report = settlementReportRepository.findById(reportId)
                .orElseThrow(() -> new InvalidDateRangeException(
                        "Settlement report not found: " + reportId));

        List<RazorpaySettlementItem> gatewayItems = razorpaySettlementClient
                .getSettlements(report.getPeriodStart(), report.getPeriodEnd());

        long gatewayTotal = gatewayItems.stream()
                .filter(item -> item.getAmount() != null)
                .mapToLong(RazorpaySettlementItem::getAmount)
                .sum();

        long internalNet = report.getNetAmount();

        if (gatewayTotal == internalNet) {
            report.setStatus(SettlementStatus.RECONCILED);
            report.setReconciledAt(Instant.now());
            settlementReportRepository.save(report);
            log.info("Settlement reconciled: reportId={} amount={}", reportId, internalNet);
        } else {
            report.setStatus(SettlementStatus.DISPUTED);
            settlementReportRepository.save(report);
            log.warn("Settlement DISPUTED: reportId={} internal={} gateway={}",
                    reportId, internalNet, gatewayTotal);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void validateDateRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new InvalidDateRangeException("from and to dates are required");
        }
        if (!from.isBefore(to)) {
            throw new InvalidDateRangeException("from must be before to");
        }
        long days = Duration.between(from, to).toDays();
        if (days > MAX_RANGE_DAYS) {
            throw new InvalidDateRangeException(
                    "Date range must not exceed " + MAX_RANGE_DAYS + " days, got: " + days);
        }
    }

    private List<LedgerEntry> fetchEntries(Instant from, Instant to) {
        return ledgerEntryRepository.findByCreatedAtBetweenAndTransactionTypeIn(
                from, to,
                List.of(TransactionType.PAYMENT, TransactionType.REFUND));
    }

    private Map<UUID, String> buildAccountCodeMap() {
        return ledgerAccountRepository.findAll().stream()
                .collect(Collectors.toMap(
                        LedgerAccount::getId,
                        LedgerAccount::getAccountCode));
    }

    private long sumByType(List<LedgerEntry> entries,
                           TransactionType txType, EntryType entryType) {
        return entries.stream()
                .filter(e -> e.getTransactionType() == txType
                        && e.getEntryType() == entryType)
                .mapToLong(LedgerEntry::getAmount)
                .sum();
    }

    private String buildCsv(List<LedgerEntry> entries, Map<UUID, String> accountCodeMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("transaction_ref,transaction_type,payment_id,order_id,")
                .append("debit_account,credit_account,amount,currency,created_at\n");

        // Group entries by transactionId to pair DEBIT + CREDIT
        Map<UUID, List<LedgerEntry>> byTransaction = entries.stream()
                .collect(Collectors.groupingBy(LedgerEntry::getTransactionId));

        for (Map.Entry<UUID, List<LedgerEntry>> group : byTransaction.entrySet()) {
            List<LedgerEntry> pair = group.getValue();

            LedgerEntry debit  = pair.stream()
                    .filter(e -> e.getEntryType() == EntryType.DEBIT)
                    .findFirst().orElse(null);
            LedgerEntry credit = pair.stream()
                    .filter(e -> e.getEntryType() == EntryType.CREDIT)
                    .findFirst().orElse(null);

            if (debit == null || credit == null) continue;

            String transactionRef  = debit.getTransactionRef();
            String transactionType = debit.getTransactionType().name();
            String paymentId       = nullSafe(debit.getPaymentId());
            String orderId         = debit.getOrderId() != null
                    ? debit.getOrderId().toString() : "";
            String debitAccount    = accountCodeMap.getOrDefault(debit.getAccountId(), "");
            String creditAccount   = accountCodeMap.getOrDefault(credit.getAccountId(), "");
            long   amount          = debit.getAmount();
            String currency        = debit.getCurrency();
            String createdAt       = debit.getCreatedAt() != null
                    ? debit.getCreatedAt().toString() : "";

            sb.append(csvEscape(transactionRef)).append(',')
                    .append(csvEscape(transactionType)).append(',')
                    .append(csvEscape(paymentId)).append(',')
                    .append(csvEscape(orderId)).append(',')
                    .append(csvEscape(debitAccount)).append(',')
                    .append(csvEscape(creditAccount)).append(',')
                    .append(amount).append(',')
                    .append(csvEscape(currency)).append(',')
                    .append(csvEscape(createdAt)).append('\n');
        }

        return sb.toString();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}