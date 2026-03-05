package com.payrout.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name="account_balances",
        uniqueConstraints = @UniqueConstraint(name="uq_balance_account_currency", columnNames={"account_id","currency"}))
public class AccountBalance {

    @Id
    private UUID id;

    @Column(name="account_id", nullable=false)
    private UUID accountId;

    @Column(nullable=false)
    private String currency;

    @Column(nullable=false, precision = 18, scale = 2)
    private BigDecimal available;

    @Column(nullable=false, precision = 18, scale = 2)
    private BigDecimal locked;

}
