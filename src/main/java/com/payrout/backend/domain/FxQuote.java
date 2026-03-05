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
@Table(name = "fx_quotes")
public class FxQuote {

    @Id
    private UUID id;

    @Column(name = "base_currency")
    private String baseCurrency;

    @Column(name = "quote_currency")
    private String quoteCurrency;

    private BigDecimal rate;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    private String status;
}