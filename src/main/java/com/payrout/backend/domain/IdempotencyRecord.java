package com.payrout.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name="idempotency_records",
        uniqueConstraints = @UniqueConstraint(name="uq_idempo_endpoint_key", columnNames={"endpoint","idem_key"}))
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(name="endpoint", nullable=false)
    private String endpoint;

    @Column(name="idem_key", nullable=false)
    private String idemKey;

    @Column(name="request_hash", nullable=false)
    private String requestHash;

    @Column(name="response_json", nullable=false, columnDefinition="text")
    private String responseJson;

    @Column(name="transaction_id")
    private UUID transactionId;

    @Column(name="created_at", nullable=false)
    private Instant createdAt;
}