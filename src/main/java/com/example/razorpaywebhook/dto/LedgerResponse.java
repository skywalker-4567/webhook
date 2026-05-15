package com.example.razorpaywebhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerResponse {
    private List<LedgerEntryDTO> entries;
    private Long totalDebit;
    private Long totalCredit;
    private long totalElements;
    private int totalPages;
    private int currentPage;
}