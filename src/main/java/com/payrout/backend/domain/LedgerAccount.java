package com.payrout.backend.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name="ledger_accounts", uniqueConstraints = @UniqueConstraint(name="uq_ledger_code_currency", columnNames={"code","currency"}))
public class LedgerAccount {
    @Id
    private UUID id;

    @Column(nullable=false)
    private String code; // e.g. CUST_AVAILABLE, CUST_LOCKED, PROVIDER_CLEARING, FX_REVENUE

    @Column(nullable=false)
    private String currency; // ledger is per currency

    @Column(nullable=false)
    private String type; // ASSET, LIABILITY, REVENUE, EXPENSE
}
