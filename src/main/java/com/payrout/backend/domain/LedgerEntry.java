package com.payrout.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name="ledger_entries", indexes=@Index(name="idx_ledger_tx", columnList="transaction_id"))
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name="posting_id", nullable=false)
    private UUID postingId;

    @Column(name="transaction_id", nullable=false)
    private UUID transactionId;

    @Column(name="ledger_account_id", nullable=false)
    private UUID ledgerAccountId;

    @Column(nullable=false)
    private String currency;

    @Column(nullable=false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable=false)
    private String direction; // DEBIT or CREDIT

    @Column(name="created_at", nullable=false)
    private Instant createdAt;
}