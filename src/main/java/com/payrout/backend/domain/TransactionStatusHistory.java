package com.payrout.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name="transaction_status_history", indexes=@Index(name="idx_hist_tx", columnList="transaction_id, at_time"))
public class TransactionStatusHistory {

    @Id
    private UUID id;

    @Column(name="transaction_id", nullable=false)
    private UUID transactionId;

    @Column(name="from_status", nullable=false)
    private String fromStatus;

    @Column(name="to_status", nullable=false)
    private String toStatus;

    @Column(name="at_time", nullable=false)
    private Instant atTime;

    @Column(name="reason")
    private String reason;
}