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
@Table(name = "transactions",
        indexes = {
                @Index(name="idx_tx_reference", columnList = "reference", unique = true),
                @Index(name="idx_tx_provider_ref", columnList = "provider_reference", unique = true),
                @Index(name="idx_tx_status_created", columnList = "status, created_at")
        })
public class Transaction {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(name="sender_account_id", nullable = false)
    private UUID senderAccountId;

    @Column(name="recipient_account_id", nullable = false)
    private UUID recipientAccountId;

    @Column(name="source_currency", nullable = false)
    private String sourceCurrency;

    @Column(name="destination_currency", nullable = false)
    private String destinationCurrency;

    @Column(name="source_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal sourceAmount;

    @Column(name="destination_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal destinationAmount;

    @Column(nullable = false)
    private String status; // INITIATED, PROCESSING, COMPLETED, FAILED, REVERSED (optional)

    @Column(name="provider_reference")
    private String providerReference;

    @Column(name="fx_quote_id", nullable = false)
    private UUID fxQuoteId;

    @Column(name="created_at", nullable = false)
    private Instant createdAt;

    @Column(name="updated_at", nullable = false)
    private Instant updatedAt;

    // getters/setters...
}
