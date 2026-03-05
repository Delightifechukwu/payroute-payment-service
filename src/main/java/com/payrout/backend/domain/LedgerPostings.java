package com.payrout.backend.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name="ledger_postings",
        uniqueConstraints = @UniqueConstraint(name="uq_posting_unique_key", columnNames={"unique_key"}))
public class LedgerPostings {

    @Id
    private UUID id;

    @Column(name="transaction_id", nullable=false)
    private UUID transactionId;

    @Column(name="posting_type", nullable=false)
    private String postingType; // DEBIT_LOCK, SETTLE, REVERSE

    @Column(name="unique_key", nullable=false, unique = true)
    private String uniqueKey; // prevents duplicates (idempotency at ledger level)

    @Column(name="created_at", nullable=false)
    private Instant createdAt;
}