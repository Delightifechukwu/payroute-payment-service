package com.payrout.backend.dto;

import com.payrout.backend.domain.LedgerEntry;
import com.payrout.backend.domain.Transaction;
import com.payrout.backend.domain.TransactionStatusHistory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionDetailResponse(
        UUID id,
        String reference,
        UUID senderAccountId,
        UUID recipientAccountId,
        String sourceCurrency,
        String destinationCurrency,
        BigDecimal sourceAmount,
        BigDecimal destinationAmount,
        String status,
        String providerReference,
        BigDecimal fxRate,
        Instant createdAt,
        Instant updatedAt,
        List<StatusHistoryItem> statusHistory,
        List<LedgerEntryItem> ledgerEntries
) {
    public record StatusHistoryItem(
            String fromStatus,
            String toStatus,
            String reason,
            Instant atTime
    ) {}

    public record LedgerEntryItem(
            UUID id,
            String ledgerAccountCode,
            String currency,
            BigDecimal amount,
            String direction,
            Instant createdAt
    ) {}

    public static TransactionDetailResponse from(Transaction tx, BigDecimal fxRate,
                                                   List<TransactionStatusHistory> history,
                                                   List<LedgerEntry> entries) {
        return new TransactionDetailResponse(
                tx.getId(),
                tx.getReference(),
                tx.getSenderAccountId(),
                tx.getRecipientAccountId(),
                tx.getSourceCurrency(),
                tx.getDestinationCurrency(),
                tx.getSourceAmount(),
                tx.getDestinationAmount(),
                tx.getStatus(),
                tx.getProviderReference(),
                fxRate,
                tx.getCreatedAt(),
                tx.getUpdatedAt(),
                history.stream()
                        .map(h -> new StatusHistoryItem(h.getFromStatus(), h.getToStatus(), h.getReason(), h.getAtTime()))
                        .toList(),
                entries.stream()
                        .map(e -> new LedgerEntryItem(e.getId(), "LEDGER_" + e.getLedgerAccountId(),
                                e.getCurrency(), e.getAmount(), e.getDirection(), e.getCreatedAt()))
                        .toList()
        );
    }
}
